package com.jiaoyi.mapper;

import com.jiaoyi.entity.Order;
import com.jiaoyi.entity.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
