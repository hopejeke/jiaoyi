package com.jiaoyi.order.enums;

/**
 * 支付类型枚举
 */
public enum PaymentTypeEnum {
    /**
     * 扣款
     */
    CHARGE(100, "扣款"),
    
    /**
     * 退款
     */
    REFUND(200, "退款");
    
    private final Integer code;
    private final String description;
    
    PaymentTypeEnum(Integer code, String description) {
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
    public static PaymentTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}






