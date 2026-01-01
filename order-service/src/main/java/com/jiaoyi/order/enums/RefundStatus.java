package com.jiaoyi.order.enums;

/**
 * 退款状态枚举
 */
public enum RefundStatus {
    /**
     * 已创建
     */
    CREATED("CREATED", "已创建"),
    
    /**
     * 处理中（已发起到支付渠道）
     */
    PROCESSING("PROCESSING", "处理中"),
    
    /**
     * 成功
     */
    SUCCEEDED("SUCCEEDED", "退款成功"),
    
    /**
     * 失败
     */
    FAILED("FAILED", "退款失败"),
    
    /**
     * 已取消（人工撤销未发起的）
     */
    CANCELED("CANCELED", "已取消");
    
    private final String code;
    private final String description;
    
    RefundStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static RefundStatus fromCode(String code) {
        for (RefundStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown refund status code: " + code);
    }
}




