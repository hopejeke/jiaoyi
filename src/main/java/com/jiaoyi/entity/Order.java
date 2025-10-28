package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    private Long id;
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单状态：PENDING-待支付，PAID-已支付，SHIPPED-已发货，DELIVERED-已送达，CANCELLED-已取消
     */
    private OrderStatus status;
    
    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 总优惠金额
     */
    private BigDecimal totalDiscountAmount;
    
    /**
     * 实际支付金额
     */
    private BigDecimal actualAmount;
    
    /**
     * 收货人姓名
     */
    private String receiverName;
    
    /**
     * 收货人电话
     */
    private String receiverPhone;
    
    /**
     * 收货地址
     */
    private String receiverAddress;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 订单项列表
     */
    private List<OrderItem> orderItems;
    
    /**
     * 订单使用的优惠券列表
     */
    private List<OrderCoupon> orderCoupons;
}
