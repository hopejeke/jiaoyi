package com.jiaoyi.order.entity;

import com.jiaoyi.order.enums.DeliveryStatusEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送实体类
 * 从 Order 表中分离出来的配送信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {
    
    /**
     * 配送ID（DoorDash 返回的 delivery_id）
     */
    private String id;
    
    /**
     * 订单ID（关联 orders.id）
     */
    private Long orderId;
    
    /**
     * 商户ID（用于分片）
     */
    private String merchantId;
    
    /**
     * 外部订单ID（external_delivery_id，格式：order_123）
     */
    private String externalDeliveryId;
    
    /**
     * DoorDash 配送状态（枚举）
     */
    private DeliveryStatusEnum status;
    
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
     * DoorDash 配送跟踪 URL
     * 用户可以通过此 URL 查看配送进度和地图
     */
    private String trackingUrl;
    
    /**
     * 配送距离（英里）
     * 从商户到用户的距离
     */
    private BigDecimal distanceMiles;
    
    /**
     * 预计送达时间（分钟）
     * 从当前时间到预计送达的时间
     */
    private Integer etaMinutes;
    
    /**
     * 骑手姓名
     */
    private String driverName;
    
    /**
     * 骑手电话
     */
    private String driverPhone;
    
    /**
     * 额外数据（JSON，包含 deliveryInfo 和 priceInfo）
     * 用于发送给 POS 系统
     */
    private String additionalData;
    
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





