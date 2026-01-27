package com.jiaoyi.order.enums;

/**
 * DoorDash Webhook 日志状态枚举
 */
public enum DoorDashWebhookLogStatusEnum {
    /**
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中"),
    
    /**
     * 处理成功
     */
    SUCCESS("SUCCESS", "处理成功"),
    
    /**
     * 处理失败
     */
    FAILED("FAILED", "处理失败");
    
    private final String code;
    private final String description;
    
    DoorDashWebhookLogStatusEnum(String code, String description) {
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
    public static DoorDashWebhookLogStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DoorDashWebhookLogStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}















