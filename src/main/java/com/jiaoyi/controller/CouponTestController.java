package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.Coupon;
import com.jiaoyi.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 优惠券测试控制器
 */
@RestController
@RequestMapping("/api/coupon-test")
@RequiredArgsConstructor
@Slf4j
public class CouponTestController {
    
    private final CouponService couponService;
    
    /**
     * 查询优惠券详情
     */
    @GetMapping("/detail/{couponCode}")
    public ApiResponse<Coupon> getCouponDetail(@PathVariable String couponCode) {
        log.info("查询优惠券详情，代码: {}", couponCode);
        
        Optional<Coupon> coupon = couponService.getCouponByCode(couponCode);
        if (coupon.isPresent()) {
            return ApiResponse.success("查询成功", coupon.get());
        } else {
            return ApiResponse.error("优惠券不存在");
        }
    }
    
    /**
     * 测试优惠券验证
     */
    @PostMapping("/validate")
    public ApiResponse<Object> testValidateCoupon(
            @RequestParam String couponCode,
            @RequestParam Long userId,
            @RequestParam BigDecimal orderAmount,
            @RequestBody List<Long> productIds) {
        
        log.info("测试优惠券验证，代码: {}, 用户ID: {}, 订单金额: {}, 商品ID: {}", 
                couponCode, userId, orderAmount, productIds);
        
        Optional<Coupon> couponOpt = couponService.getCouponByCode(couponCode);
        if (!couponOpt.isPresent()) {
            return ApiResponse.error("优惠券不存在");
        }
        
        Coupon coupon = couponOpt.get();
        boolean isValid = couponService.validateCoupon(coupon, userId, orderAmount, productIds);
        BigDecimal discountAmount = couponService.calculateDiscountAmount(coupon, orderAmount);
        
        return ApiResponse.success("验证完成", new Object() {
            public Coupon coupon = coupon;
            public boolean isValid = isValid;
            public BigDecimal discountAmount = discountAmount;
            public BigDecimal actualAmount = orderAmount.subtract(discountAmount);
        });
    }
    
    /**
     * 测试计算优惠金额
     */
    @PostMapping("/calculate")
    public ApiResponse<Object> testCalculateDiscount(
            @RequestParam String couponCode,
            @RequestParam BigDecimal orderAmount) {
        
        log.info("测试计算优惠金额，代码: {}, 订单金额: {}", couponCode, orderAmount);
        
        Optional<Coupon> couponOpt = couponService.getCouponByCode(couponCode);
        if (!couponOpt.isPresent()) {
            return ApiResponse.error("优惠券不存在");
        }
        
        Coupon coupon = couponOpt.get();
        BigDecimal discountAmount = couponService.calculateDiscountAmount(coupon, orderAmount);
        
        return ApiResponse.success("计算完成", new Object() {
            public Coupon coupon = coupon;
            public BigDecimal orderAmount = orderAmount;
            public BigDecimal discountAmount = discountAmount;
            public BigDecimal actualAmount = orderAmount.subtract(discountAmount);
        });
    }
}
