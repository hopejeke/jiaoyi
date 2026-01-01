package com.jiaoyi.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息发件箱实体（Outbox Pattern）
 * 用于在本地事务中记录需要发送的消息，保证事务一致性
 * 解决支付成功后扣减库存、订单取消解锁库存等场景的数据一致性问题
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
     * 分片ID（用于分片处理）
     */
    private Integer shardId;
    
    /**
     * RocketMQ Topic
     */
    private String topic;
    
    /**
     * RocketMQ Tag
     */
    private String tag;
    
    /**
     * 消息Key（用于消息追踪）
     */
    private String messageKey;
    
    /**
     * 消息体（JSON格式）
     */
    private String messageBody;
    
    /**
     * 状态：PENDING-待发送，SENT-已发送，FAILED-发送失败
     */
    private OutboxStatus status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 错误信息（发送失败时记录）
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 发送时间
     */
    private LocalDateTime sentAt;
    
    /**
     * Outbox状态枚举
     */
    public enum OutboxStatus {
        PENDING,  // 待发送
        SENT,     // 已发送
        FAILED    // 发送失败
    }
}

