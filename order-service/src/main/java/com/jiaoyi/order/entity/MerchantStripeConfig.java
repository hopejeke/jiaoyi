package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户 Stripe 配置实体
 * 存储每个商户的 Stripe Connected Account 信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantStripeConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 商户ID（用于分片）
     */
    private String merchantId;
    
    /**
     * Stripe Connected Account ID（acct_xxx）
     */
    private String stripeAccountId;
    
    /**
     * 是否启用 Stripe Connect
     */
    private Boolean enabled;
    
    /**
     * 货币代码（USD, CAD 等）
     */
    private String currency;
    
    /**
     * 平台手续费率（百分比，如 2.5 表示 2.5%）
     */
    private Double applicationFeePercentage;
    
    /**
     * 平台固定手续费（元，如 0.30 表示 $0.30）
     */
    private Double applicationFeeFixed;
    
    /**
     * 美国运通手续费率（百分比，如 3.5 表示 3.5%）
     */
    private Double amexApplicationFeePercentage;
    
    /**
     * 美国运通固定手续费（元）
     */
    private Double amexApplicationFeeFixed;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}






