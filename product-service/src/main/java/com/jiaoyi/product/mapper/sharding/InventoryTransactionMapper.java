package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.InventoryTransaction;
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
}

