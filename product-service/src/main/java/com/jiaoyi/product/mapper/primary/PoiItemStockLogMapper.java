package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.PoiItemStockLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存变更记录表Mapper
 * 注意：此表存储在 primary 数据源，不分库分表
 */
@Mapper
public interface PoiItemStockLogMapper {
    
    /**
     * 插入变更记录
     */
    int insert(PoiItemStockLog log);
    
    /**
     * 根据库存ID查询变更记录列表
     */
    List<PoiItemStockLog> selectByStockId(
        @Param("stockId") Long stockId,
        @Param("limit") Integer limit
    );
    
    /**
     * 根据品牌ID、门店ID查询变更记录列表
     */
    List<PoiItemStockLog> selectByBrandIdAndPoiId(
        @Param("brandId") String brandId,
        @Param("poiId") String poiId,
        @Param("limit") Integer limit
    );
    
    /**
     * 根据订单ID检查是否已存在（幂等性检查）
     * 返回 count
     */
    int countByOrderId(@Param("orderId") String orderId);
    
    /**
     * 计算指定时间之后的相对变更总量（RELATIVE_DELTA的delta之和）
     * 用于绝对设置的冲突合并：补偿这段时间内的自动扣减
     */
    BigDecimal sumDeltaSince(
        @Param("stockId") Long stockId,
        @Param("sinceTime") LocalDateTime sinceTime
    );
}
