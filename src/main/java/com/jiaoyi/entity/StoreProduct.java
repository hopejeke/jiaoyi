package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺商品实体类（包含所有商品字段 + store_id）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreProduct {
    
    private Long id;
    
    /**
     * 店铺ID
     */
    private Long storeId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品描述
     */
    private String description;
    
    /**
     * 商品单价
     */
    private BigDecimal unitPrice;
    
    /**
     * 商品图片
     */
    private String productImage;
    
    /**
     * 商品分类
     */
    private String category;
    
    /**
     * 商品状态：ACTIVE-上架，INACTIVE-下架
     */
    private StoreProductStatus status;
    
    /**
     * 是否删除：0-未删除，1-已删除（逻辑删除）
     */
    private Boolean isDelete;
    
    /**
     * 版本号（用于缓存一致性控制）
     * 当商品信息发生变化时，此版本号会递增
     */
    private Long version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 商品状态枚举
     */
    public enum StoreProductStatus {
        ACTIVE("上架"),
        INACTIVE("下架");
        
        private final String description;
        
        StoreProductStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

