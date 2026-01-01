package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.MerchantCapabilityConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商户高峰拒单配置Mapper
 */
@Mapper
public interface MerchantCapabilityConfigMapper {
    
    /**
     * 根据商户ID查询配置
     */
    MerchantCapabilityConfig selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 插入配置
     */
    int insert(MerchantCapabilityConfig config);
    
    /**
     * 更新配置
     */
    int update(MerchantCapabilityConfig config);
    
    /**
     * 更新限流状态（下次开放时间、重新开放时间等）
     */
    int updateCapabilityStatus(
        @Param("merchantId") String merchantId,
        @Param("nextOpenAt") Long nextOpenAt,
        @Param("reOpenAllAt") Long reOpenAllAt,
        @Param("operatePickUp") String operatePickUp,
        @Param("operateDelivery") String operateDelivery,
        @Param("operateTogo") String operateTogo,
        @Param("version") Long version
    );
}

