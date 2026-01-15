package com.jiaoyi.order.enums;

/**
 * 在线服务费类型枚举
 */
public enum OnlineServiceFeeTypeEnum {
    /**
     * 无服务费
     */
    NONE("NONE", "无服务费"),
    
    /**
     * 固定金额
     */
    FIXED("FIXED", "固定金额"),
    
    /**
     * 百分比
     */
    PERCENTAGE("PERCENTAGE", "百分比"),
    
    /**
     * 阶梯费率
     */
    TIERED("TIERED", "阶梯费率");
    
    private final String code;
    private final String description;
    
    OnlineServiceFeeTypeEnum(String code, String description) {
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
    public static OnlineServiceFeeTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OnlineServiceFeeTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}













