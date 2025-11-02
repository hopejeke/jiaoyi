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
     * 插入库存记录
     */
    void insert(Inventory inventory);
    
    /**
     * 根据商品ID查询库存（兼容旧接口，建议使用 selectByStoreIdAndProductId）
     */
    Inventory selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据店铺ID和商品ID查询库存
     */
    Inventory selectByStoreIdAndProductId(@Param("storeId") Long storeId, @Param("productId") Long productId);
    
    /**
     * 查询库存不足的商品
     */
    List<Inventory> selectLowStockItems();
    
    /**
     * 根据店铺ID查询库存不足的商品
     */
    List<Inventory> selectLowStockItemsByStoreId(@Param("storeId") Long storeId);
    
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
