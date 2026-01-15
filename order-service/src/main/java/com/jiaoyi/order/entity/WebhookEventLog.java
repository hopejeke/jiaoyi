package com.jiaoyi.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Webhook 事件日志实体
 * 用于事件幂等，确保同一事件只处理一次
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventLog {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 第三方事件ID（Stripe event.id 或支付宝交易号）
     * 唯一键，用于幂等
     */
    private String eventId;
    
    /**
     * 事件类型（payment_intent.succeeded, TRADE_SUCCESS 等）
     */
    private String eventType;
    
    /**
     * Stripe Payment Intent ID
     */
    private String paymentIntentId;
    
    /**
     * 第三方交易号（支付宝 trade_no 或 Stripe charge.id）
     */
    private String thirdPartyTradeNo;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 状态：RECEIVED-已接收，PROCESSED-已处理，FAILED-处理失败
     */
    private EventStatus status;
    
    /**
     * 错误信息（处理失败时记录）
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 处理完成时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 事件状态枚举
     */
    public enum EventStatus {
        RECEIVED,   // 已接收
        PROCESSED,  // 已处理
        FAILED      // 处理失败
    }
}



