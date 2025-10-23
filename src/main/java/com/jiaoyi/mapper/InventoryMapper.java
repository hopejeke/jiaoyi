package com.jiaoyi.mapper;

import com.jiaoyi.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存Mapper接口
 */
@Mapper
public interface InventoryMapper {
    
    /**
     * 根据商品ID查询库存
     */
    Inventory selectByProductId(@Param("productId") Long productId);
    
    /**
     * 查询库存不足的商品
     */
    List<Inventory> selectLowStockItems();
    
    /**
     * 锁定库存（原子操作）
     */
    int lockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    /**
     * 解锁库存（原子操作）
     */
    int unlockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    /**
     * 扣减库存（原子操作）
     */
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
