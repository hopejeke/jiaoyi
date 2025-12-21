package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.Inventory;
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
     * 根据店铺ID和商品ID查询库存（商品级别，sku_id为NULL）
     */
    Inventory selectByStoreIdAndProductId(@Param("storeId") Long storeId, @Param("productId") Long productId);
    
    /**
     * 根据店铺ID、商品ID和SKU ID查询库存（SKU级别）
     */
    Inventory selectByStoreIdAndProductIdAndSkuId(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("skuId") Long skuId);
    
    /**
     * 根据商品ID查询所有SKU库存
     */
    List<Inventory> selectByProductIdWithSku(@Param("productId") Long productId);
    
    /**
     * 查询库存不足的商品
     */
    List<Inventory> selectLowStockItems();
    
    /**
     * 根据店铺ID查询库存不足的商品
     */
    List<Inventory> selectLowStockItemsByStoreId(@Param("storeId") Long storeId);
    
    /**
     * 锁定库存（原子操作，商品级别）
     */
    int lockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    /**
     * 锁定库存（原子操作，SKU级别）
     */
    int lockStockBySku(@Param("productId") Long productId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
    
    /**
     * 解锁库存（原子操作，商品级别）
     */
    int unlockStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    /**
     * 解锁库存（原子操作，SKU级别）
     */
    int unlockStockBySku(@Param("productId") Long productId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
    
    /**
     * 扣减库存（原子操作，商品级别）
     */
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    /**
     * 扣减库存（原子操作，SKU级别）
     */
    int deductStockBySku(@Param("productId") Long productId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
    
    /**
     * 查询所有库存记录
     */
    List<Inventory> selectAll();
    
    /**
     * 更新库存数量
     */
    int updateStock(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("skuId") Long skuId, 
                    @Param("currentStock") Integer currentStock, @Param("minStock") Integer minStock, @Param("maxStock") Integer maxStock);
}

