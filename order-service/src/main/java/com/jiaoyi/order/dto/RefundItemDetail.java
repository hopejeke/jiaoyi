package com.jiaoyi.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款明细详情（用于计算）
 */
@Data
@Builder
public class RefundItemDetail {
    
    /**
     * 订单项ID
     */
    private Long orderItemId;
    
    /**
     * 退款科目
     */
    private String subject;
    
    /**
     * 退款数量
     */
    private Integer refundQty;
    
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    
    /**
     * 税费退款
     */
    private BigDecimal taxRefund;
    
    /**
     * 折扣退款
     */
    private BigDecimal discountRefund;
}




