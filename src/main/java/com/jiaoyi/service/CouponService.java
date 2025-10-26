package com.jiaoyi.service;

import com.jiaoyi.entity.Coupon;
import com.jiaoyi.entity.CouponUsage;
import com.jiaoyi.mapper.CouponMapper;
import com.jiaoyi.mapper.CouponUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 优惠券服务类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {
    
    private final CouponMapper couponMapper;
    private final CouponUsageMapper couponUsageMapper;
    
    /**
     * 根据优惠券代码查询优惠券
     */
    public Optional<Coupon> getCouponByCode(String couponCode) {
        log.info("查询优惠券，代码: {}", couponCode);
        Coupon coupon = couponMapper.selectByCouponCode(couponCode);
        return Optional.ofNullable(coupon);
    }
    
    /**
     * 根据ID查询优惠券
     */
    public Optional<Coupon> getCouponById(Long couponId) {
        log.info("查询优惠券，ID: {}", couponId);
        Coupon coupon = couponMapper.selectById(couponId);
        return Optional.ofNullable(coupon);
    }
    
    /**
     * 获取用户可用优惠券列表
     */
    public List<Coupon> getAvailableCouponsByUserId(Long userId, BigDecimal orderAmount) {
        log.info("查询用户可用优惠券，用户ID: {}, 订单金额: {}", userId, orderAmount);
        return couponMapper.selectAvailableCouponsByUserId(userId, orderAmount);
    }
    
    /**
     * 根据商品ID获取可用优惠券列表
     */
    public List<Coupon> getAvailableCouponsByProductIds(List<Long> productIds, BigDecimal orderAmount) {
        log.info("根据商品查询可用优惠券，商品数量: {}, 订单金额: {}", productIds.size(), orderAmount);
        return couponMapper.selectAvailableCouponsByProductIds(productIds, orderAmount);
    }
    
    /**
     * 验证优惠券是否可用
     */
    public boolean validateCoupon(Coupon coupon, Long userId, BigDecimal orderAmount, List<Long> productIds) {
        if (coupon == null) {
            log.warn("优惠券不存在");
            return false;
        }
        
        // 检查优惠券状态
        if (coupon.getStatus() != Coupon.CouponStatus.ACTIVE) {
            log.warn("优惠券状态无效，状态: {}", coupon.getStatus());
            return false;
        }
        
        // 检查时间有效性
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            log.warn("优惠券不在有效期内，当前时间: {}, 开始时间: {}, 结束时间: {}", 
                    now, coupon.getStartTime(), coupon.getEndTime());
            return false;
        }
        
        // 检查库存
        if (coupon.getUsedQuantity() >= coupon.getTotalQuantity()) {
            log.warn("优惠券已用完，已使用: {}, 总数量: {}", coupon.getUsedQuantity(), coupon.getTotalQuantity());
            return false;
        }
        
        // 检查最低消费金额
        if (coupon.getMinOrderAmount() != null && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            log.warn("订单金额不满足优惠券最低消费要求，订单金额: {}, 最低消费: {}", 
                    orderAmount, coupon.getMinOrderAmount());
            return false;
        }
        
        // 检查用户使用次数限制
        int usedCount = couponUsageMapper.countByUserIdAndCouponId(userId, coupon.getId());
        if (usedCount >= coupon.getLimitPerUser()) {
            log.warn("用户使用次数已达上限，已使用: {}, 限制: {}", usedCount, coupon.getLimitPerUser());
            return false;
        }
        
        // 检查适用商品
        if (coupon.getApplicableType() != Coupon.ApplicableType.ALL) {
            if (coupon.getApplicableType() == Coupon.ApplicableType.PRODUCT && coupon.getApplicableProducts() != null) {
                // 检查商品是否在适用范围内
                boolean applicable = productIds.stream().anyMatch(productId -> 
                    coupon.getApplicableProducts().contains(productId.toString()));
                if (!applicable) {
                    log.warn("商品不在优惠券适用范围内，商品ID: {}, 适用商品: {}", 
                            productIds, coupon.getApplicableProducts());
                    return false;
                }
            }
        }
        
        log.info("优惠券验证通过，优惠券ID: {}", coupon.getId());
        return true;
    }
    
    /**
     * 计算优惠金额
     */
    public BigDecimal calculateDiscountAmount(Coupon coupon, BigDecimal orderAmount) {
        if (coupon == null || orderAmount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal discountAmount;
        
        if (coupon.getType() == Coupon.CouponType.FIXED) {
            // 固定金额优惠
            discountAmount = coupon.getValue();
        } else if (coupon.getType() == Coupon.CouponType.PERCENTAGE) {
            // 百分比优惠
            discountAmount = orderAmount.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100));
            
            // 检查最大优惠金额限制
            if (coupon.getMaxDiscountAmount() != null && 
                discountAmount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discountAmount = coupon.getMaxDiscountAmount();
            }
        } else {
            discountAmount = BigDecimal.ZERO;
        }
        
        // 优惠金额不能超过订单金额
        if (discountAmount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
        }
        
        log.info("计算优惠金额，优惠券类型: {}, 订单金额: {}, 优惠金额: {}", 
                coupon.getType(), orderAmount, discountAmount);
        
        return discountAmount;
    }
    
    /**
     * 使用优惠券
     */
    @Transactional
    public CouponUsage useCoupon(Long couponId, Long userId, Long orderId, String couponCode, 
                                BigDecimal orderAmount, BigDecimal discountAmount) {
        log.info("使用优惠券，优惠券ID: {}, 用户ID: {}, 订单ID: {}", couponId, userId, orderId);
        
        // 更新优惠券使用数量
        couponMapper.updateUsedQuantity(couponId, 1);
        
        // 创建优惠券使用记录
        CouponUsage couponUsage = new CouponUsage();
        couponUsage.setCouponId(couponId);
        couponUsage.setUserId(userId);
        couponUsage.setOrderId(orderId);
        couponUsage.setCouponCode(couponCode);
        couponUsage.setDiscountAmount(discountAmount);
        couponUsage.setUsedTime(LocalDateTime.now());
        couponUsage.setOrderAmount(orderAmount);
        couponUsage.setActualAmount(orderAmount.subtract(discountAmount));
        couponUsage.setStatus(CouponUsage.UsageStatus.USED);
        couponUsage.setRemark("订单使用优惠券");
        couponUsage.setCreateTime(LocalDateTime.now());
        
        couponUsageMapper.insert(couponUsage);
        
        log.info("优惠券使用成功，使用记录ID: {}", couponUsage.getId());
        return couponUsage;
    }
    
    /**
     * 退款优惠券
     */
    @Transactional
    public boolean refundCoupon(Long orderId) {
        log.info("退款优惠券，订单ID: {}", orderId);
        
        CouponUsage couponUsage = couponUsageMapper.selectByOrderId(orderId);
        if (couponUsage == null) {
            log.warn("未找到优惠券使用记录，订单ID: {}", orderId);
            return false;
        }
        
        // 更新使用记录状态为已退款
        couponUsageMapper.updateStatus(couponUsage.getId(), CouponUsage.UsageStatus.REFUNDED.name());
        
        // 减少优惠券使用数量
        couponMapper.updateUsedQuantity(couponUsage.getCouponId(), -1);
        
        log.info("优惠券退款成功，使用记录ID: {}", couponUsage.getId());
        return true;
    }
    
    /**
     * 获取优惠券使用记录
     */
    public List<CouponUsage> getCouponUsageByUserId(Long userId) {
        log.info("查询用户优惠券使用记录，用户ID: {}", userId);
        return couponUsageMapper.selectByUserId(userId);
    }
    
    /**
     * 获取订单优惠券使用记录
     */
    public Optional<CouponUsage> getCouponUsageByOrderId(Long orderId) {
        log.info("查询订单优惠券使用记录，订单ID: {}", orderId);
        CouponUsage couponUsage = couponUsageMapper.selectByOrderId(orderId);
        return Optional.ofNullable(couponUsage);
    }
}
