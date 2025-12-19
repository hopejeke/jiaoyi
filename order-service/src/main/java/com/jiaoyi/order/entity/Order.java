package com.jiaoyi.order.entity;

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
     * 订单类型：PICKUP/DELIVERY/SELF_DINE_IN
     */
    private String orderType;
    
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
     * DoorDash 配送ID
     */
    private String deliveryId;
    
    /**
     * DoorDash 报价费用（下单前获取的 quoted_fee）
     */
    private BigDecimal deliveryFeeQuoted;
    
    /**
     * DoorDash 报价时间（用于检查报价是否过期）
     */
    private LocalDateTime deliveryFeeQuotedAt;
    
    /**
     * DoorDash 报价ID（quote_id，用于接受报价）
     */
    private String deliveryFeeQuoteId;
    
    /**
     * 用户实际支付的配送费（delivery_fee_charged_to_user）
     * 可能包含 buffer 或平台补贴
     */
    private BigDecimal deliveryFeeChargedToUser;
    
    /**
     * DoorDash 账单费用（delivery_fee_billed）
     * 从 DoorDash 账单中获取的实际费用
     */
    private BigDecimal deliveryFeeBilled;
    
    /**
     * 配送费差额归因（JSON）
     * 格式：{
     *   "waitingFee": {"amount": 2.00, "attribution": "MERCHANT"},  // 商户出餐慢
     *   "extraFee": {"amount": 1.50, "attribution": "USER"},        // 用户改址
     *   "platformSubsidy": {"amount": 0.50, "attribution": "PLATFORM"}  // 平台补贴
     * }
     */
    private String deliveryFeeVariance;
    
    /**
     * 额外数据（JSON，包含 deliveryInfo 和 priceInfo）
     * 用于发送给 POS 系统
     */
    private String additionalData;
    
    /**
     * DoorDash 配送跟踪 URL
     * 用户可以通过此 URL 查看配送进度和地图
     */
    private String deliveryTrackingUrl;
    
    /**
     * 配送距离（英里）
     * 从商户到用户的距离
     */
    private BigDecimal deliveryDistanceMiles;
    
    /**
     * 预计送达时间（分钟）
     * 从当前时间到预计送达的时间
     */
    private Integer deliveryEtaMinutes;
    
    /**
     * DoorDash 配送状态
     * CREATED - 已创建
     * ASSIGNED - 已分配骑手
     * PICKED_UP - 已取货
     * DELIVERED - 已送达
     * CANCELLED - 已取消
     */
    private String deliveryStatus;
    
    /**
     * 骑手姓名
     */
    private String deliveryDriverName;
    
    /**
     * 骑手电话
     */
    private String deliveryDriverPhone;
}


