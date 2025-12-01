package com.jiaoyi.product.entity;

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
     * SKU ID（关联product_sku.id）
     * 如果为null，表示商品级别的库存（兼容旧数据）
     */
    private Long skuId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * SKU名称（如果存在SKU）
     */
    private String skuName;
    
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


