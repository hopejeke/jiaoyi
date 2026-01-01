package com.jiaoyi.order.enums;

/**
 * 支付服务枚举
 */
public enum PaymentServiceEnum {
    /**
     * Stripe（信用卡）
     */
    STRIPE("STRIPE", "Stripe"),
    
    /**
     * 支付宝
     */
    ALIPAY("ALIPAY", "支付宝"),
    
    /**
     * 微信支付
     */
    WECHAT_PAY("WECHAT_PAY", "微信支付"),
    
    /**
     * 现金
     */
    CASH("CASH", "现金");
    
    private final String code;
    private final String description;
    
    PaymentServiceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static PaymentServiceEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PaymentServiceEnum service : values()) {
            if (service.code.equalsIgnoreCase(code)) {
                return service;
            }
        }
        return null;
    }
}











