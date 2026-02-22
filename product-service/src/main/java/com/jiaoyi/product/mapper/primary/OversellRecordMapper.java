package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.OversellRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超卖记录表Mapper
 */
@Mapper
public interface OversellRecordMapper {
    
    int insert(OversellRecord record);
    
    OversellRecord selectById(@Param("id") Long id);
    
    List<OversellRecord> selectByBrandIdAndStoreId(
        @Param("brandId") String brandId,
        @Param("storeId") String storeId,
        @Param("status") String status,
        @Param("limit") Integer limit
    );
    
    int updateStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("resolvedBy") String resolvedBy,
        @Param("remark") String remark
    );
}
