package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车实体类
 * 支持用户购物车和桌码购物车（堂食场景）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart {
    
    private Long id;
    
    /**
     * 用户ID（如果用户已登录）
     */
    private Long userId;
    
    /**
     * 桌码ID（堂食场景，如果 tableId 存在则按桌码管理购物车）
     */
    private Integer tableId;
    
    /**
     * 餐馆ID
     */
    private String merchantId;
    
    /**
     * 门店ID（用于分片）
     */
    private Long storeId;
    
    /**
     * 分片ID（0-1023，基于 storeId 计算）
     */
    private Integer shardId;
    
    /**
     * 购物车总金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 购物车商品总数
     */
    private Integer totalQuantity;
    
    /**
     * 购物车过期时间（默认24小时）
     */
    private LocalDateTime expireTime;
    
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
    
    /**
     * 购物车项列表（非数据库字段，用于返回）
     */
    private List<ShoppingCartItem> items;
}


