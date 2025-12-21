package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.Delivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送 Mapper
 */
@Mapper
public interface DeliveryMapper {
    
    /**
     * 插入配送记录
     */
    int insert(Delivery delivery);
    
    /**
     * 根据配送ID查询
     */
    Delivery selectById(@Param("id") String id);
    
    /**
     * 根据订单ID查询
     */
    Delivery selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据外部订单ID查询
     */
    Delivery selectByExternalDeliveryId(@Param("externalDeliveryId") String externalDeliveryId);
    
    /**
     * 更新配送信息
     */
    int update(Delivery delivery);
    
    /**
     * 更新配送状态
     */
    int updateStatus(@Param("id") String id, 
                     @Param("status") com.jiaoyi.order.enums.DeliveryStatusEnum status);
    
    /**
     * 更新配送跟踪信息
     */
    int updateTrackingInfo(@Param("id") String id,
                           @Param("trackingUrl") String trackingUrl,
                           @Param("distanceMiles") BigDecimal distanceMiles,
                           @Param("etaMinutes") Integer etaMinutes,
                           @Param("driverName") String driverName,
                           @Param("driverPhone") String driverPhone);
    
    /**
     * 更新配送费信息
     */
    int updateDeliveryFeeInfo(@Param("id") String id,
                               @Param("deliveryFeeQuoted") java.math.BigDecimal deliveryFeeQuoted,
                               @Param("deliveryFeeChargedToUser") java.math.BigDecimal deliveryFeeChargedToUser,
                               @Param("deliveryFeeBilled") java.math.BigDecimal deliveryFeeBilled,
                               @Param("deliveryFeeVariance") String deliveryFeeVariance);
    
    /**
     * 更新报价信息
     */
    int updateQuoteInfo(@Param("id") String id,
                        @Param("deliveryFeeQuoted") java.math.BigDecimal deliveryFeeQuoted,
                        @Param("deliveryFeeQuotedAt") java.time.LocalDateTime deliveryFeeQuotedAt,
                        @Param("deliveryFeeQuoteId") String deliveryFeeQuoteId);
}


