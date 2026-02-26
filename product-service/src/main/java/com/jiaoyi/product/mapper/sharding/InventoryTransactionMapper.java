package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.InventoryTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
     * 尝试插入库存变动记录（用于幂等性校验）
     * 使用 INSERT IGNORE，如果唯一索引冲突则返回 0，否则返回 1
     * 
     * @param transaction 库存变动记录
     * @return 1 如果插入成功（首次操作），0 如果已存在（重复操作）
     */
    int tryInsert(InventoryTransaction transaction);
    
    /**
     * 根据商品ID查询变动记录
     */
    List<InventoryTransaction> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据商品ID和SKU ID查询变动记录
     */
    List<InventoryTransaction> selectByProductIdAndSkuId(@Param("productId") Long productId, @Param("skuId") Long skuId);
    
    /**
     * 根据订单ID查询变动记录
     */
    List<InventoryTransaction> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据订单ID、SKU ID和操作类型查询变动记录
     * 用于检查是否已执行过特定操作（如：检查是否已扣减，防止已扣减的库存又被解锁）
     */
    List<InventoryTransaction> selectByOrderIdAndSkuIdAndType(
            @Param("orderId") Long orderId,
            @Param("skuId") Long skuId,
            @Param("transactionType") String transactionType);
    
    /**
     * 统计商品总入库数量
     */
    Long sumInQuantityByProductId(@Param("productId") Long productId);
    
    /**
     * 统计商品总出库数量
     */
    Long sumOutQuantityByProductId(@Param("productId") Long productId);
    
    /**
     * CAS 更新：从 LOCKED 状态转为 DEDUCTED（扣减）
     * 只有当前状态是 LOCK 时才能更新，保证原子性
     * 
     * @return 更新的行数，1 表示成功，0 表示状态不是 LOCK（已被其他操作修改）
     */
    int casUpdateToDeducted(
            @Param("orderId") Long orderId,
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity,
            @Param("beforeStock") Integer beforeStock,
            @Param("afterStock") Integer afterStock,
            @Param("beforeLocked") Integer beforeLocked,
            @Param("afterLocked") Integer afterLocked,
            @Param("remark") String remark);
    
    /**
     * CAS 更新：从 LOCKED 状态转为 UNLOCKED（解锁）
     * 只有当前状态是 LOCK 时才能更新，保证原子性
     * 
     * @return 更新的行数，1 表示成功，0 表示状态不是 LOCK（已被其他操作修改）
     */
    int casUpdateToUnlocked(
            @Param("orderId") Long orderId,
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity,
            @Param("beforeStock") Integer beforeStock,
            @Param("afterStock") Integer afterStock,
            @Param("beforeLocked") Integer beforeLocked,
            @Param("afterLocked") Integer afterLocked,
            @Param("remark") String remark);
    
    /**
     * 根据订单ID和SKU ID查询当前状态（用于检查）
     */
    InventoryTransaction selectByOrderIdAndSkuId(
            @Param("orderId") Long orderId,
            @Param("skuId") Long skuId);

    // ========================= POI 渠道库存扩展方法 =========================

    /**
     * 查询某时刻之后所有 RELATIVE_DELTA 操作的 delta 总和（绝对设置冲突合并用）
     */
    BigDecimal sumDeltaSince(
        @Param("inventoryId") Long inventoryId,
        @Param("since") LocalDateTime since
    );

    /**
     * 查询该订单所有扣减日志（delta < 0），用于订单取消归还
     */
    List<InventoryTransaction> selectDeductLogsByOrderId(@Param("orderId") String orderId);

    /**
     * 归还幂等检查：查询 orderId + inventoryId 是否已有归还记录
     */
    int countReturnByOrderIdAndInventoryId(
        @Param("orderId") String orderId,
        @Param("inventoryId") Long inventoryId
    );

    /**
     * 扣减幂等检查：查询 orderId + inventoryId 是否已有扣减记录
     */
    int countDeductByOrderIdAndInventoryId(
        @Param("orderId") String orderId,
        @Param("inventoryId") Long inventoryId
    );

    /**
     * 按库存ID查询变动记录（POI 库存日志，支持 limit）
     */
    List<InventoryTransaction> selectByInventoryId(
        @Param("inventoryId") Long inventoryId,
        @Param("limit") int limit
    );
}

