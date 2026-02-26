package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.InventoryOversellRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存超卖记录Mapper接口（sharding分片表）
 */
@Mapper
public interface InventoryOversellRecordMapper {

    /**
     * 插入超卖记录
     */
    int insert(InventoryOversellRecord record);

    /**
     * 根据门店ID查询超卖记录
     */
    List<InventoryOversellRecord> selectByStoreId(@Param("storeId") Long storeId);

    /**
     * 根据门店ID和状态查询超卖记录
     */
    List<InventoryOversellRecord> selectByStoreIdAndStatus(
        @Param("storeId") Long storeId,
        @Param("status") String status
    );

    /**
     * 更新超卖记录状态
     */
    int updateStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("resolvedBy") String resolvedBy,
        @Param("resolvedAt") LocalDateTime resolvedAt,
        @Param("remark") String remark
    );
}
