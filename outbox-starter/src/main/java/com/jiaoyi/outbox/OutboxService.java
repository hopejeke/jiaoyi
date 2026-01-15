package com.jiaoyi.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 服务（简化版）
 * 只负责：落库（同事务）→ 定时扫表 → 发送（MQ/HTTP）→ 重试
 */
@Slf4j
public class OutboxService {
    
    private final com.jiaoyi.outbox.repository.OutboxRepository outboxRepository;
    private OutboxServiceCore outboxServiceCore;
    
    @Autowired(required = false)
    private ApplicationContext applicationContext; // 用于获取 handlers
    
    @Autowired(required = false)
    private TaskExecutor taskExecutor; // 用于异步执行任务处理
    
    @Value("${outbox.shard-count:10}")
    private int shardCount;
    
    @Value("${outbox.table:outbox}")
    private String table;
    
    /**
     * 实例ID（用于 claim）
     */
    private final String instanceId = UUID.randomUUID().toString();
    
    /**
     * 锁超时时间（30秒）
     */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 20;
    
    public OutboxService(com.jiaoyi.outbox.repository.OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.outboxServiceCore = null;
    }
    
    @PostConstruct
    private void initOutboxServiceCore() {
        validateTableName(table);
        
        if (outboxServiceCore == null) {
            this.outboxServiceCore = new OutboxServiceCore(outboxRepository, shardCount, table);
        }
    }
    
    private void validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("outbox.table 配置不能为空");
        }
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException(
                    String.format("outbox.table 配置值 '%s' 不合法，只允许字母、数字和下划线（[a-zA-Z0-9_]）", tableName));
        }
        log.info("【OutboxService】表名校验通过: {}", tableName);
    }
    
    public String getTable() {
        return table;
    }
    
    /**
     * 写入 outbox（通用方法，需要传入 shardingKey 用于分库路由）
     * 
     * 业务方只需要调用此方法，内部会自动：
     * 1. 写入 outbox 表（与业务事务同库，状态为 NEW）
     * 2. 事务提交后自动处理：
     *    - 先 claim 为 PROCESSING（处理中）
     *    - 执行 handler 逻辑
     *    - 成功：标记为 SENT
     *    - 失败：标记为 FAILED，由兜底任务执行重试
     * 
     * 业务方完全无感，不需要手动触发任何事件。
     * 
     * 注意：分库路由使用 sharding_key（通用字段），业务方需要在 ShardingSphere 配置中指定此字段作为分片键
     * 
     * @param type 任务类型
     * @param bizKey 业务键
     * @param payload 任务负载
     * @param topic RocketMQ Topic（可选）
     * @param tag RocketMQ Tag（可选）
     * @param messageKey 消息Key（可选）
     * @param shardingKey 分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，必须传入）
     * @param shardId 分片ID（用于扫描优化，由业务方计算传入，可选，如果为 null 则从 shardingKey 计算）
     * @return Outbox记录
     */
    @Transactional(transactionManager = "shardingTransactionManager")
    public Outbox enqueue(String type, String bizKey, String payload, 
                         String topic, String tag, String messageKey, String shardingKey, Integer shardId) {
        log.info("【OutboxService】开始写入 outbox，type: {}, bizKey: {}, shardingKey: {}", type, bizKey, shardingKey);
        try {
            // 使用已初始化的 outboxServiceCore，避免每次创建新实例
            if (outboxServiceCore == null) {
                initOutboxServiceCore();
            }
            
            // 如果 shardId 为 null，从 shardingKey 计算（用于扫描优化）
            if (shardId == null && shardingKey != null) {
                shardId = calculateShardId(shardingKey);
            }
            
            Outbox outbox = outboxServiceCore.enqueue(type, bizKey, payload, topic, tag, messageKey, shardingKey, shardId);
            log.info("【OutboxService】✓ 写入 outbox 成功，outboxId: {}, type: {}, bizKey: {}, shardingKey: {}", 
                    outbox != null ? outbox.getId() : "null", type, bizKey, shardingKey);
            
            // 事务提交后自动处理任务（异步执行，不阻塞主事务）
            // 流程：claim → handler.handle() → markSent/markFailed
            if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
                Long outboxId = outbox.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 异步执行，避免阻塞主事务
                        if (taskExecutor != null) {
                            taskExecutor.execute(() -> processTask(outboxId));
                        } else {
                            // 如果没有 TaskExecutor，直接同步执行（afterCommit 本身不会阻塞主事务）
                            processTask(outboxId);
                        }
                    }
                });
            } else if (outbox != null) {
                // 如果没有事务上下文，直接处理（非事务场景）
                if (taskExecutor != null) {
                    taskExecutor.execute(() -> processTask(outbox.getId()));
                } else {
                    processTask(outbox.getId());
                }
            }
            
            return outbox;
        } catch (Exception e) {
            log.error("【OutboxService】✗ 写入 outbox 失败，type: {}, bizKey: {}, shardingKey: {}, 错误: {}", 
                    type, bizKey, shardingKey, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 计算 shardId（用于扫描优化，从 shardingKey 计算）
     * 默认算法：hash(shardingKey) % 9 / 3 -> 0/1/2
     * 业务方可以重写此方法使用自己的算法
     */
    private int calculateShardId(String shardingKey) {
        int hashCode = Math.abs(shardingKey.hashCode());
        int shardValue = hashCode % 9;
        return shardValue / 3; // 0, 1, 2
    }
    
    /**
     * 处理单个任务（事务提交后执行）
     * 流程：claim → handler.handle() → markSent/markFailed
     * 
     * 1. claim 为 PROCESSING
     * 2. 执行 handler
     * 3. 成功：markSent
     * 4. 失败：markFailed（由兜底任务执行重试）
     */
    private void processTask(Long outboxId) {
        try {
            // 1. 先查询任务详情，获取 merchantId 和 shardId
            Outbox outbox = outboxRepository.selectById(table, outboxId);
            if (outbox == null) {
                log.warn("【OutboxService】任务不存在，outboxId: {}", outboxId);
                return;
            }
            
            // 2. 从 outbox 记录中获取 shardId（用于 WHERE 条件）
            Integer shardId = outbox.getShardId();
            if (shardId == null) {
                log.warn("【OutboxService】任务缺少 shardId，无法处理，outboxId: {}", outboxId);
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lockUntil = now.plus(LOCK_TIMEOUT);
            
            // 3. claim 为 PROCESSING（使用 claimByIds，传入单个 id）
            List<Long> ids = Collections.singletonList(outboxId);
            int claimed = outboxRepository.claimByIds(table, shardId, ids, instanceId, lockUntil, now);
            
            if (claimed == 0) {
                // claim 失败（可能已被其他实例处理，或状态不对）
                log.debug("【OutboxService】任务已被其他实例处理或状态不对，outboxId: {}, shardId: {}", outboxId, shardId);
                return;
            }
            
            // 4. 重新查询任务详情（确保获取最新状态）
            List<Outbox> tasks = outboxRepository.selectByIds(table, shardId, ids);
            if (tasks.isEmpty()) {
                log.warn("【OutboxService】claim 成功但查询不到任务，outboxId: {}, shardId: {}", outboxId, shardId);
                return;
            }
            
            final Outbox finalOutbox = tasks.getFirst();
            
            // 3. 查找对应的 handler
            List<OutboxHandler> handlers = getHandlers();
            if (handlers == null || handlers.isEmpty()) {
                log.warn("【OutboxService】未找到 handlers，释放锁，outboxId: {}, type: {}", outboxId, outbox.getType());
                outboxRepository.releaseLock(table, outboxId, instanceId);
                return;
            }
            
            OutboxHandler handler = handlers.stream()
                    .filter(h -> h.supports(finalOutbox.getType()))
                    .findFirst()
                    .orElse(null);
            
            if (handler == null) {
                log.warn("【OutboxService】未找到对应的 handler，释放锁，outboxId: {}, type: {}", outboxId, finalOutbox.getType());
                outboxRepository.releaseLock(table, outboxId, instanceId);
                return;
            }
            
            // 4. 执行 handler
            try {
                handler.handle(finalOutbox);
                
                // 5. 成功：标记为 SENT
                int updated = outboxRepository.markSent(table, outboxId, instanceId);
                if (updated > 0) {
                    log.info("【OutboxService】✓ 任务处理成功，outboxId: {}, type: {}, bizKey: {}", 
                            outboxId, finalOutbox.getType(), finalOutbox.getBizKey());
                } else {
                    log.warn("【OutboxService】标记已发送失败，可能锁已失效，outboxId: {}", outboxId);
                }
                
            } catch (Exception e) {
                log.error("【OutboxService】✗ 处理任务失败，outboxId: {}, type: {}, bizKey: {}, 错误: {}", 
                        outboxId, finalOutbox.getType(), finalOutbox.getBizKey(), e.getMessage(), e);
                
                // 6. 失败：标记为 FAILED，由兜底任务执行重试
                int retryCount = (finalOutbox.getRetryCount() != null ? finalOutbox.getRetryCount() : 0) + 1;
                long backoffSeconds = Math.min((long) Math.pow(2, retryCount), 300); // 上限300秒（5分钟）
                LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
                String errorMessage = truncate(e.getMessage());
                
                if (retryCount >= MAX_RETRY_COUNT) {
                    // 超过最大重试次数，标记为死信
                    int updated = outboxRepository.markDead(table, outboxId, instanceId, errorMessage);
                    if (updated > 0) {
                        log.error("【OutboxService】任务标记为死信，outboxId: {}, retryCount: {}, 错误: {}", 
                                outboxId, retryCount, errorMessage);
                    }
                } else {
                    // 标记为失败，等待兜底任务重试
                    int updated = outboxRepository.markFailed(
                            table, outboxId, instanceId, retryCount, nextRetryTime, errorMessage);
                    if (updated > 0) {
                        log.warn("【OutboxService】任务标记为失败，等待兜底任务重试，outboxId: {}, retryCount: {}, nextRetryTime: {}", 
                                outboxId, retryCount, nextRetryTime);
                    }
                }
            }
        } catch (Exception e) {
            log.error("【OutboxService】处理任务异常，outboxId: {}", outboxId, e);
            // 不抛出异常，失败的任务会由兜底扫表处理
        }
    }
    
    /**
     * 获取 handlers 列表
     */
    private List<OutboxHandler> getHandlers() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBeansOfType(OutboxHandler.class).values().stream().toList();
        } catch (Exception e) {
            log.warn("【OutboxService】获取 handlers 失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 截断错误信息（避免过长）
     */
    private String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
    
    /**
     * 写入 outbox（兼容旧方法，已废弃）
     * 
     * @deprecated 建议使用 enqueue(..., shardingKey, shardId) 方法，明确传入 shardingKey
     */
    @Deprecated
    @Transactional(transactionManager = "shardingTransactionManager")
    public Outbox enqueue(String type, String bizKey, String payload, 
                         String topic, String tag, String messageKey) {
        log.warn("【OutboxService】使用了已废弃的方法 enqueue(..., 无 shardingKey)，建议使用 enqueue(..., shardingKey, shardId) 方法");
        throw new UnsupportedOperationException("请使用 enqueue(..., shardingKey, shardId) 方法，明确传入 shardingKey");
    }
    
    /**
     * 写入 outbox（兼容旧方法，已废弃）
     * 
     * @deprecated 建议使用 enqueue(..., shardingKey, shardId) 方法，明确传入 shardingKey
     */
    @Deprecated
    @Transactional(transactionManager = "shardingTransactionManager")
    public Outbox enqueue(String type, String bizKey, String payload, 
                         String topic, String tag, String messageKey, Integer shardId) {
        log.warn("【OutboxService】使用了已废弃的方法 enqueue(..., shardId)，建议使用 enqueue(..., shardingKey, shardId) 方法");
        throw new UnsupportedOperationException("请使用 enqueue(..., shardingKey, shardId) 方法，明确传入 shardingKey");
    }
    
    @Transactional(transactionManager = "shardingTransactionManager")
    public Outbox saveMessage(String topic, String tag, String messageKey, Object message, ObjectMapper objectMapper) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            String bizKey = messageKey != null ? messageKey : (topic + ":" + tag);
            String type = topic.toUpperCase().replace("-", "_") + "_MQ";
            
            log.warn("【OutboxService】saveMessage 方法无法自动获取 shardingKey，建议直接使用 enqueue(..., shardingKey, shardId) 方法");
            throw new UnsupportedOperationException("请使用 enqueue(..., shardingKey, shardId) 方法，明确传入 shardingKey");
        } catch (Exception e) {
            log.error("写入outbox表失败，Topic: {}, Tag: {}", topic, tag, e);
            throw new RuntimeException("写入outbox表失败: " + e.getMessage(), e);
        }
    }
}
