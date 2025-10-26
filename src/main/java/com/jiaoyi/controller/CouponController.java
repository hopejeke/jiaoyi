package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.Coupon;
import com.jiaoyi.entity.CouponUsage;
import com.jiaoyi.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 优惠券控制器
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Slf4j
public class CouponController {
    
    private final CouponService couponService;
    
    /**
     * 根据优惠券代码查询优惠券
     */
    @GetMapping("/code/{couponCode}")
    public ResponseEntity<ApiResponse<Coupon>> getCouponByCode(@PathVariable String couponCode) {
        log.info("查询优惠券，代码: {}", couponCode);
        Optional<Coupon> coupon = couponService.getCouponByCode(couponCode);
        if (coupon.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", coupon.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "优惠券不存在"));
        }
    }
    
    /**
     * 根据ID查询优惠券
     */
    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<Coupon>> getCouponById(@PathVariable Long couponId) {
        log.info("查询优惠券，ID: {}", couponId);
        Optional<Coupon> coupon = couponService.getCouponById(couponId);
        if (coupon.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", coupon.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "优惠券不存在"));
        }
    }
    
    /**
     * 获取用户可用优惠券列表
     */
    @GetMapping("/available/user/{userId}")
    public ResponseEntity<ApiResponse<List<Coupon>>> getAvailableCouponsByUserId(
            @PathVariable Long userId,
            @RequestParam BigDecimal orderAmount) {
        log.info("查询用户可用优惠券，用户ID: {}, 订单金额: {}", userId, orderAmount);
        List<Coupon> coupons = couponService.getAvailableCouponsByUserId(userId, orderAmount);
        return ResponseEntity.ok(ApiResponse.success("查询成功", coupons));
    }
    
    /**
     * 根据商品ID获取可用优惠券列表
     */
    @PostMapping("/available/products")
    public ResponseEntity<ApiResponse<List<Coupon>>> getAvailableCouponsByProductIds(
            @RequestBody List<Long> productIds,
            @RequestParam BigDecimal orderAmount) {
        log.info("根据商品查询可用优惠券，商品数量: {}, 订单金额: {}", productIds.size(), orderAmount);
        List<Coupon> coupons = couponService.getAvailableCouponsByProductIds(productIds, orderAmount);
        return ResponseEntity.ok(ApiResponse.success("查询成功", coupons));
    }
    
    /**
     * 验证优惠券
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateCoupon(
            @RequestParam String couponCode,
            @RequestParam Long userId,
            @RequestParam BigDecimal orderAmount,
            @RequestBody List<Long> productIds) {
        log.info("验证优惠券，代码: {}, 用户ID: {}, 订单金额: {}", couponCode, userId, orderAmount);
        
        Optional<Coupon> couponOpt = couponService.getCouponByCode(couponCode);
        if (!couponOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.error("优惠券不存在"));
        }
        
        boolean isValid = couponService.validateCoupon(couponOpt.get(), userId, orderAmount, productIds);
        return ResponseEntity.ok(ApiResponse.success("验证完成", isValid));
    }
    
    /**
     * 计算优惠金额
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<BigDecimal>> calculateDiscountAmount(
            @RequestParam String couponCode,
            @RequestParam BigDecimal orderAmount) {
        log.info("计算优惠金额，代码: {}, 订单金额: {}", couponCode, orderAmount);
        
        Optional<Coupon> couponOpt = couponService.getCouponByCode(couponCode);
        if (!couponOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.error("优惠券不存在"));
        }
        
        BigDecimal discountAmount = couponService.calculateDiscountAmount(couponOpt.get(), orderAmount);
        return ResponseEntity.ok(ApiResponse.success("计算完成", discountAmount));
    }
    
    /**
     * 获取用户优惠券使用记录
     */
    @GetMapping("/usage/user/{userId}")
    public ResponseEntity<ApiResponse<List<CouponUsage>>> getCouponUsageByUserId(@PathVariable Long userId) {
        log.info("查询用户优惠券使用记录，用户ID: {}", userId);
        List<CouponUsage> usageList = couponService.getCouponUsageByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", usageList));
    }
    
    /**
     * 获取订单优惠券使用记录
     */
    @GetMapping("/usage/order/{orderId}")
    public ResponseEntity<ApiResponse<CouponUsage>> getCouponUsageByOrderId(@PathVariable Long orderId) {
        log.info("查询订单优惠券使用记录，订单ID: {}", orderId);
        Optional<CouponUsage> usage = couponService.getCouponUsageByOrderId(orderId);
        if (usage.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", usage.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "未找到使用记录"));
        }
    }
}
