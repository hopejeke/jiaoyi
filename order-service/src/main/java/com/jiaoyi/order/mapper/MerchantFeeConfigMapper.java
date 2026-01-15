package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.MerchantFeeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商户费用配置 Mapper
 */
@Mapper
public interface MerchantFeeConfigMapper {

    /**
     * 根据商户ID查询配置
     */
    MerchantFeeConfig selectByMerchantId(@Param("merchantId") String merchantId);

    /**
     * 插入配置
     */
    int insert(MerchantFeeConfig config);

    /**
     * 更新配置
     */
    int update(MerchantFeeConfig config);

    /**
     * 根据商户ID删除配置
     */
    int deleteByMerchantId(@Param("merchantId") String merchantId);
}














