package com.jiaoyi.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款计算结果
 */
@Data
@Builder
public class RefundCalculationResult {
    
    /**
     * 退款明细列表
     */
    private List<RefundItemDetail> refundItems;
    
    /**
     * 退款总金额
     */
    private BigDecimal totalRefundAmount;
}




