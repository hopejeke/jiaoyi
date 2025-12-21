package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 支付请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    /**
     * 支付方式：ALIPAY-支付宝，WECHAT-微信，BANK-银行卡
     */
    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;
    
    /**
     * 支付金额（分）
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;
    
    /**
     * 支付渠道：WEB-网页，APP-应用，H5-手机网页
     */
    private String channel = "WEB";
    
    /**
     * 用户IP地址
     */
    private String clientIp;
    
    /**
     * 用户代理
     */
    private String userAgent;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 支付信息（JSON格式，包含支付方式相关参数）
     * 例如：{"cardInfo": {"paymentMethodId": "pm_xxx", "saveCard": true}}
     */
    private String paymentInfo;
}


