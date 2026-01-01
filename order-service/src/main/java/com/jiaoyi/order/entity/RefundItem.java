package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款明细实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundItem {
    
    /**
     * 退款明细ID
     */
    private Long refundItemId;
    
    /**
     * 退款单ID
     */
    private Long refundId;
    
    /**
     * 商户ID（分片键）
     */
    private String merchantId;
    
    /**
     * 订单项ID（如果按商品退款）
     */
    private Long orderItemId;
    
    /**
     * 退款科目：ITEM, TAX, DELIVERY_FEE, TIPS, CHARGE, DISCOUNT
     */
    private String subject;
    
    /**
     * 退款数量（仅商品退款时有效）
     */
    private Integer refundQty;
    
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    
    /**
     * 税费退款（仅商品退款时有效）
     */
    private BigDecimal taxRefund;
    
    /**
     * 折扣退款（仅商品退款时有效）
     */
    private BigDecimal discountRefund;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

