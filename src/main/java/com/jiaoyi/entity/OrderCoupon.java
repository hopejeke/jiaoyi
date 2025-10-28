package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单优惠券关联实体
 * 支持一个订单使用多个优惠券
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCoupon {

    private Long id;
    private Long orderId;
    private Long couponId;
    private String couponCode;
    private BigDecimal appliedAmount;  // 该优惠券实际抵扣金额
    private LocalDateTime createTime;
}
