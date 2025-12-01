package com.jiaoyi.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    
    /**
     * 应用ID
     */
    private String appId;
    
    /**
     * 商户私钥
     */
    private String privateKey;



    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;
    
    /**
     * 网关地址（沙盒环境）
     */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    
    /**
     * 签名算法
     */
    private String signType = "RSA2";
    
    /**
     * 字符编码
     */
    private String charset = "UTF-8";
    
    /**
     * 数据格式
     */
    private String format = "JSON";
    
    /**
     * 回调地址
     */
    private String notifyUrl;
    
    /**
     * 返回地址
     */
    private String returnUrl;
}


