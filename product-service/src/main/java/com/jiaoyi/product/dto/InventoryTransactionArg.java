package com.jiaoyi.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 库存事务参数传递类
 * 用于在sendMessageInTransaction和executeLocalTransaction之间传递数据
 * 
 * 注意：RocketMQ的sendMessageInTransaction方法只能传递一个arg参数，
 * 如果需要传递多个值，需要封装在一个对象中
 */
@Data
public class InventoryTransactionArg implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 商品ID引用（可选）
     * 用于在executeLocalTransaction执行后传递结果
     */
    private AtomicReference<Long> productIdRef;
    
    /**
     * 库存变更数量
     */
    private Integer quantity;
    
    /**
     * 订单ID
     */
    private Long orderId;
}


