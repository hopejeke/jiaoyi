package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单项Mapper接口
 */
@Mapper
public interface OrderItemMapper {
    
    /**
     * 插入订单项
     */
    int insert(OrderItem orderItem);
    
    /**
     * 批量插入订单项
     */
    int insertBatch(@Param("orderItems") List<OrderItem> orderItems);
    
    /**
     * 根据订单ID查询订单项列表
     */
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据商品ID查询订单项列表
     */
    List<OrderItem> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 统计商品销售数量
     */
    Long sumQuantityByProductId(@Param("productId") Long productId);
    
    /**
     * 根据订单号查询订单项列表
     */
    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);
}

