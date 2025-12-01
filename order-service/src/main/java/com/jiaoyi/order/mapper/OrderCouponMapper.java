package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.OrderCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单优惠券关联Mapper
 */
@Mapper
public interface OrderCouponMapper {

    /**
     * 插入订单优惠券关联记录
     */
    void insert(OrderCoupon orderCoupon);

    /**
     * 批量插入订单优惠券关联记录
     */
    void batchInsert(@Param("orderCoupons") List<OrderCoupon> orderCoupons);

    /**
     * 根据订单ID查询优惠券关联记录
     */
    List<OrderCoupon> selectByOrderId(Long orderId);

    /**
     * 根据优惠券ID查询使用记录
     */
    List<OrderCoupon> selectByCouponId(Long couponId);

    /**
     * 根据订单ID删除优惠券关联记录
     */
    void deleteByOrderId(Long orderId);

    /**
     * 根据优惠券ID删除关联记录
     */
    void deleteByCouponId(Long couponId);

    /**
     * 统计优惠券使用次数
     */
    int countByCouponId(Long couponId);
}

