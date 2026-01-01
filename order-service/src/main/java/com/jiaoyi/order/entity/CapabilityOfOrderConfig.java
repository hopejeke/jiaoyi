package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 高峰拒单配置
 * 存储限流参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityOfOrderConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 是否启用限流
     */
    private Boolean enable;
    
    /**
     * 订单数量阈值（触发限流的订单数）
     * 例如：10 表示在时间窗口内达到10个订单就触发限流
     */
    private Integer qtyOfOrders;
    
    /**
     * 时间窗口（分钟）
     * 例如：10 表示统计最近10分钟的订单
     */
    private Integer timeInterval;
    
    /**
     * 关闭持续时间（分钟）
     * 例如：30 表示限流后关闭30分钟
     */
    private Integer closingDuration;
}

