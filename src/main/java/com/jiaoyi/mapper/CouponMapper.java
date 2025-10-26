package com.jiaoyi.mapper;

import com.jiaoyi.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券Mapper接口
 */
@Mapper
public interface CouponMapper {
    
    /**
     * 插入优惠券
     */
    int insert(Coupon coupon);
    
    /**
     * 根据ID查询优惠券
     */
    Coupon selectById(Long id);
    
    /**
     * 根据优惠券代码查询优惠券
     */
    Coupon selectByCouponCode(String couponCode);
    
    /**
     * 查询所有有效优惠券
     */
    List<Coupon> selectActiveCoupons();
    
    /**
     * 根据用户ID查询可用优惠券
     */
    List<Coupon> selectAvailableCouponsByUserId(@Param("userId") Long userId, @Param("orderAmount") java.math.BigDecimal orderAmount);
    
    /**
     * 根据商品ID查询可用优惠券
     */
    List<Coupon> selectAvailableCouponsByProductIds(@Param("productIds") List<Long> productIds, @Param("orderAmount") java.math.BigDecimal orderAmount);
    
    /**
     * 更新优惠券使用数量
     */
    int updateUsedQuantity(@Param("id") Long id, @Param("increment") int increment);
    
    /**
     * 更新优惠券状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    /**
     * 查询即将过期的优惠券
     */
    List<Coupon> selectExpiringCoupons(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 批量更新过期优惠券状态
     */
    int updateExpiredCoupons(@Param("currentTime") LocalDateTime currentTime);
}
