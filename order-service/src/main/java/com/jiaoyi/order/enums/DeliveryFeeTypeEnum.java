package com.jiaoyi.order.enums;

/**
 * 配送费类型枚举
 */
public enum DeliveryFeeTypeEnum {
    /**
     * 固定费率
     */
    FLAT_RATE("FLAT_RATE", "固定费率"),
    
    /**
     * 按距离可变费率
     */
    VARIABLE_RATE("VARIABLE_RATE", "按距离可变费率"),
    
    /**
     * 按邮编区域费率
     */
    ZONE_RATE("ZONE_RATE", "按邮编区域费率");
    
    private final String code;
    private final String description;
    
    DeliveryFeeTypeEnum(String code, String description) {
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
    public static DeliveryFeeTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeliveryFeeTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}











