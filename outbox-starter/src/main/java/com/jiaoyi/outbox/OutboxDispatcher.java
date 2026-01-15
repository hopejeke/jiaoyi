package com.jiaoyi.outbox;

import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.event.OutboxDeadLetterEvent;
import com.jiaoyi.outbox.service.OutboxClaimService;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 任务分发器（简化版：抢占式 claim + 发送 + ack）
 * 只做三件事：落库（同事务）→ 定时扫表 → 发送（MQ/HTTP）→ 重试
 */
@Slf4j
public class OutboxDispatcher {

    protected final com.jiaoyi.outbox.repository.OutboxRepository outboxRepository;
    protected final List<OutboxHandler> handlers;

    @Autowired(required = false)
    private OutboxClaimService outboxClaimService; // 两段式 claim 服务

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher; // 可选，用于发布死信事件

    @Value("${outbox.table}")
    private String table; // 表名（从配置读取）

    @Value("${outbox.shard-count:3}")
    private int shardCount; // 分片数量（默认3个分片库）

    /**
     * 实例ID（用于 claim）
     */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 20;

    /**
     * 锁超时时间（30秒）
     */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 每次 claim 的任务数量（每个分片）
     */
    private static final int BATCH_SIZE_PER_SHARD = 50;

    /**
     * 恢复卡死任务的调度频率（10秒）
     * 轻操作，可以更频繁执行，确保及时恢复宕机实例的任务
     */
    private static final long RECOVER_INTERVAL_MS = 10000; // 10秒

    public OutboxDispatcher(com.jiaoyi.outbox.repository.OutboxRepository outboxRepository,
                            List<OutboxHandler> handlers,
                            OutboxService outboxService) {
        this.outboxRepository = outboxRepository;
        this.handlers = handlers;
    }

    /**
     * 获取表名（从配置读取）
     */
    protected String getTable() {
        return table;
    }

    /**
     * 恢复卡死任务（10秒执行一次）
     * 将锁已过期的 PROCESSING 任务恢复为 FAILED，进入统一的指数退避重试流程
     * <p>
     * 优势：
     * - 避免慢任务被误判为卡死导致并发重复执行
     * - 重试节奏可控，不会重试风暴
     * - 语义清晰，排障简单
     * <p>
     * 注意：
     * - 由于 outbox 表已改为按 store_id 分片，无法按 shard_id 路由
     * - 此任务会广播查询所有表，但这是可接受的（维护性任务，频率低）
     */
    @Scheduled(fixedDelay = RECOVER_INTERVAL_MS)
    public void recoverStuckTasks() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String table = getTable();

            // 直接恢复所有卡死的任务（不按分片，因为 outbox 表已改为按 store_id 分片）
            // 广播查询是可接受的，因为这是维护性任务，频率低（10秒一次）
            int recovered = outboxRepository.recoverStuck(table, now);
            if (recovered > 0) {
                log.info("【OutboxDispatcher】恢复卡死任务，恢复数量: {}, 表: {}", recovered, table);
            }
        } catch (Exception e) {
            log.error("【OutboxDispatcher】恢复卡死任务失败", e);
        }
    }

    /**
     * 扫表任务（1分钟执行一次，作为兜底）
     * 用于处理：漏掉的 kick、宕机恢复、锁过期重试
     */
    @Scheduled(fixedDelay = 60000) // 1分钟 = 60 * 1000 毫秒
    public void dispatch() {
        dispatchOnce(null); // 处理所有分片
    }

    /**
     * 执行一次 dispatch（事件驱动或定时兜底）
     *
     * @param targetShardId 目标分片ID（如果为 null，则处理所有分片）
     */
    public void dispatchOnce(Integer targetShardId) {
        try {
            // 检查 handlers 列表是否为空
            if (handlers == null || handlers.isEmpty()) {
                log.warn("【OutboxDispatcher】handlers 列表为空，无法处理任务！");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            String table = getTable(); // 获取表名
            LocalDateTime lockUntil = now.plus(LOCK_TIMEOUT);

            // 1. 按分片ID循环 claim（避免跨库广播）
            // 如果指定了 targetShardId，只处理该分片；否则处理所有分片
            List<Outbox> allClaimedTasks = new java.util.ArrayList<>();
            int startShardId = (targetShardId != null) ? targetShardId : 0;
            int endShardId = (targetShardId != null) ? targetShardId + 1 : shardCount;

            for (int shardId = startShardId; shardId < endShardId; shardId++) {
                // 使用两段式 claim（FOR UPDATE SKIP LOCKED），避免多实例并发锁等待
                List<Outbox> shardClaimedTasks;
                
                if (outboxClaimService != null) {
                    // 新方式：两段式 claim（推荐，MySQL 8.0.4+）
                    shardClaimedTasks = outboxClaimService.claimAndLoad(table, shardId, instanceId, lockUntil, now, BATCH_SIZE_PER_SHARD);
                } else {
                    // 降级方案：如果 OutboxClaimService 不可用，跳过该分片
                    log.warn("【OutboxDispatcher】OutboxClaimService 不可用，跳过分片: {}, 表: {}", shardId, table);
                    shardClaimedTasks = new java.util.ArrayList<>();
                }

                if (!shardClaimedTasks.isEmpty()) {
                    log.debug("【OutboxDispatcher】从分片 {} claim 到 {} 个任务，表: {}", shardId, shardClaimedTasks.size(), table);
                    allClaimedTasks.addAll(shardClaimedTasks);
                }
            }

            if (allClaimedTasks.isEmpty()) {
                return; // 没有可处理的任务
            }

            log.info("【OutboxDispatcher】开始处理 {} 个已 claim 的任务，表: {}, 分片: {}",
                    allClaimedTasks.size(), table, targetShardId != null ? targetShardId : "全部");

            // 2. 处理每个任务
            processTasks(allClaimedTasks, table, now);

        } catch (Exception e) {
            log.error("OutboxDispatcher 执行异常", e);
        }
    }

    /**
     * 处理任务列表
     */
    private void processTasks(List<Outbox> tasks, String table, LocalDateTime now) {
        for (Outbox outbox : tasks) {
            try {
                // 3.1 查找对应的 handler
                OutboxHandler handler = handlers.stream()
                        .filter(h -> h.supports(outbox.getType()))
                        .findFirst()
                        .orElse(null);

                if (handler == null) {
                    // 如果找不到 handler，释放锁并跳过（不抛异常）
                    log.warn("【OutboxDispatcher】跳过任务（当前服务不支持此类型），ID: {}, 表: {}, type: {}, bizKey: {}",
                            outbox.getId(), table, outbox.getType(), outbox.getBizKey());
                    // 释放锁，将状态改回 NEW
                    int released = outboxRepository.releaseLock(table, outbox.getId(), instanceId);
                    if (released > 0) {
                        log.debug("【OutboxDispatcher】已释放锁，任务ID: {}, type: {}", outbox.getId(), outbox.getType());
                    }
                    continue;
                }

                // 3.2 执行任务（发送 MQ 或调用 HTTP）
                handler.handle(outbox);

                // 3.3 标记为已发送（SENT）
                int updated = outboxRepository.markSent(table, outbox.getId(), instanceId);
                if (updated > 0) {
                    log.info("【OutboxDispatcher】✓ 任务发送成功，ID: {}, type: {}, bizKey: {}",
                            outbox.getId(), outbox.getType(), outbox.getBizKey());
                } else {
                    log.warn("【OutboxDispatcher】标记已发送失败，可能锁已失效，任务ID: {}", outbox.getId());
                }

            } catch (Exception e) {
                log.error("【OutboxDispatcher】✗ 处理任务失败，ID: {}, type: {}, bizKey: {}, 错误: {}",
                        outbox.getId(), outbox.getType(), outbox.getBizKey(), e.getMessage(), e);

                // 计算重试次数和下次重试时间（指数退避，上限5分钟）
                int retryCount = (outbox.getRetryCount() != null ? outbox.getRetryCount() : 0) + 1;
                long backoffSeconds = Math.min((long) Math.pow(2, retryCount), 300); // 上限300秒（5分钟）
                LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
                String errorMessage = truncate(e.getMessage());

                if (retryCount >= MAX_RETRY_COUNT) {
                    // 超过最大重试次数，标记为死信
                    int updated = outboxRepository.markDead(table, outbox.getId(), instanceId, errorMessage);
                    if (updated > 0) {
                        // 死信告警：记录详细信息（后续可接入钉钉/邮件告警）
                        String handlerName = handlers.stream()
                                .filter(h -> h.supports(outbox.getType()))
                                .findFirst()
                                .map(h -> h.getClass().getSimpleName())
                                .orElse("UNKNOWN");

                        log.error("【DEAD LETTER ALERT】任务标记为死信，需要人工介入处理！\n" +
                                        "  ID: {}\n" +
                                        "  表: {}\n" +
                                        "  类型: {}\n" +
                                        "  业务键: {}\n" +
                                        "  分片ID: {}\n" +
                                        "  Handler: {}\n" +
                                        "  重试次数: {}/{}\n" +
                                        "  最后错误: {}\n" +
                                        "  创建时间: {}\n" +
                                        "  更新时间: {}\n" +
                                        "  Payload: {}\n" +
                                        "  补偿操作: POST /outbox/{}/retry 或 POST /outbox/replay?bizKey={}",
                                outbox.getId(), table, outbox.getType(), outbox.getBizKey(),
                                outbox.getShardId(), handlerName, retryCount, MAX_RETRY_COUNT,
                                errorMessage, outbox.getCreatedAt(), now,
                                outbox.getPayload() != null && outbox.getPayload().length() > 200
                                        ? outbox.getPayload().substring(0, 200) + "..."
                                        : outbox.getPayload(),
                                outbox.getId(), outbox.getBizKey());

                        // 发布死信事件（业务方可以监听并更新订单状态等）
                        if (eventPublisher != null) {
                            eventPublisher.publishEvent(new OutboxDeadLetterEvent(outbox, handlerName, retryCount));
                        }

                        // TODO: 接入钉钉/邮件告警
                        // alertService.sendDeadLetterAlert(outbox);
                    }
                } else {
                    // 标记为失败，等待下次重试
                    int updated = outboxRepository.markFailed(
                            table, outbox.getId(), instanceId, retryCount, nextRetryTime, errorMessage);
                    if (updated > 0) {
                        log.warn("任务标记为失败，等待重试，ID: {}, 表: {}, type: {}, bizKey: {}, retryCount: {}, nextRetryTime: {}",
                                outbox.getId(), table, outbox.getType(), outbox.getBizKey(), retryCount, nextRetryTime);
                    }
                }
            }
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
}
