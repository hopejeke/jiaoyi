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
     * 根据ID查询订单（带行锁，用于并发控制）
     */
    Order selectByIdForUpdate(@Param("id") Long id);
    
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
     * 根据 storeId 和订单ID列表批量查询订单（用于用户订单查询优化）
     * ShardingSphere 会根据 store_id 精准路由到对应的分片
     *
     * @param storeId  商户ID（分片键，精准路由）
     * @param orderIds 订单ID列表
     * @return 订单列表
     */
    List<Order> selectByStoreIdAndOrderIds(@Param("storeId") Long storeId,
                                           @Param("orderIds") List<Long> orderIds);
    
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
     * 更新订单的配送ID
     */
    int updateDeliveryId(
            @Param("id") Long id,
            @Param("deliveryId") String deliveryId
    );
    
    /**
     * 更新订单退款金额（累加）
     */
    int updateRefundAmount(@Param("id") Long id, @Param("refundAmount") java.math.BigDecimal refundAmount);
    
    /**
     * 根据商户ID和时间范围查询订单（用于高峰拒单统计）
     */
    List<Order> selectByMerchantIdAndTimeRange(
        @Param("merchantId") String merchantId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}

