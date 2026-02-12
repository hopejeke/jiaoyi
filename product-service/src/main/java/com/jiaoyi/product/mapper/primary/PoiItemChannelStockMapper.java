package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.PoiItemChannelStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品渠道库存表Mapper
 * 注意：此表存储在 primary 数据源，不分库分表
 */
@Mapper
public interface PoiItemChannelStockMapper {
    
    /**
     * 根据库存ID和渠道代码查询
     */
    PoiItemChannelStock selectByStockIdAndChannel(
        @Param("stockId") Long stockId,
        @Param("channelCode") String channelCode
    );
    
    /**
     * 根据库存ID查询所有渠道库存
     */
    List<PoiItemChannelStock> selectByStockId(@Param("stockId") Long stockId);
    
    /**
     * 插入渠道库存记录
     */
    int insert(PoiItemChannelStock channelStock);
    
    /**
     * 更新渠道库存
     */
    int updateChannelStock(
        @Param("id") Long id,
        @Param("stockStatus") Integer stockStatus,
        @Param("stockType") Integer stockType
    );
    
    /**
     * 删除渠道库存
     */
    int deleteByStockId(@Param("stockId") Long stockId);
    
    /**
     * 原子扣减渠道已售数量
     * WHERE (channel_quota - channel_sold) >= delta
     * 返回 affected rows：1=成功，0=渠道额度不足
     */
    int atomicDeductChannelQuota(
        @Param("stockId") Long stockId,
        @Param("channelCode") String channelCode,
        @Param("delta") BigDecimal delta
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
     * 重置渠道已售数量（用于重新分配额度时）
     */
    int resetChannelSold(@Param("stockId") Long stockId);
}
