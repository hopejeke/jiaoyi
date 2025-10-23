package com.jiaoyi.mapper;

import com.jiaoyi.entity.InventoryTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存变动记录Mapper接口
 */
@Mapper
public interface InventoryTransactionMapper {
    
    /**
     * 插入库存变动记录
     */
    int insert(InventoryTransaction transaction);
    
    /**
     * 根据商品ID查询变动记录
     */
    List<InventoryTransaction> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据订单ID查询变动记录
     */
    List<InventoryTransaction> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 统计商品总入库数量
     */
    Long sumInQuantityByProductId(@Param("productId") Long productId);
    
    /**
     * 统计商品总出库数量
     */
    Long sumOutQuantityByProductId(@Param("productId") Long productId);
}
