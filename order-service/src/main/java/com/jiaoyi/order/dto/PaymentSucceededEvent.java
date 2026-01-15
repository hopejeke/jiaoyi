package com.jiaoyi.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付成功事件（用于 MQ 消息）
 * 由 Webhook 回调写入 outbox，异步处理支付成功业务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 事件ID（Stripe event.id 或支付宝交易号）
     * 用于幂等
     */
    private String eventId;
    
    /**
     * 事件类型（payment_intent.succeeded, TRADE_SUCCESS 等）
     */
    private String eventType;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * Stripe Payment Intent ID（Stripe 支付时使用）
     */
    private String paymentIntentId;
    
    /**
     * 第三方交易号（支付宝 trade_no 或 Stripe charge.id）
     */
    private String thirdPartyTradeNo;
    
    /**
     * 支付服务类型（STRIPE, ALIPAY）
     */
    private String paymentService;
}



