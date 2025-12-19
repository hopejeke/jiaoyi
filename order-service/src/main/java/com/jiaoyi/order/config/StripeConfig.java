package com.jiaoyi.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe 支付配置
 * 参照 OO 项目的 Stripe 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {
    
    /**
     * Stripe Secret Key
     */
    private String secretKey;
    
    /**
     * Stripe Publishable Key（前端使用）
     */
    private String publishableKey;
    
    /**
     * Webhook 签名密钥
     */
    private String webhookSecret;
    
    /**
     * 是否启用 Stripe 支付
     */
    private Boolean enabled = false;
    
    /**
     * Stripe Connect 配置
     */
    private Connect connect = new Connect();
    
    @Data
    public static class Connect {
        /**
         * 平台手续费率（百分比，如 2.5 表示 2.5%）
         */
        private Double applicationFeePercentage = 2.5;
        
        /**
         * 平台固定手续费（元，如 0.30 表示 $0.30）
         */
        private Double applicationFeeFixed = 0.30;
        
        /**
         * 美国运通手续费率（百分比，如 3.5 表示 3.5%）
         */
        private Double amexApplicationFeePercentage = 3.5;
        
        /**
         * 美国运通固定手续费（元）
         */
        private Double amexApplicationFeeFixed = 0.30;
    }
}

