package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.InventoryDeductionIdempotency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存扣减幂等性日志 Mapper 接口
 */
@Mapper
public interface InventoryDeductionIdempotencyMapper {
    
    /**
     * 尝试插入幂等性日志（如果 idempotencyKey 已存在则返回 0）
     * 使用 INSERT IGNORE 或 INSERT ... ON DUPLICATE KEY UPDATE 实现幂等
     * 
     * @param idempotencyKey 幂等键
     * @param orderId 订单ID
     * @param productShardId 分片ID（0-1023，基于storeId计算）
     * @param productIds 商品ID列表（JSON格式）
     * @param skuIds SKU ID列表（JSON格式）
     * @param quantities 数量列表（JSON格式）
     * @return 插入的行数（1表示成功插入，0表示已存在）
     */
    int tryInsert(@Param("idempotencyKey") String idempotencyKey,
                  @Param("orderId") Long orderId,
                  @Param("productShardId") Integer productShardId,
                  @Param("productIds") String productIds,
                  @Param("skuIds") String skuIds,
                  @Param("quantities") String quantities);
    
    /**
     * 更新状态为成功
     * 
     * @param idempotencyKey 幂等键
     * @param productShardId 分片ID（0-1023，用于路由到正确的库）
     * @return 更新的行数
     */
    int updateStatusToSuccess(@Param("idempotencyKey") String idempotencyKey,
                              @Param("productShardId") Integer productShardId);
    
    /**
     * 更新状态为失败
     * 
     * @param idempotencyKey 幂等键
     * @param productShardId 分片ID（0-1023，用于路由到正确的库）
     * @param errorMessage 错误信息
     * @return 更新的行数
     */
    int updateStatusToFailed(@Param("idempotencyKey") String idempotencyKey,
                            @Param("productShardId") Integer productShardId,
                            @Param("errorMessage") String errorMessage);
    
    /**
     * 根据幂等键查询（需要提供 productShardId 用于分片路由）
     * 
     * @param idempotencyKey 幂等键
     * @param productShardId 分片ID（0-1023，用于路由到正确的库）
     * @return 幂等性日志
     */
    InventoryDeductionIdempotency selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey,
                                                         @Param("productShardId") Integer productShardId);
}

