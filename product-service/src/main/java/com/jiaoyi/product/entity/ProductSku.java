package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品SKU实体类
 * SKU（Stock Keeping Unit）：库存量单位，商品的最小销售单元
 * 例如：同一件T恤，不同颜色、不同尺寸就是不同的SKU
 * 
 * @author Administrator
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSku {
    
    /**
     * SKU ID（主键）
     */
    private Long id;
    
    /**
     * 商品ID（关联store_products.id）
     */
    private Long productId;
    
    /**
     * 店铺ID（冗余字段，用于分片）
     */
    private Long storeId;
    
    /**
     * 分片ID（0-1023，基于storeId计算，用于分库分表路由）
     */
    private Integer productShardId;
    
    /**
     * SKU编码（唯一标识，如：PROD001-RED-L）
     */
    private String skuCode;
    
    /**
     * SKU属性（JSON格式，如：{"color":"红色","size":"L"}）
     */
    private String skuAttributes;
    
    /**
     * SKU名称（如：红色 L码）
     */
    private String skuName;
    
    /**
     * SKU价格（如果与商品价格不同）
     */
    private BigDecimal skuPrice;
    
    /**
     * SKU图片（如果与商品图片不同）
     */
    private String skuImage;
    
    /**
     * SKU状态：ACTIVE-启用，INACTIVE-禁用
     */
    private SkuStatus status;
    
    /**
     * 是否删除：0-未删除，1-已删除（逻辑删除）
     */
    private Boolean isDelete;
    
    /**
     * 版本号（用于缓存一致性控制）
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
     * SKU状态枚举
     */
    public enum SkuStatus {
        ACTIVE("启用"),
        INACTIVE("禁用");
        
        private final String description;
        
        SkuStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

