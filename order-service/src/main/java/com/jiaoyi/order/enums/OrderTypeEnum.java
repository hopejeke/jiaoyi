package com.jiaoyi.order.enums;

/**
 * 订单类型枚举
 */
public enum OrderTypeEnum {
    /**
     * 自取
     */
    PICKUP("PICKUP", "自取"),
    
    /**
     * 配送
     */
    DELIVERY("DELIVERY", "配送"),
    
    /**
     * 堂食
     */
    SELF_DINE_IN("SELF_DINE_IN", "堂食");
    
    private final String code;
    private final String description;
    
    OrderTypeEnum(String code, String description) {
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
    public static OrderTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}





