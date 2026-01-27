package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单对账响应（资金分解）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderReconciliationResponse {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单总金额
     */
    private BigDecimal orderTotal;
    
    /**
     * 资金分解明细
     */
    private FinancialBreakdown breakdown;
    
    /**
     * 退款分解明细（如果有退款）
     */
    private List<RefundBreakdown> refundBreakdowns;
    
    /**
     * 资金分解明细
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialBreakdown {
        /**
         * 商品金额小计
         */
        private BigDecimal itemsSubtotal;
        
        /**
         * 税费
         */
        private BigDecimal tax;
        
        /**
         * 配送费
         */
        private BigDecimal deliveryFee;
        
        /**
         * 小费
         */
        private BigDecimal tips;
        
        /**
         * 平台抽成（手续费）
         */
        private BigDecimal platformCharge;
        
        /**
         * 优惠券折扣
         */
        private BigDecimal couponDiscount;
        
        /**
         * 商户实收金额（订单总额 - 平台抽成）
         */
        private BigDecimal merchantReceivable;
        
        /**
         * 平台收入（平台抽成）
         */
        private BigDecimal platformRevenue;
    }
    
    /**
     * 退款分解明细
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundBreakdown {
        /**
         * 退款ID
         */
        private Long refundId;
        
        /**
         * 退款请求号
         */
        private String requestNo;
        
        /**
         * 退款总金额
         */
        private BigDecimal refundAmount;
        
        /**
         * 退款明细
         */
        private RefundFinancialBreakdown breakdown;
        
        /**
         * 退款时间
         */
        private LocalDateTime refundTime;
        
        /**
         * 退款状态
         */
        private String status;
    }
    
    /**
     * 退款资金分解明细
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundFinancialBreakdown {
        /**
         * 商品退款金额
         */
        private BigDecimal itemsRefund;
        
        /**
         * 税费退款
         */
        private BigDecimal taxRefund;
        
        /**
         * 配送费退款
         */
        private BigDecimal deliveryFeeRefund;
        
        /**
         * 小费退款
         */
        private BigDecimal tipsRefund;
        
        /**
         * 平台抽成回补
         */
        private BigDecimal commissionReversal;
        
        /**
         * 优惠券退款
         */
        private BigDecimal couponRefund;
        
        /**
         * 商户应退金额
         */
        private BigDecimal merchantRefundable;
        
        /**
         * 平台应退金额（抽成回补）
         */
        private BigDecimal platformRefundable;
    }
}








