package com.jiaoyi.order.enums;

/**
 * DoorDash Webhook 事件类型枚举
 */
public enum DoorDashEventTypeEnum {
    /**
     * 配送已创建
     */
    DELIVERY_CREATED("delivery.created", "配送已创建"),
    
    /**
     * 已分配骑手
     */
    DELIVERY_ASSIGNED("delivery.assigned", "已分配骑手"),
    
    /**
     * 已取货
     */
    DELIVERY_PICKED_UP("delivery.picked_up", "已取货"),
    
    /**
     * 已送达
     */
    DELIVERY_DELIVERED("delivery.delivered", "已送达"),
    
    /**
     * 已取消
     */
    DELIVERY_CANCELLED("delivery.cancelled", "已取消"),
    
    /**
     * 失败
     */
    DELIVERY_FAILED("delivery.failed", "失败");
    
    private final String code;
    private final String description;
    
    DoorDashEventTypeEnum(String code, String description) {
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
    public static DoorDashEventTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DoorDashEventTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}





