package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车项实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCartItem {
    
    private Long id;
    
    /**
     * 购物车ID
     */
    private Long cartId;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * SKU ID（如果商品有SKU）
     */
    private Long skuId;
    
    /**
     * SKU名称（冗余字段，用于显示）
     */
    private String skuName;
    
    /**
     * SKU属性（JSON格式，冗余字段，用于显示）
     */
    private String skuAttributes;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品图片
     */
    private String productImage;
    
    /**
     * 商品单价
     */
    private BigDecimal unitPrice;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 小计金额（unitPrice * quantity）
     */
    private BigDecimal subtotal;
    
    /**
     * 选项（JSON数组，用于存储商品选项）
     */
    private String options;
    
    /**
     * 套餐详情（JSON，如果是套餐商品）
     */
    private String comboDetail;
    
    /**
     * 版本号（用于乐观锁）
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
}


