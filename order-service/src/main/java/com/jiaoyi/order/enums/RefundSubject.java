package com.jiaoyi.order.enums;

/**
 * 退款科目枚举
 */
public enum RefundSubject {
    /**
     * 商品退款
     */
    ITEM("ITEM", "商品退款"),
    
    /**
     * 税费退款
     */
    TAX("TAX", "税费退款"),
    
    /**
     * 配送费退款
     */
    DELIVERY_FEE("DELIVERY_FEE", "配送费退款"),
    
    /**
     * 小费退款
     */
    TIPS("TIPS", "小费退款"),
    
    /**
     * 服务费退款
     */
    CHARGE("CHARGE", "服务费退款"),
    
    /**
     * 折扣退款
     */
    DISCOUNT("DISCOUNT", "折扣退款");
    
    private final String code;
    private final String description;
    
    RefundSubject(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static RefundSubject fromCode(String code) {
        for (RefundSubject subject : values()) {
            if (subject.code.equals(code)) {
                return subject;
            }
        }
        throw new IllegalArgumentException("Unknown refund subject code: " + code);
    }
}






