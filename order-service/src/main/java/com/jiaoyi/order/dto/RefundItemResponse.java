package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 退款明细响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundItemResponse {
    
    /**
     * 退款明细ID
     */
    private Long refundItemId;
    
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




