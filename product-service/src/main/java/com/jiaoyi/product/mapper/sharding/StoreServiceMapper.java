package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.StoreService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆服务Mapper接口
 */
@Mapper
public interface StoreServiceMapper {
    
    /**
     * 插入餐馆服务
     */
    void insert(StoreService storeService);
    
    /**
     * 根据ID查询餐馆服务
     */
    Optional<StoreService> selectById(@Param("id") Long id);
    
    /**
     * 根据merchantId和serviceType查询餐馆服务（包含分片键）
     */
    Optional<StoreService> selectByMerchantIdAndServiceType(
            @Param("merchantId") String merchantId, 
            @Param("serviceType") String serviceType);
    
    /**
     * 根据merchantId查询所有餐馆服务
     */
    List<StoreService> selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 更新餐馆服务（使用乐观锁）
     */
    int update(StoreService storeService);
    
    /**
     * 删除餐馆服务
     */
    int deleteById(StoreService storeService);
    
    /**
     * 获取餐馆服务的当前版本号
     */
    Long getVersion(@Param("merchantId") String merchantId, @Param("serviceType") String serviceType);
}

