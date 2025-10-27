package com.jiaoyi.mapper;

import com.jiaoyi.entity.Order;
import com.jiaoyi.entity.OrderStatus;
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
     * 查询所有订单
     */
    List<Order> selectAll();
    
    /**
     * 根据订单号查询订单
     */
    Order selectByOrderNo(String orderNo);
    
    /**
     * 根据用户ID查询订单列表
     */
    List<Order> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID和状态查询订单列表
     */
    List<Order> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") OrderStatus status);
    
    /**
     * 根据状态查询订单列表
     */
    List<Order> selectByStatus(@Param("status") OrderStatus status);
    
    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);
    
    /**
     * 原子更新订单状态：只有当状态为PENDING时才更新为新状态
     * @param id 订单ID
     * @param newStatus 新状态
     * @return 更新的行数
     */
    int updateStatusIfPending(@Param("id") Long id, @Param("newStatus") OrderStatus newStatus);
    
    /**
     * 统计用户订单数量
     */
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 统计指定状态的订单数量
     */
    long countByStatus(@Param("status") OrderStatus status);

    /**
     * 根据支付流水号更新订单状态
     */
    int updateStatusByPaymentNo(@Param("paymentNo") String paymentNo, @Param("status") OrderStatus status);
    
    /**
     * 原子更新订单状态为已支付：只有当状态为PENDING时才更新为PAID
     * @param paymentNo 支付流水号（订单号）
     * @return 更新的行数
     */
    int updateStatusToPaidIfPending(@Param("paymentNo") String paymentNo);
    
    /**
     * 查询超时订单（指定时间之前创建的待支付订单）
     */
    List<Order> selectTimeoutOrders(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);
}
