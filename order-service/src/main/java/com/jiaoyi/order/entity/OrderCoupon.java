package com.jiaoyi.order.entity;

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
    private String merchantId;
    private Long storeId;  // 门店ID（用于分片，与商品服务保持一致）
    private Long couponId;
    private String couponCode;
    private BigDecimal appliedAmount;  // 该优惠券实际抵扣金额
    private LocalDateTime createTime;
}


