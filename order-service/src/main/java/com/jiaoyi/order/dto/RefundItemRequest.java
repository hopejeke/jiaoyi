package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 退款商品项请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundItemRequest {
    
    /**
     * 订单项ID
     */
    private Long orderItemId;
    
    /**
     * 退款数量
     */
    private Integer refundQuantity;
}






