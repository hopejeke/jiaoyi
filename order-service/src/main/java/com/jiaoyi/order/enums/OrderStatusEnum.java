package com.jiaoyi.order.enums;

/**
 * 订单状态枚举
 */
public enum OrderStatusEnum {
    /**
     * 已下单待支付
     */
    PENDING(1, "已下单待支付"),
    
    /**
     * 已支付
     */
    PAID(2, "已支付"),
    
    /**
     * 待接单（已支付，等待商家接单）
     */
    WAITING_ACCEPT(9, "待接单"),
    
    /**
     * 制作中（商家正在制作餐品）
     */
    PREPARING(3, "制作中"),
    
    /**
     * 配送中（商家已制作完成，骑手正在配送）
     */
    DELIVERING(8, "配送中"),
    
    /**
     * 已完成
     */
    COMPLETED(4, "已完成"),
    
    /**
     * 已取消
     */
    CANCELLED(5, "已取消"),
    
    /**
     * 已退款
     */
    REFUNDED(6, "已退款"),
    
    /**
     * 支付失败
     */
    PAY_FAILED(7, "支付失败");
    
    private final Integer code;
    private final String description;
    
    OrderStatusEnum(Integer code, String description) {
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
    public static OrderStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}


