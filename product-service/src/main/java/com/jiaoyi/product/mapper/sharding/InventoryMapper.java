package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    // ========================= POI 渠道库存扩展方法 =========================

    /**
     * 加行锁查询（用于绝对设置冲突合并）
     */
    Inventory selectByIdForUpdate(@Param("id") Long id);

    /**
     * 原子扣减 current_stock（WHERE current_stock >= delta）
     * 返回 affected rows：1=成功，0=库存不足
     */
    int atomicDeductCurrentStock(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 原子扣减 current_stock，同时保证不低于安全线
     * WHERE current_stock - delta >= safetyFloor
     * 返回 affected rows：1=成功，0=库存不足
     */
    int atomicDeductCurrentStockWithFloor(
        @Param("id") Long id,
        @Param("delta") BigDecimal delta,
        @Param("safetyFloor") BigDecimal safetyFloor
    );

    /**
     * 强制更新 current_stock（绝对设置，离线回放超卖时使用）
     */
    int forceUpdateCurrentStock(
        @Param("id") Long id,
        @Param("currentStock") BigDecimal currentStock,
        @Param("lastManualSetTime") LocalDateTime lastManualSetTime,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 原子增加 current_stock（订单取消归还时使用）
     */
    int atomicIncreaseCurrentStock(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * 更新渠道分配模式
     */
    int updateAllocationMode(@Param("id") Long id, @Param("allocationMode") String allocationMode);

    /**
     * 更新库存状态（STATUS_CHANGE 用）
     */
    int updateStockStatusDirect(
        @Param("id") Long id,
        @Param("stockStatus") Integer stockStatus,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 更新共享池库存（渠道额度重新分配时使用）
     */
    int updateSharedPoolQuantity(@Param("id") Long id, @Param("sharedPoolQuantity") BigDecimal sharedPoolQuantity);
}

