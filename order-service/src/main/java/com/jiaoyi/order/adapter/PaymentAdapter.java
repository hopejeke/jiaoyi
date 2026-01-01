package com.jiaoyi.order.adapter;

import com.jiaoyi.order.enums.PaymentServiceEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付适配器接口
 * 统一第三方支付调用，支持超时和 Fallback
 */
public interface PaymentAdapter {
    
    /**
     * 创建退款
     * 
     * @param request 退款请求
     * @return 退款结果
     */
    RefundResult createRefund(RefundRequest request);
    
    /**
     * 查询退款状态
     * 
     * @param thirdPartyRefundId 第三方退款ID
     * @return 退款状态
     */
    RefundStatusResult queryRefundStatus(String thirdPartyRefundId);
    
    /**
     * 退款请求
     */
    @Data
    class RefundRequest {
        private PaymentServiceEnum paymentService;
        private String paymentIntentId; // Stripe Payment Intent ID
        private String thirdPartyTradeNo; // 支付宝交易号
        private BigDecimal refundAmount;
        private String reason;
        private String requestNo; // 退款请求号（幂等键）
    }
    
    /**
     * 退款结果
     */
    @Data
    class RefundResult {
        private boolean success;
        private String thirdPartyRefundId;
        private String errorMessage;
        private RefundStatus status; // PROCESSING, SUCCEEDED, FAILED
        
        public static RefundResult failed(String errorMessage) {
            RefundResult result = new RefundResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            result.setStatus(RefundStatus.FAILED);
            return result;
        }
    }
    
    /**
     * 退款状态结果
     */
    @Data
    class RefundStatusResult {
        private boolean success;
        private RefundStatus status;
        private String errorMessage;
        
        public static RefundStatusResult failed(String errorMessage) {
            RefundStatusResult result = new RefundStatusResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }
    }
    
    /**
     * 退款状态
     */
    enum RefundStatus {
        PROCESSING,
        SUCCEEDED,
        FAILED
    }
}

