package com.jiaoyi.order.dto;

import com.jiaoyi.order.entity.Order;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 创建订单响应DTO
 * 参照 OO 项目的返回格式，包含订单和支付信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    
    /**
     * 订单信息
     */
    private Order order;
    
    /**
     * 支付信息（如果是在线支付）
     */
    private PaymentResponse payment;
    
    /**
     * Stripe Client Secret（信用卡支付时返回）
     */
    private String clientSecret;
    
    /**
     * Stripe Payment Intent ID（信用卡支付时返回）
     */
    private String paymentIntentId;
    
    /**
     * 支付URL（微信/支付宝扫码支付时返回）
     */
    private String paymentUrl;
    
    /**
     * 支付HTML（微信/支付宝H5支付时返回）
     */
    private String paymentHtml;
}














