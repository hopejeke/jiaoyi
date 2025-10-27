package com.jiaoyi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单超时消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单创建时间
     */
    private String createTime;
    
    /**
     * 超时时间（毫秒）
     */
    private Long timeoutMillis;
    
    /**
     * 消息创建时间
     */
    private Long messageCreateTime;
    
    public OrderTimeoutMessage(Long orderId, String orderNo, Long userId) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.messageCreateTime = System.currentTimeMillis();
    }
}
