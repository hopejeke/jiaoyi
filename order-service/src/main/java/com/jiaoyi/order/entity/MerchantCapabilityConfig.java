package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户高峰拒单配置和状态
 * 存储每个商户的高峰拒单配置参数和当前状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCapabilityConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 商户ID（分片键）
     */
    private String merchantId;
    
    /**
     * 是否启用限流
     */
    private Boolean enable;
    
    /**
     * 订单数量阈值（触发限流的订单数）
     */
    private Integer qtyOfOrders;
    
    /**
     * 时间窗口（分钟）
     */
    private Integer timeInterval;
    
    /**
     * 关闭持续时间（分钟）
     */
    private Integer closingDuration;
    
    /**
     * 下次开放时间（时间戳，毫秒）
     */
    private Long nextOpenAt;
    
    /**
     * 重新开放所有服务的时间（时间戳，毫秒）
     */
    private Long reOpenAllAt;
    
    /**
     * Pickup 服务操作类型（'manual' | 'system'）
     */
    private String operatePickUp;
    
    /**
     * Delivery 服务操作类型（'manual' | 'system'）
     */
    private String operateDelivery;
    
    /**
     * Togo 服务操作类型（'manual' | 'system'）
     */
    private String operateTogo;
    
    /**
     * SelfDineIn 服务操作类型（'manual' | 'system'）
     */
    private String operateSelfDineIn;
    
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

