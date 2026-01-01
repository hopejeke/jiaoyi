package com.jiaoyi.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品退款金额明细（用于计算）
 */
@Data
@Builder
public class ItemRefundAmount {
    
    /**
     * 商品退款金额
     */
    private BigDecimal itemAmount;
    
    /**
     * 税费退款金额
     */
    private BigDecimal taxAmount;
    
    /**
     * 折扣退款金额
     */
    private BigDecimal discountAmount;
    
    /**
     * 总退款金额（商品 + 税费 - 折扣）
     */
    private BigDecimal totalAmount;
}




