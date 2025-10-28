package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    private Long id;
    
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
    private ProductStatus status;
    
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
    public enum ProductStatus {
        ACTIVE("上架"),
        INACTIVE("下架");
        
        private final String description;
        
        ProductStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
