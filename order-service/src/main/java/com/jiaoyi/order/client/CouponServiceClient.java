package com.jiaoyi.order.client;

import com.jiaoyi.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 优惠券服务 Feign Client
 * 用于订单服务调用优惠券服务
 */
@FeignClient(name = "coupon-service", path = "/api")
public interface CouponServiceClient {
    
    /**
     * 根据优惠券ID获取优惠券
     */
    @GetMapping("/api/coupons/{couponId}")
    ApiResponse<?> getCouponById(@PathVariable("couponId") Long couponId);
    
    /**
     * 根据优惠券代码获取优惠券
     */
    @GetMapping("/api/coupons/code/{couponCode}")
    ApiResponse<?> getCouponByCode(@PathVariable("couponCode") String couponCode);
    
    /**
     * 验证优惠券
     */
    @GetMapping("/api/coupons/{couponCode}/validate")
    ApiResponse<Boolean> validateCoupon(@PathVariable("couponCode") String couponCode,
                         @RequestParam("userId") Long userId,
                         @RequestParam("orderAmount") BigDecimal orderAmount);
    
    /**
     * 计算优惠金额
     */
    @PostMapping("/api/coupons/{couponCode}/calculate")
    ApiResponse<BigDecimal> calculateDiscountAmount(@PathVariable("couponCode") String couponCode,
                                                      @RequestParam("orderAmount") BigDecimal orderAmount);
    
    /**
     * 使用优惠券
     */
    @PostMapping("/api/coupons/{couponCode}/use")
    ApiResponse<Void> useCoupon(@PathVariable("couponCode") String couponCode,
                    @RequestParam("userId") Long userId,
                    @RequestParam("orderId") Long orderId,
                    @RequestParam("discountAmount") BigDecimal discountAmount);
    
    /**
     * 退款优惠券（根据订单ID）
     * 
     * 返回 OperationResult，包含操作结果状态：
     * - SUCCESS: 第一次调用，退款成功
     * - IDEMPOTENT_SUCCESS: 重复调用，但优惠券已退款过（幂等成功）
     * - FAILED: 操作失败
     */
    @PostMapping("/api/coupons/refund/order/{orderId}")
    ApiResponse<com.jiaoyi.common.OperationResult> refundCouponByOrderId(@PathVariable("orderId") Long orderId);
    
    /**
     * 退款优惠券（根据优惠券ID）
     */
    @PostMapping("/api/coupons/refund/{couponId}")
    ApiResponse<Boolean> refundCoupon(@PathVariable("couponId") Long couponId);
}
