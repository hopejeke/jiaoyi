package com.jiaoyi.order.enums;

/**
 * 支付状态枚举
 */
public enum PaymentStatusEnum {
    /**
     * 待支付
     */
    PENDING(0, "待支付"),
    
    /**
     * 支付成功
     */
    SUCCESS(100, "支付成功"),
    
    /**
     * 支付失败
     */
    FAILED(200, "支付失败");
    
    private final Integer code;
    private final String description;
    
    PaymentStatusEnum(Integer code, String description) {
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
    public static PaymentStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}














