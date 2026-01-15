package com.jiaoyi.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MQ 消费日志实体
 * 用于消费幂等，确保同一消息只处理一次
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerLog {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 消费者组（RocketMQ Consumer Group）
     */
    private String consumerGroup;
    
    /**
     * Topic
     */
    private String topic;
    
    /**
     * Tag
     */
    private String tag;
    
    /**
     * 消息Key（eventId 或 idempotencyKey）
     * 与 consumerGroup 组成唯一键
     */
    private String messageKey;
    
    /**
     * RocketMQ MessageId（用于追踪）
     */
    private String messageId;
    
    /**
     * 状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败
     */
    private ConsumerStatus status;
    
    /**
     * 错误信息（处理失败时记录）
     */
    private String errorMessage;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 处理完成时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 消费状态枚举
     */
    public enum ConsumerStatus {
        PROCESSING,  // 处理中
        SUCCESS,     // 成功
        FAILED       // 失败
    }
}



