package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
     * 同时扣减 current_stock 和 locked_stock，用于 OO 流程（先锁后扣）
     */
    int deductStockBySku(@Param("productId") Long productId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 直接扣减库存（仅扣 current_stock，不扣 locked_stock）
     * 用于 POS 等无锁定、支付成功直接实扣的场景；WHERE current_stock >= quantity 防超卖
     */
    int deductStockBySkuDirect(@Param("productId") Long productId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
    
    /**
     * 查询所有库存记录
     */
    List<Inventory> selectAll();
    
    /**
     * 根据店铺ID查询所有库存
     */
    List<Inventory> selectByStoreId(@Param("storeId") Long storeId);
    
    /**
     * 根据ID查询库存
     * 注意：由于 inventory 表是分片表（基于 store_id），selectById 没有分片键可能查询不到
     * 建议使用 selectByStoreIdAndId
     */
    Inventory selectById(@Param("id") Long id);
    
    /**
     * 根据店铺ID和库存ID查询库存（推荐，包含分片键）
     */
    Inventory selectByStoreIdAndId(@Param("storeId") Long storeId, @Param("id") Long id);
    
    /**
     * 更新库存数量（包含库存模式）
     */
    int updateStock(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("skuId") Long skuId, 
                    @Param("stockMode") Inventory.StockMode stockMode,
                    @Param("currentStock") Integer currentStock, @Param("minStock") Integer minStock, @Param("maxStock") Integer maxStock);
    
    /**
     * 根据ID更新库存数量
     */
    int updateStockById(@Param("id") Long id,
                        @Param("currentStock") Integer currentStock,
                        @Param("minStock") Integer minStock,
                        @Param("maxStock") Integer maxStock);

    /**
     * 查询需要次日恢复的库存
     * 条件：restore_enabled = true AND restore_mode = 'TOMORROW'
     */
    List<Inventory> selectForTomorrowRestore();

    /**
     * 查询需要在指定时间恢复的库存
     * 条件：restore_enabled = true AND restore_mode = 'SCHEDULED' AND restore_time <= now
     */
    List<Inventory> selectForScheduledRestore(@Param("now") LocalDateTime now);

    /**
     * 恢复库存（更新库存状态）
     */
    int restoreInventory(@Param("id") Long id,
                         @Param("stockMode") Inventory.StockMode stockMode,
                         @Param("currentStock") Integer currentStock,
                         @Param("lastRestoreTime") LocalDateTime lastRestoreTime);

    /**
     * 更新库存恢复配置
     */
    int updateRestoreConfig(@Param("id") Long id,
                            @Param("restoreMode") Inventory.RestoreMode restoreMode,
                            @Param("restoreTime") LocalDateTime restoreTime,
                            @Param("restoreStock") Integer restoreStock,
                            @Param("restoreEnabled") Boolean restoreEnabled);

    /**
     * 禁用过期的恢复配置
     * 将 restore_time < cutoffTime 且 last_restore_time IS NOT NULL 的记录
     * 设置为 restore_enabled = false
     */
    int disableExpiredRestoreConfig(@Param("cutoffTime") LocalDateTime cutoffTime);
}

