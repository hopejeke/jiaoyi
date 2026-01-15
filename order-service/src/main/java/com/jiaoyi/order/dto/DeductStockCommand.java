package com.jiaoyi.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 库存扣减命令（用于 MQ 消息）
 * 由支付成功业务处理写入 outbox，异步扣减库存
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeductStockCommand implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 商品ID列表
     */
    private List<Long> productIds;
    
    /**
     * SKU ID列表
     */
    private List<Long> skuIds;
    
    /**
     * 数量列表
     */
    private List<Integer> quantities;
    
    /**
     * 幂等键（用于库存服务幂等）
     * 格式：orderId 或 orderId_paymentIntentId
     */
    private String idempotencyKey;
}



