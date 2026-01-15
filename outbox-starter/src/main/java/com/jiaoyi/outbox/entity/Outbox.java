package com.jiaoyi.outbox.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息发件箱实体（Outbox Pattern）
 * 用于在本地事务中记录需要发送的消息，保证事务一致性
 * 支持 MQ 消息发送和 HTTP 接口调用两种执行方式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务类型（如：PAYMENT_SUCCEEDED_MQ、DEDUCT_STOCK_HTTP）
     */
    private String type;
    
    /**
     * 业务键（如：orderId，用于唯一约束和幂等）
     */
    private String bizKey;
    
    /**
     * 分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等）
     * 注意：这是业务分片键，ShardingSphere 会根据此字段路由到正确的分片库
     * 业务方需要在 ShardingSphere 配置中指定此字段作为分片键
     */
    private String shardingKey;
    
    /**
     * 门店ID（用于分片，与订单和商品服务保持一致）
     * 注意：ShardingSphere 使用 store_id 作为分片键进行数据库和表路由
     */
    private Long storeId;
    
    /**
     * 分片ID（用于扫描优化，不再用于分库路由，保留用于兼容）
     * 注意：分库路由现在使用 merchant_id 或 store_id，shard_id 仅用于扫描时的分片过滤
     */
    private Integer shardId;
    
    /**
     * RocketMQ Topic（MQ 类型任务使用）
     */
    private String topic;
    
    /**
     * RocketMQ Tag（MQ 类型任务使用）
     */
    private String tag;
    
    /**
     * 消息Key（用于消息追踪，MQ 类型任务使用）
     */
    private String messageKey;
    
    /**
     * 任务负载（JSON格式，包含任务所需的所有数据）
     */
    private String payload;
    
    /**
     * 状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信
     */
    private OutboxStatus status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * 锁持有者（实例ID，用于多实例抢锁）
     */
    private String lockOwner;
    
    /**
     * 锁定时间
     */
    private LocalDateTime lockTime;
    
    /**
     * 锁过期时间（用于抢占式 claim）
     */
    private LocalDateTime lockUntil;
    
    /**
     * 最后错误信息
     */
    private String lastError;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 完成时间（SUCCESS 时记录）
     */
    private LocalDateTime completedAt;
    
    /**
     * 兼容旧字段：messageBody（用于向后兼容）
     */
    private String messageBody;
    
    /**
     * 兼容旧字段：errorMessage（用于向后兼容）
     */
    private String errorMessage;
    
    /**
     * 兼容旧字段：sentAt（用于向后兼容）
     */
    private LocalDateTime sentAt;
    
    /**
     * Outbox状态枚举
     */
    public enum OutboxStatus {
        NEW,         // 新建
        PROCESSING,  // 处理中（已 claim）
        SENT,        // 已发送（成功）
        FAILED,      // 失败（可重试）
        DEAD,        // 死信（超过最大重试次数）
        // 兼容旧状态
        SUCCESS,     // 成功（兼容旧版本，等同于 SENT）
        PENDING      // 待发送（旧版本，兼容，等同于 NEW）
    }
}

