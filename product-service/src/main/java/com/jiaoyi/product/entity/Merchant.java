package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 餐馆实体类
 * 对应 online-order-v2-backend 的 Merchant 模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {
    
    /**
     * 主键ID（雪花算法生成）
     */
    private Long id;
    
    /**
     * 餐馆ID（POS系统ID，用于分片）
     */
    private String merchantId;
    
    /**
     * 餐馆组ID
     */
    private String merchantGroupId;
    
    /**
     * 加密的餐馆ID
     */
    private String encryptMerchantId;
    
    /**
     * 餐馆名称
     */
    private String name;
    
    /**
     * 时区
     */
    private String timeZone;
    
    /**
     * 餐馆Logo
     */
    private String logo;
    
    /**
     * 短链接
     */
    private String shortUrl;
    
    /**
     * 是否支持自取
     */
    private Boolean isPickup;
    
    /**
     * 是否支持配送
     */
    private Boolean isDelivery;
    
    /**
     * 自取支付方式（JSON数组）
     */
    private String pickupPaymentAcceptance;
    
    /**
     * 配送支付方式（JSON数组）
     */
    private String deliveryPaymentAcceptance;
    
    /**
     * 自取准备时间（JSON：{"min":30,"max":60}）
     */
    private String pickupPrepareTime;
    
    /**
     * 配送准备时间（JSON：{"min":45,"max":90}）
     */
    private String deliveryPrepareTime;
    
    /**
     * 自取营业时间（JSON数组）
     */
    private String pickupOpenTime;
    
    /**
     * 配送营业时间（JSON数组）
     */
    private String deliveryOpenTime;
    
    /**
     * 默认配送费类型：FLAT_RATE/VARIABLE_RATE/ZONE_RATE
     */
    private String defaultDeliveryFee;
    
    /**
     * 配送固定费用
     */
    private BigDecimal deliveryFlatFee;
    
    /**
     * 配送可变费率（JSON数组）
     */
    private String deliveryVariableRate;
    
    /**
     * 配送区域费率（JSON数组）
     */
    private String deliveryZoneRate;
    
    /**
     * 配送最低金额
     */
    private BigDecimal deliveryMinimumAmount;
    
    /**
     * 配送最大距离（米）
     */
    private Integer deliveryMaximumDistance;
    
    /**
     * 是否激活（自取）
     */
    private Boolean activate;
    
    /**
     * 是否激活（配送）
     */
    private Boolean dlActivate;
    
    /**
     * 自取是否已设置
     */
    private Boolean pickupHaveSetted;
    
    /**
     * 配送是否已设置
     */
    private Boolean deliveryHaveSetted;
    
    /**
     * 是否开启备注
     */
    private Boolean enableNote;
    
    /**
     * 是否开启自动送厨
     */
    private Boolean enableAutoSend;
    
    /**
     * 是否开启自动打印小票
     */
    private Boolean enableAutoReceipt;
    
    /**
     * 是否开启堂食自动打印小票
     */
    private Boolean enableSdiAutoReceipt;
    
    /**
     * 是否开启堂食自动送厨
     */
    private Boolean enableSdiAutoSend;
    
    /**
     * 是否展示热门商品
     */
    private Boolean enablePopularItem;
    
    /**
     * 是否显示
     */
    private Boolean display;
    
    /**
     * 个性化配置（JSON）
     */
    private String personalization;
    
    /**
     * 接单能力配置（JSON）
     */
    private String capabilityOfOrder;
    
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

