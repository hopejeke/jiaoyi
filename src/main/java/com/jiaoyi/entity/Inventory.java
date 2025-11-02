package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    
    private Long id;
    
    /**
     * 店铺ID
     */
    private Long storeId;
    
    /**
     * 商品ID（关联store_products.id）
     */
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 当前库存数量
     */
    private Integer currentStock;
    
    /**
     * 锁定库存数量（已下单但未支付）
     */
    private Integer lockedStock = 0;
    
    
    /**
     * 最低库存预警线
     */
    private Integer minStock = 0;
    
    /**
     * 最大库存容量
     */
    private Integer maxStock;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
