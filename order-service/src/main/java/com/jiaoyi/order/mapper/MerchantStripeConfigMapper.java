package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.MerchantStripeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商户 Stripe 配置 Mapper
 */
@Mapper
public interface MerchantStripeConfigMapper {
    
    /**
     * 根据商户ID查询配置
     */
    MerchantStripeConfig selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 插入配置
     */
    int insert(MerchantStripeConfig config);
    
    /**
     * 更新配置
     */
    int update(MerchantStripeConfig config);
    
    /**
     * 根据商户ID删除配置
     */
    int deleteByMerchantId(@Param("merchantId") String merchantId);
}






