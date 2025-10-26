package com.jiaoyi.mapper;

import com.jiaoyi.entity.CouponUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券使用记录Mapper接口
 */
@Mapper
public interface CouponUsageMapper {
    
    /**
     * 插入优惠券使用记录
     */
    int insert(CouponUsage couponUsage);
    
    /**
     * 根据ID查询优惠券使用记录
     */
    CouponUsage selectById(Long id);
    
    /**
     * 根据订单ID查询优惠券使用记录
     */
    CouponUsage selectByOrderId(Long orderId);
    
    /**
     * 根据用户ID查询优惠券使用记录
     */
    List<CouponUsage> selectByUserId(Long userId);
    
    /**
     * 根据优惠券ID查询使用记录
     */
    List<CouponUsage> selectByCouponId(Long couponId);
    
    /**
     * 更新优惠券使用状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    /**
     * 根据用户ID和优惠券ID查询使用次数
     */
    int countByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);
}
