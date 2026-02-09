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
            // 优化：直接传递完整的 outbox 对象，避免广播查询
            // 流程：claim → handler.handle() → markSent/markFailed
            if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
                final Outbox finalOutbox = outbox;  // 用于 lambda 表达式捕获
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 异步执行，避免阻塞主事务
                        if (taskExecutor != null) {
                            try {
                                // ✅ 优化：直接传递 outbox 对象，不需要查询数据库
                                taskExecutor.execute(() -> processTaskWithOutbox(finalOutbox));
                            } catch (java.util.concurrent.RejectedExecutionException e) {
                                // 队列满，记录日志，由定时扫表处理
                                log.warn("【OutboxService】异步任务队列满，outboxId: {} 将由定时扫表任务处理", finalOutbox.getId());
                                // TODO: 增加监控指标
                                // metricsService.incrementCounter("outbox.queue.rejected");
                            } catch (Exception e) {
                                // 其他异常也记录，不影响主流程
                                log.error("【OutboxService】提交异步任务失败，outboxId: {} 将由定时扫表任务处理", finalOutbox.getId(), e);
                            }
                        } else {
                            // 如果没有 TaskExecutor，直接同步执行（afterCommit 本身不会阻塞主事务）
                            try {
                                processTaskWithOutbox(finalOutbox);
                            } catch (Exception e) {
                                log.error("【OutboxService】同步处理任务失败，outboxId: {}，将由定时扫表任务重试", finalOutbox.getId(), e);
                            }
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            // 事务回滚，记录日志（Outbox也会回滚）
                            log.info("【OutboxService】业务事务回滚，Outbox记录已回滚，outboxId: {}", finalOutbox.getId());
                        }
                    }
                });
            } else if (outbox != null) {
                // 如果没有事务上下文，直接处理（非事务场景）
                if (taskExecutor != null) {
                    taskExecutor.execute(() -> processTaskWithOutbox(outbox));
                } else {
                    processTaskWithOutbox(outbox);
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
     * 处理任务（使用完整的 Outbox 对象，不需要查询数据库）
     * 用于事务提交后立即执行，性能最优
     *
     * 流程：claim → handler.handle() → markSent/markFailed
     *
     * @param outbox 完整的 Outbox 对象（包含 id、shardId、type、payload 等所有字段）
     */
    private void processTaskWithOutbox(Outbox outbox) {
        if (outbox == null) {
            log.warn("【OutboxService】outbox 对象为 null，无法处理");
            return;
        }

        Long outboxId = outbox.getId();
        Integer shardId = outbox.getShardId();

        if (shardId == null) {
            log.warn("【OutboxService】任务缺少 shardId，无法处理，outboxId: {}", outboxId);
            return;
        }

        log.debug("【OutboxService】开始处理任务（使用 outbox 对象），outboxId: {}, type: {}, shardId: {}",
                outboxId, outbox.getType(), shardId);

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lockUntil = now.plus(LOCK_TIMEOUT);

            // 1. Claim 任务（带 shardId，精准路由，不会触发广播查询）
            List<Long> ids = Collections.singletonList(outboxId);
            int claimed = outboxRepository.claimByIds(table, shardId, ids, instanceId, lockUntil, now);

            if (claimed == 0) {
                // claim 失败（可能已被其他实例处理，或状态不对）
                log.debug("【OutboxService】任务已被其他实例处理或状态不对，outboxId: {}, shardId: {}", outboxId, shardId);
                return;
            }

            // 2. 查找对应的 handler
            List<OutboxHandler> handlers = getHandlers();
            if (handlers == null || handlers.isEmpty()) {
                log.warn("【OutboxService】未找到 handlers，释放锁，outboxId: {}, type: {}", outboxId, outbox.getType());
                outboxRepository.releaseLock(table, outboxId, instanceId);
                return;
            }

            OutboxHandler handler = handlers.stream()
                    .filter(h -> h.supports(outbox.getType()))
                    .findFirst()
                    .orElse(null);

            if (handler == null) {
                log.warn("【OutboxService】未找到对应的 handler，释放锁，outboxId: {}, type: {}", outboxId, outbox.getType());
                outboxRepository.releaseLock(table, outboxId, instanceId);
                return;
            }

            // 3. 执行 handler（直接使用传入的 outbox 对象，无需查询数据库）
            try {
                handler.handle(outbox);

                // 4. 成功：标记为 SENT
                int updated = outboxRepository.markSent(table, outboxId, instanceId);
                if (updated > 0) {
                    log.info("【OutboxService】✓ 任务处理成功，outboxId: {}, type: {}, bizKey: {}",
                            outboxId, outbox.getType(), outbox.getBizKey());
                } else {
                    log.warn("【OutboxService】标记已发送失败，可能锁已失效，outboxId: {}", outboxId);
                }

            } catch (Exception e) {
                log.error("【OutboxService】✗ 处理任务失败，outboxId: {}, type: {}, bizKey: {}, 错误: {}",
                        outboxId, outbox.getType(), outbox.getBizKey(), e.getMessage(), e);

                // 5. 失败：标记为 FAILED，由兜底任务执行重试
                int retryCount = (outbox.getRetryCount() != null ? outbox.getRetryCount() : 0) + 1;
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
     * 处理Outbox任务（带 shardId 参数，用于定时重试扫表）
     *
     * 注意：此方法供定时扫表任务调用，因为扫表时已经知道 shardId
     * 事务提交后立即执行请使用 processTaskWithOutbox(Outbox outbox)
     *
     * @param outboxId Outbox任务ID
     * @param shardId 分片ID（必须传入，用于精准路由）
     */
    public void processTask(Long outboxId, Integer shardId) {
        if (shardId == null) {
            log.error("【OutboxService】shardId 为 null，无法处理任务，outboxId: {}", outboxId);
            return;
        }

        try {
            // 1. 查询任务详情（带 shardId，精准路由）
            List<Long> ids = Collections.singletonList(outboxId);
            List<Outbox> tasks = outboxRepository.selectByIds(table, shardId, ids);

            if (tasks.isEmpty()) {
                log.warn("【OutboxService】任务不存在，outboxId: {}, shardId: {}", outboxId, shardId);
                return;
            }

            Outbox outbox = tasks.getFirst();

            // 2. 调用核心处理方法（复用逻辑）
            processTaskWithOutbox(outbox);

        } catch (Exception e) {
            log.error("【OutboxService】处理任务异常，outboxId: {}, shardId: {}", outboxId, shardId, e);
        }
    }

    /**
     * 处理Outbox任务（旧方法，已废弃）
     *
     * @deprecated 此方法会触发广播查询，性能差。定时扫表请使用 processTask(Long outboxId, Integer shardId)
     * @param outboxId Outbox任务ID
     */
    @Deprecated
    public void processTask(Long outboxId) {
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
