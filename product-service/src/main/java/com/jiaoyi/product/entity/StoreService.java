package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 餐馆服务实体类
 * 对应 online-order-v2-backend 的 RestaurantService 模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreService {
    
    /**
     * 主键ID（雪花算法生成）
     */
    private Long id;
    
    /**
     * 餐馆ID（用于分片）
     */
    private String merchantId;
    
    /**
     * 服务类型：PICKUP/DELIVERY/SELF_DINE_IN
     */
    private String serviceType;
    
    /**
     * 支付方式（JSON数组）
     */
    private String paymentAcceptance;
    
    /**
     * 准备时间（JSON：{"min":30,"max":60}）
     */
    private String prepareTime;
    
    /**
     * 营业时间（JSON数组）
     */
    private String openTime;
    
    /**
     * 营业时间范围（JSON对象）
     */
    private String openTimeRange;
    
    /**
     * 特殊营业时间（JSON数组）
     */
    private String specialHours;
    
    /**
     * 是否临时关闭
     */
    private Boolean tempClose;
    
    /**
     * 是否已设置
     */
    private Boolean haveSet;
    
    /**
     * 是否已激活
     */
    private Boolean activate;
    
    /**
     * 激活时间（时间戳）
     */
    private Long activateDate;
    
    /**
     * 是否启用
     */
    private Boolean enableUse;
    
    /**
     * 使用时间段（JSON数组）
     */
    private String usageTimePeriod;
    
    /**
     * Togo入口二维码（Base64）
     */
    private String togoEntranceQrBase64;
    
    /**
     * Togo入口小程序二维码（Base64）
     */
    private String togoEntranceQrMiniProgramBase64;
    
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

