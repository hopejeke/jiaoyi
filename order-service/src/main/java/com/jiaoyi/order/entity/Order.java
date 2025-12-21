package com.jiaoyi.order.entity;

import com.jiaoyi.order.enums.OrderTypeEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体类（在线点餐）
 * 保留库存锁定、优惠券等电商功能
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    private Long id;
    
    /**
     * 餐馆ID（用于分片）
     */
    private String merchantId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单类型（枚举）
     */
    private OrderTypeEnum orderType;
    
    /**
     * 订单状态：1-已下单，100-已支付，-1-已取消等
     */
    private Integer status;
    
    /**
     * 本地订单状态：1-已下单，100-成功，200-支付失败等
     */
    private Integer localStatus;
    
    /**
     * 厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成
     */
    private Integer kitchenStatus;
    
    /**
     * 订单价格信息（JSON，包含 subtotal, discount, charge, deliveryFee, taxTotal, tips, total 等）
     */
    private String orderPrice;
    
    /**
     * 客户信息（JSON）
     */
    private String customerInfo;
    
    /**
     * 配送地址（JSON）
     */
    private String deliveryAddress;
    
    /**
     * 备注
     */
    private String notes;
    
    /**
     * POS系统订单ID
     */
    private String posOrderId;
    
    /**
     * 支付方式
     */
    private String paymentMethod;
    
    /**
     * 支付状态
     */
    private String paymentStatus;
    
    /**
     * Stripe支付意图ID
     */
    private String stripePaymentIntentId;
    
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    
    /**
     * 退款原因
     */
    private String refundReason;
    
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
    
    /**
     * 订单项列表
     */
    private List<OrderItem> orderItems;
    
    /**
     * 订单使用的优惠券列表（保留优惠券功能）
     */
    private List<OrderCoupon> orderCoupons;
    
    /**
     * DoorDash 配送ID（外键，关联 deliveries.id）
     */
    private String deliveryId;
}


