package com.jiaoyi.order.enums;

/**
 * DoorDash 配送状态枚举
 */
public enum DeliveryStatusEnum {
    /**
     * 已创建
     */
    CREATED("CREATED", "已创建"),
    
    /**
     * 已分配骑手
     */
    ASSIGNED("ASSIGNED", "已分配骑手"),
    
    /**
     * 已取货
     */
    PICKED_UP("PICKED_UP", "已取货"),
    
    /**
     * 已送达
     */
    DELIVERED("DELIVERED", "已送达"),
    
    /**
     * 已取消
     */
    CANCELLED("CANCELLED", "已取消"),
    
    /**
     * 失败
     */
    FAILED("FAILED", "失败");
    
    private final String code;
    private final String description;
    
    DeliveryStatusEnum(String code, String description) {
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
    public static DeliveryStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeliveryStatusEnum status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}





