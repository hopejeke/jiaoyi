package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.InventoryChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 渠道库存Mapper接口（sharding分片表）
 */
@Mapper
public interface InventoryChannelMapper {

    /**
     * 根据inventoryId和渠道代码查询
     */
    InventoryChannel selectByInventoryIdAndChannel(
        @Param("inventoryId") Long inventoryId,
        @Param("channelCode") String channelCode
    );

    /**
     * 根据inventoryId查询所有渠道库存
     */
    List<InventoryChannel> selectByInventoryId(@Param("inventoryId") Long inventoryId);

    /**
     * 插入渠道库存记录
     */
    int insert(InventoryChannel inventoryChannel);

    /**
     * 删除指定inventory的所有渠道库存
     */
    int deleteByInventoryId(@Param("inventoryId") Long inventoryId);

    /**
     * 方案一：增加渠道已售数并校验不超过 channel_max（0=不设上限）
     * 返回 affected rows：1=成功，0=超过渠道上限
     */
    int atomicIncreaseChannelSoldWithCap(
        @Param("inventoryId") Long inventoryId,
        @Param("channelCode") String channelCode,
        @Param("delta") BigDecimal delta
    );

    /**
     * 原子减少渠道已售数量（订单取消归还时使用）
     * 需保证 channel_sold >= qty
     * 返回 affected rows：1=成功，0=失败
     */
    int atomicDecreaseChannelSold(
        @Param("inventoryId") Long inventoryId,
        @Param("channelCode") String channelCode,
        @Param("qty") BigDecimal qty
    );

    /**
     * 更新渠道额度和权重（用于重新分配）
     */
    int updateChannelQuotaAndWeight(
        @Param("id") Long id,
        @Param("channelQuota") BigDecimal channelQuota,
        @Param("channelWeight") BigDecimal channelWeight
    );

    /**
     * 重置渠道已售数量（重新分配额度时使用）
     */
    int resetChannelSold(@Param("inventoryId") Long inventoryId);

    /**
     * 查询比当前渠道优先级更高的渠道的 safety_stock 总和（方案二 SAFETY_STOCK 用）
     */
    BigDecimal sumSafetyStockForHigherPriority(
        @Param("inventoryId") Long inventoryId,
        @Param("myPriority") Integer myPriority
    );

    /**
     * 更新渠道优先级与安全线（运营后台配置）
     */
    int updatePriorityAndSafetyStock(
        @Param("id") Long id,
        @Param("channelPriority") Integer channelPriority,
        @Param("safetyStock") BigDecimal safetyStock
    );
}
