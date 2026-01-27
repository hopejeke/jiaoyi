package com.jiaoyi.order.dto;

import com.jiaoyi.order.enums.RefundType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 退款请求号（幂等键，客户端生成，建议格式：REF + timestamp + random）
     */
    private String requestNo;
    
    /**
     * 退款类型：BY_ITEMS（按商品退款）或 BY_AMOUNT（按金额退款）
     */
    private RefundType refundType;
    
    /**
     * 按商品退款：指定要退的商品和数量
     */
    private List<RefundItemRequest> refundItems;
    
    /**
     * 按金额退款：指定退款金额（会自动分配到商品）
     */
    private BigDecimal refundAmount;
    
    /**
     * 是否退配送费
     */
    private Boolean refundDeliveryFee = false;
    
    /**
     * 是否退小费
     */
    private Boolean refundTips = false;
    
    /**
     * 是否退服务费
     */
    private Boolean refundCharge = false;
    
    /**
     * 退款原因
     */
    private String reason;
}








