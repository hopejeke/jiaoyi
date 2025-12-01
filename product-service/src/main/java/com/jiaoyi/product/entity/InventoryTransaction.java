package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存变动记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {
    
    private Long id;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 订单ID（如果是订单相关变动）
     */
    private Long orderId;
    
    /**
     * 变动类型：IN-入库，OUT-出库，LOCK-锁定，UNLOCK-解锁
     */
    private TransactionType transactionType;
    
    /**
     * 变动数量（正数为增加，负数为减少）
     */
    private Integer quantity;
    
    /**
     * 变动前库存
     */
    private Integer beforeStock;
    
    /**
     * 变动后库存
     */
    private Integer afterStock;
    
    /**
     * 变动前锁定库存
     */
    private Integer beforeLocked = 0;
    
    /**
     * 变动后锁定库存
     */
    private Integer afterLocked = 0;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 库存变动类型枚举
     */
    public enum TransactionType {
        IN("入库"),
        OUT("出库"),
        LOCK("锁定"),
        UNLOCK("解锁"),
        ADJUST("调整");
        
        private final String description;
        
        TransactionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}


