package com.jiaoyi.order.enums;

/**
 * 支付方式类别枚举
 */
public enum PaymentCategoryEnum {
    /**
     * 信用卡
     */
    CREDIT_CARD(1, "信用卡"),
    
    /**
     * 现金
     */
    CASH(7, "现金"),
    
    /**
     * 微信支付
     */
    WECHAT_PAY(8, "微信支付"),
    
    /**
     * 支付宝
     */
    ALIPAY(9, "支付宝");
    
    private final Integer code;
    private final String description;
    
    PaymentCategoryEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static PaymentCategoryEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentCategoryEnum category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}














