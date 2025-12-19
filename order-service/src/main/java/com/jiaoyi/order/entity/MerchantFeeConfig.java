package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户费用配置实体
 * 存储每个商户的配送费、税费等费用配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFeeConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 商户ID（用于分片）
     */
    private String merchantId;
    
    /**
     * 配送费类型：FLAT_RATE-固定费率，VARIABLE_RATE-按距离可变费率，ZONE_RATE-按邮编区域费率
     */
    private String deliveryFeeType;
    
    /**
     * 配送费固定金额（元，FLAT_RATE 时使用）
     */
    private BigDecimal deliveryFeeFixed;
    
    /**
     * 配送费百分比（如 5.0 表示 5%，已废弃，保留兼容）
     */
    private BigDecimal deliveryFeePercentage;
    
    /**
     * 配送费最低金额（元）
     */
    private BigDecimal deliveryFeeMin;
    
    /**
     * 配送费最高金额（元）
     */
    private BigDecimal deliveryFeeMax;
    
    /**
     * 免配送费门槛（订单金额达到此金额免配送费，元）
     */
    private BigDecimal deliveryFeeFreeThreshold;
    
    /**
     * 按距离的可变费率（JSON格式，VARIABLE_RATE 时使用）
     * 例如：[{"from": 0, "to": 5, "price": 5.0}, {"from": 5, "to": 10, "price": 8.0}]
     * from/to 单位为英里（mile）
     */
    private String deliveryVariableRate;
    
    /**
     * 按邮编区域的费率（JSON格式，ZONE_RATE 时使用）
     * 例如：[{"zipcodes": ["10001", "10002"], "price": 5.0}, {"zipcodes": ["10003"], "price": 8.0}]
     */
    private String deliveryZoneRate;
    
    /**
     * 最大配送距离（英里，mile）
     */
    private BigDecimal deliveryMaximumDistance;
    
    /**
     * 商户纬度（用于计算配送距离）
     */
    private BigDecimal merchantLatitude;
    
    /**
     * 商户经度（用于计算配送距离）
     */
    private BigDecimal merchantLongitude;
    
    /**
     * 配送时段配置（JSON格式）
     * 例如：{"monday": {"start": "09:00", "end": "22:00"}, "tuesday": {"start": "09:00", "end": "22:00"}, ...}
     * 或者：{"daily": {"start": "09:00", "end": "22:00"}} 表示每天相同时段
     * 如果为空，表示全天可配送
     */
    private String deliveryTimeSlots;
    
    /**
     * 税率（百分比，如 8.0 表示 8%）
     */
    private BigDecimal taxRate;
    
    /**
     * 是否免税
     */
    private Boolean taxExempt;
    
    /**
     * 在线服务费类型：FIXED-固定费用，PERCENTAGE-百分比，NONE-无
     */
    private String onlineServiceFeeType;
    
    /**
     * 在线服务费固定金额（元）
     */
    private BigDecimal onlineServiceFeeFixed;
    
    /**
     * 在线服务费百分比（如 2.5 表示 2.5%）
     */
    private BigDecimal onlineServiceFeePercentage;
    
    /**
     * 在线服务费策略（JSON格式，存储阶梯费率配置）
     * 例如：[{"from": 0, "to": 50, "type": "PERCENTAGE", "fee": 5.0}, {"from": 50, "to": 100, "type": "FLAT_FEE", "fee": 2.5}]
     */
    private String onlineServiceFeeStrategy;
    
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

