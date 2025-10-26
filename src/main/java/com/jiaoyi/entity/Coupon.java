package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    
    private Long id;
    
    /**
     * 优惠券代码
     */
    private String couponCode;
    
    /**
     * 优惠券名称
     */
    private String couponName;
    
    /**
     * 优惠券类型：FIXED-固定金额，PERCENTAGE-百分比折扣
     */
    private CouponType type;
    
    /**
     * 优惠券面值（固定金额或百分比）
     */
    private BigDecimal value;
    
    /**
     * 最低消费金额（使用优惠券的最低订单金额）
     */
    private BigDecimal minOrderAmount;
    
    /**
     * 最大优惠金额（百分比折扣时的最大优惠金额）
     */
    private BigDecimal maxDiscountAmount;
    
    /**
     * 优惠券状态：ACTIVE-有效，INACTIVE-无效，EXPIRED-已过期
     */
    private CouponStatus status;
    
    /**
     * 总发行数量
     */
    private Integer totalQuantity;
    
    /**
     * 已使用数量
     */
    private Integer usedQuantity;
    
    /**
     * 每人限领数量
     */
    private Integer limitPerUser;
    
    /**
     * 适用商品类型：ALL-全部商品，CATEGORY-指定分类，PRODUCT-指定商品
     */
    private ApplicableType applicableType;
    
    /**
     * 适用商品ID列表（JSON格式）
     */
    private String applicableProducts;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 优惠券类型枚举
     */
    public enum CouponType {
        FIXED("固定金额"),
        PERCENTAGE("百分比折扣");
        
        private final String description;
        
        CouponType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 优惠券状态枚举
     */
    public enum CouponStatus {
        ACTIVE("有效"),
        INACTIVE("无效"),
        EXPIRED("已过期");
        
        private final String description;
        
        CouponStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 适用类型枚举
     */
    public enum ApplicableType {
        ALL("全部商品"),
        CATEGORY("指定分类"),
        PRODUCT("指定商品");
        
        private final String description;
        
        ApplicableType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
