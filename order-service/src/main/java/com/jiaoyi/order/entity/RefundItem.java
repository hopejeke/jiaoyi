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
     * 商户ID
     */
    private String merchantId;
    
    /**
     * 门店ID（用于分片，与商品服务保持一致）
     */
    private Long storeId;
    
    /**
     * 分片ID（0-1023，基于 storeId 计算，用于分库分表路由）
     * 注意：此字段必须与 storeId 一起设置，确保分片一致性
     */
    private Integer shardId;
    
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

