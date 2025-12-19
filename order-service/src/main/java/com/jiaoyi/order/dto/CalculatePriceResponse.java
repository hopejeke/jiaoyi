package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 计算订单价格响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatePriceResponse {

    /**
     * 订单小计（商品总价）
     */
    private BigDecimal subtotal;

    /**
     * 配送费（用户实际支付的费用，可能包含 buffer）
     */
    private BigDecimal deliveryFee;
    
    /**
     * DoorDash 报价费用（quoted_fee，如果有）
     */
    private BigDecimal deliveryFeeQuoted;

    /**
     * 税费
     */
    private BigDecimal taxTotal;

    /**
     * 在线服务费
     */
    private BigDecimal charge;

    /**
     * 优惠金额
     */
    private BigDecimal discount;

    /**
     * 小费
     */
    private BigDecimal tips;

    /**
     * 总金额
     */
    private BigDecimal total;

    /**
     * 价格明细说明
     */
    private String priceDetail;
}

