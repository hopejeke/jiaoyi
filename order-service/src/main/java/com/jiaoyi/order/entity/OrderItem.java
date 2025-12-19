package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项实体类（在线点餐）
 * 保留 productId/saleItemId 用于库存锁定功能
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    
    private Long id;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 餐馆ID（用于分片）
     */
    private String merchantId;
    
    /**
     * 商品ID（用于库存锁定，可以是 productId 或 saleItemId）
     */
    private Long productId;
    
    /**
     * 销售项ID（POS系统ID）
     */
    private Long saleItemId;
    
    /**
     * 订单项ID
     */
    private Long orderItemId;
    
    /**
     * 商品名称
     */
    private String itemName;
    
    /**
     * 商品图片
     */
    private String productImage;
    
    /**
     * 商品单价
     */
    private BigDecimal itemPrice;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 小计金额
     */
    private BigDecimal itemPriceTotal;
    
    /**
     * 显示价格
     */
    private BigDecimal displayPrice;
    
    /**
     * 详细价格ID（在线点餐）
     */
    private Long detailPriceId;
    
    /**
     * 详细价格信息（在线点餐，JSON）
     */
    private String detailPriceInfo;
    
    /**
     * 尺寸ID（在线点餐）
     */
    private Long sizeId;
    
    /**
     * 选项（在线点餐，JSON数组）
     */
    private String options;
    
    /**
     * 套餐详情（在线点餐，JSON）
     */
    private String comboDetail;
    
    /**
     * 折扣名称（在线点餐）
     */
    private String discountName;
    
    /**
     * 费用名称（在线点餐）
     */
    private String chargeName;
    
    /**
     * 课程编号（在线点餐）
     */
    private String courseNumber;
    
    /**
     * 商品类型（在线点餐）
     */
    private String itemType;
    
    /**
     * 商品编号（在线点餐）
     */
    private String itemNumber;
    
    /**
     * 厨房索引（在线点餐）
     */
    private Integer kitchenIndex;
    
    /**
     * 分类ID（在线点餐）
     */
    private Long categoryId;
    
    /**
     * 版本号（用于乐观锁和缓存一致性）
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


