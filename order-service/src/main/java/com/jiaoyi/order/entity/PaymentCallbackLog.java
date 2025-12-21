package com.jiaoyi.order.entity;

import com.jiaoyi.order.enums.PaymentCallbackLogStatusEnum;
import com.jiaoyi.order.enums.PaymentServiceEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付回调日志（用于幂等性去重）
 * 记录每次支付回调，基于 thirdPartyTradeNo 去重
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackLog {
    
    /**
     * 日志ID
     */
    private Long id;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 支付记录ID
     */
    private Long paymentId;
    
    /**
     * 第三方交易号（唯一键，用于去重）
     */
    private String thirdPartyTradeNo;
    
    /**
     * 支付服务（枚举）
     */
    private PaymentServiceEnum paymentService;
    
    /**
     * 回调数据（JSON，存储完整的回调数据）
     */
    private String callbackData;
    
    /**
     * 处理状态（枚举）
     */
    private PaymentCallbackLogStatusEnum status;
    
    /**
     * 处理结果（JSON，存储处理结果）
     */
    private String result;
    
    /**
     * 错误信息（如果处理失败）
     */
    private String errorMessage;
    
    /**
     * 处理时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

