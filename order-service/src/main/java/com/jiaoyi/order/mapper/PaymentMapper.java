package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 支付记录 Mapper
 */
@Mapper
public interface PaymentMapper {
    
    /**
     * 插入支付记录
     */
    int insert(Payment payment);
    
    /**
     * 根据ID查询支付记录
     * 注意：由于 payments 表是分片表（基于 merchant_id），selectById 没有分片键可能查询不到
     * 建议使用 selectByMerchantIdAndId
     */
    Payment selectById(@Param("id") Long id);
    
    /**
     * 根据商户ID和支付ID查询支付记录（推荐，包含分片键）
     */
    Payment selectByMerchantIdAndId(@Param("merchantId") String merchantId, @Param("id") Long id);
    
    /**
     * 根据订单ID查询支付记录
     */
    List<Payment> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据第三方交易号查询支付记录
     */
    Payment selectByThirdPartyTradeNo(@Param("thirdPartyTradeNo") String thirdPartyTradeNo);
    
    /**
     * 根据 Payment Intent ID 查询支付记录
     */
    Payment selectByPaymentIntentId(@Param("paymentIntentId") String paymentIntentId);
    
    /**
     * 更新支付状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("thirdPartyTradeNo") String thirdPartyTradeNo);
    
    /**
     * 更新支付状态（原子操作，只有状态为 PENDING 时才能更新）
     */
    int updateStatusIfPending(@Param("id") Long id, @Param("oldStatus") Integer oldStatus, @Param("newStatus") Integer newStatus, @Param("thirdPartyTradeNo") String thirdPartyTradeNo);
    
    /**
     * 更新 Payment Intent ID
     */
    int updatePaymentIntentId(@Param("id") Long id, @Param("paymentIntentId") String paymentIntentId);
}








