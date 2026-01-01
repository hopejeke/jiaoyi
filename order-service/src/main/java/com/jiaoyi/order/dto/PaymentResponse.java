package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    /**
     * 支付ID
     */
    private Long paymentId;
    
    /**
     * 支付状态：PENDING-待支付，SUCCESS-支付成功，FAILED-支付失败
     */
    private String status;
    
    /**
     * 支付方式
     */
    private String paymentMethod;
    
    /**
     * 支付金额（分）
     */
    private BigDecimal amount;
    
    /**
     * 支付时间
     */
    private LocalDateTime payTime;
    
    /**
     * 第三方支付平台交易号
     */
    private String thirdPartyTradeNo;
    
    /**
     * 支付URL（用于跳转支付）
     */
    private String payUrl;
    
    /**
     * 二维码内容（用于扫码支付）
     */
    private String qrCode;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * Stripe Client Secret（用于前端确认支付）
     */
    private String clientSecret;
    
    /**
     * Stripe Payment Intent ID
     */
    private String paymentIntentId;
}


