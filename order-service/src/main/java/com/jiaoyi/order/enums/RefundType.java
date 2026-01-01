package com.jiaoyi.order.enums;

/**
 * 退款类型枚举
 */
public enum RefundType {
    /**
     * 按商品退款（指定要退的商品和数量）
     */
    BY_ITEMS("BY_ITEMS", "按商品退款"),
    
    /**
     * 按金额退款（指定退款金额，自动分配到商品）
     */
    BY_AMOUNT("BY_AMOUNT", "按金额退款");
    
    private final String code;
    private final String description;
    
    RefundType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static RefundType fromCode(String code) {
        for (RefundType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown refund type code: " + code);
    }
}




