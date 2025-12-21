package com.jiaoyi.coupon.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券使用记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponUsage {
    
    private Long id;
    
    /**
     * 优惠券ID
     */
    private Long couponId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 优惠券代码
     */
    private String couponCode;
    
    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;
    
    /**
     * 使用时间
     */
    private LocalDateTime usedTime;
    
    /**
     * 订单金额（使用优惠券前的金额）
     */
    private BigDecimal orderAmount;
    
    /**
     * 实际支付金额（使用优惠券后的金额）
     */
    private BigDecimal actualAmount;
    
    /**
     * 使用状态：USED-已使用，REFUNDED-已退款
     */
    private UsageStatus status;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 使用状态枚举
     */
    public enum UsageStatus {
        USED("已使用"),
        REFUNDED("已退款");
        
        private final String description;
        
        UsageStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}


