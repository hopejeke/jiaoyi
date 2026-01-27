package com.jiaoyi.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 取消订单命令（用于 Outbox）
 * 包含优惠券退还和库存解锁所需的所有信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderCommand {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 商品ID列表（用于库存解锁）
     */
    private List<Long> productIds;
    
    /**
     * SKU ID列表（用于库存解锁）
     */
    private List<Long> skuIds;
    
    /**
     * 数量列表（用于库存解锁）
     */
    private List<Integer> quantities;
}


