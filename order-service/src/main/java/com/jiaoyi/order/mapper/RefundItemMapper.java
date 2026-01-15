package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.RefundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 退款明细 Mapper
 */
@Mapper
public interface RefundItemMapper {
    
    /**
     * 插入退款明细
     */
    int insert(RefundItem refundItem);
    
    /**
     * 批量插入退款明细
     */
    int insertBatch(@Param("refundItems") List<RefundItem> refundItems);
    
    /**
     * 根据退款单ID查询退款明细列表
     */
    List<RefundItem> selectByRefundId(@Param("refundId") Long refundId);
    
    /**
     * 根据订单项ID查询退款明细列表（用于统计该订单项已退款数量）
     */
    List<RefundItem> selectByOrderItemId(@Param("orderItemId") Long orderItemId);
}






