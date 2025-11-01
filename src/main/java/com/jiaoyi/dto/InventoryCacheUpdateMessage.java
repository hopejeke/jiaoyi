package com.jiaoyi.dto;

import com.jiaoyi.entity.Inventory;
import lombok.Data;

import java.io.Serializable;

/**
 * 库存缓存更新消息
 * 用于通过RocketMQ事务消息异步更新库存缓存
 */
@Data
public class InventoryCacheUpdateMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 操作类型
     */
    public enum OperationType {
        /**
         * 锁定库存（下单时）
         */
        LOCK,
        /**
         * 扣减库存（支付成功后）
         */
        DEDUCT,
        /**
         * 解锁库存（订单取消）
         */
        UNLOCK,
        /**
         * 刷新缓存（从数据库重新加载）
         */
        REFRESH,
        /**
         * 删除缓存
         */
        DELETE
    }
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 操作类型
     */
    private OperationType operationType;
    
    /**
     * 操作数量（锁定、扣减、解锁的数量）
     */
    private Integer quantity;
    
    /**
     * 订单ID（可选）
     */
    private Long orderId;
    
    /**
     * 消息时间戳
     */
    private Long timestamp;
    
    /**
     * 消息版本号（用于去重）
     */
    private Long messageVersion;
    
    /**
     * 库存数据（用于LOCK/DEDUCT/UNLOCK操作，在executeLocalTransaction中执行数据库操作）
     * 注意：对于REFRESH操作，此字段为null，需要从数据库重新加载
     */
    private Inventory inventory;
    
    /**
     * 操作前库存数据（用于记录变更前状态）
     */
    private Inventory beforeInventory;
}

