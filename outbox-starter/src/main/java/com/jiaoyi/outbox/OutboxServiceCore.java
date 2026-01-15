package com.jiaoyi.outbox;

import com.jiaoyi.outbox.entity.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Outbox 服务核心逻辑（通用实现）
 * 提供 enqueue 方法
 * 
 * 注意：这个类不是 Spring Bean，所以 @Transactional 不会生效
 * 事务需要在调用方（OutboxService）中管理
 */
@RequiredArgsConstructor
@Slf4j
public class OutboxServiceCore {
    
    private final com.jiaoyi.outbox.repository.OutboxRepository outboxRepository;
    @SuppressWarnings("unused")
    private final int shardCount; // 保留字段，用于未来扩展（如分片验证等）
    private final String table; // 表名（用于隔离不同服务的任务：order_outbox、stock_outbox）
    
    /**
     * 写入outbox表（通用方法）
     * 
     * 注意：这个方法没有 @Transactional，因为 OutboxServiceCore 不是 Spring Bean
     * 事务由调用方（OutboxService.enqueue）管理
     * 
     * @param type 任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）
     * @param bizKey 业务键（如：orderId，用于唯一约束和幂等）
     * @param payload 任务负载（JSON格式）
     * @param topic RocketMQ Topic（MQ 类型任务使用，可选）
     * @param tag RocketMQ Tag（MQ 类型任务使用，可选）
     * @param messageKey 消息Key（MQ 类型任务使用，可选）
     * @param shardingKey 分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，必须传入，不能为null）
     * @param shardId 分片ID（用于扫描优化，从 shardingKey 计算得出）
     * @return Outbox记录
     */
    public Outbox enqueue(String type, String bizKey, String payload, 
                         String topic, String tag, String messageKey, String shardingKey, Integer shardId) {
        try {
            if (shardingKey == null || shardingKey.isEmpty()) {
                throw new IllegalArgumentException("shardingKey 不能为 null 或空，必须传入用于分库路由");
            }
            
            LocalDateTime now = LocalDateTime.now();
            
            // 从 shardingKey 中解析 storeId（如果 shardingKey 是 storeId 的字符串形式）
            // 注意：ShardingSphere 使用 store_id 作为分片键，所以需要将 shardingKey 转换为 Long
            Long storeId = null;
            try {
                // 尝试将 shardingKey 解析为 Long（假设 shardingKey 就是 storeId）
                storeId = Long.parseLong(shardingKey);
            } catch (NumberFormatException e) {
                // 如果 shardingKey 不是数字，说明可能是 merchantId 等其他值
                // 这种情况下，storeId 可能需要在调用方传入，或者从其他地方获取
                log.warn("shardingKey 不是数字格式，无法解析为 storeId: {}", shardingKey);
            }
            
            Outbox outbox = Outbox.builder()
                    .type(type)
                    .bizKey(bizKey)
                    .shardingKey(shardingKey) // 通用分片键，用于分库路由
                    .storeId(storeId) // store_id 用于 ShardingSphere 分片路由
                    .shardId(shardId) // 用于扫描优化，由业务方计算传入
                    .topic(topic)
                    .tag(tag)
                    .messageKey(messageKey)
                    .payload(payload)
                    .status(Outbox.OutboxStatus.NEW)
                    .retryCount(0)
                    .nextRetryTime(null) // NEW 状态不需要重试时间
                    .lockOwner(null)
                    .lockTime(null)
                    .lastError(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .completedAt(null)
                    .build();
            
            outboxRepository.insert(table, outbox);
            
            log.info("已写入outbox表 {}，ID: {}, type: {}, bizKey: {}, shardingKey: {}, shardId: {}", 
                    table, outbox.getId(), type, bizKey, shardingKey, shardId);
            
            return outbox;
        } catch (Exception e) {
            log.error("写入outbox表失败，type: {}, bizKey: {}, shardingKey: {}", type, bizKey, shardingKey, e);
            throw new RuntimeException("写入outbox表失败", e);
        }
    }
}

