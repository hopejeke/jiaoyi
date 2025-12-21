package com.jiaoyi.order.entity;

import com.jiaoyi.order.enums.DoorDashWebhookLogStatusEnum;
import com.jiaoyi.order.enums.DoorDashEventTypeEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DoorDash Webhook 事件日志（用于幂等性去重）
 * 记录每次 Webhook 回调，基于 event_id 去重
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoorDashWebhookLog {
    
    /**
     * 日志ID
     */
    private Long id;
    
    /**
     * 事件ID（DoorDash 返回的唯一事件ID，用于去重）
     */
    private String eventId;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 配送ID（delivery_id）
     */
    private String deliveryId;
    
    /**
     * 外部订单ID（external_delivery_id）
     */
    private String externalDeliveryId;
    
    /**
     * 事件类型（枚举）
     */
    private DoorDashEventTypeEnum eventType;
    
    /**
     * Webhook 数据（JSON，存储完整的 webhook payload）
     */
    private String payload;
    
    /**
     * 处理状态（枚举）
     */
    private DoorDashWebhookLogStatusEnum status;
    
    /**
     * 处理结果（JSON，存储处理结果）
     */
    private String result;
    
    /**
     * 错误信息（如果处理失败）
     */
    private String errorMessage;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 处理时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

