package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单Mapper接口
 */
@Mapper
public interface OrderMapper {
    
    /**
     * 插入订单
     */
    int insert(Order order);
    
    /**
     * 根据ID查询订单
     */
    Order selectById(Long id);
    
    /**
     * 根据merchantId和id查询订单（在线点餐，包含分片键）
     */
    Order selectByMerchantIdAndId(@Param("merchantId") String merchantId, @Param("id") Long id);
    
    /**
     * 查询所有订单
     */
    List<Order> selectAll();
    
    /**
     * 根据用户ID查询订单列表
     */
    List<Order> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据merchantId查询所有订单
     */
    List<Order> selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 根据merchantId和status查询订单
     */
    List<Order> selectByMerchantIdAndStatus(@Param("merchantId") String merchantId, @Param("status") Integer status);
    
    /**
     * 根据用户ID和状态查询订单列表（在线点餐：status 是 Integer）
     */
    List<Order> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);
    
    /**
     * 根据状态查询订单列表（在线点餐：status 是 Integer）
     */
    List<Order> selectByStatus(@Param("status") Integer status);
    
    /**
     * 更新订单（使用乐观锁）
     */
    int update(Order order);
    
    /**
     * 更新订单状态（在线点餐：status 是 Integer）
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    
    /**
     * 原子更新订单状态：只有当状态为 oldStatus 时才更新为新状态（在线点餐）
     * @param id 订单ID
     * @param oldStatus 旧状态（必须是这个状态才能更新）
     * @param newStatus 新状态
     * @return 更新的行数
     */
    int updateStatusIfPending(@Param("id") Long id, @Param("oldStatus") Integer oldStatus, @Param("newStatus") Integer newStatus);
    
    /**
     * 删除订单（逻辑删除，在线点餐）
     */
    int deleteById(Order order);
    
    /**
     * 统计用户订单数量
     */
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 统计指定状态的订单数量（在线点餐：status 是 Integer）
     */
    long countByStatus(@Param("status") Integer status);
    
    /**
     * 查询超时订单（指定时间之前创建的待支付订单，status = 1）
     */
    List<Order> selectTimeoutOrders(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);
    
    /**
     * 更新订单的配送信息（deliveryId 和 additionalData）
     */
    int updateDeliveryInfo(
            @Param("id") Long id,
            @Param("deliveryId") String deliveryId,
            @Param("additionalData") String additionalData
    );
    
    /**
     * 更新订单的配送费信息
     */
    int updateDeliveryFeeInfo(
            @Param("id") Long id,
            @Param("deliveryFeeQuoted") java.math.BigDecimal deliveryFeeQuoted,
            @Param("deliveryFeeChargedToUser") java.math.BigDecimal deliveryFeeChargedToUser,
            @Param("deliveryFeeBilled") java.math.BigDecimal deliveryFeeBilled,
            @Param("deliveryFeeVariance") String deliveryFeeVariance
    );
    
    /**
     * 更新订单的报价信息（报价时间、报价ID）
     */
    int updateQuoteInfo(
            @Param("id") Long id,
            @Param("deliveryFeeQuoted") java.math.BigDecimal deliveryFeeQuoted,
            @Param("deliveryFeeQuotedAt") LocalDateTime deliveryFeeQuotedAt,
            @Param("deliveryFeeQuoteId") String deliveryFeeQuoteId
    );
}

