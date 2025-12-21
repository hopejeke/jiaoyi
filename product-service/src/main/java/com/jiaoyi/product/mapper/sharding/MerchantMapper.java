package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆Mapper接口
 */
@Mapper
public interface MerchantMapper {
    
    /**
     * 插入餐馆
     */
    void insert(Merchant merchant);
    
    /**
     * 根据ID查询餐馆
     */
    Optional<Merchant> selectById(@Param("id") Long id);
    
    /**
     * 根据merchantId查询餐馆（包含分片键）
     */
    Optional<Merchant> selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 根据encryptMerchantId查询餐馆
     */
    Optional<Merchant> selectByEncryptMerchantId(@Param("encryptMerchantId") String encryptMerchantId);
    
    /**
     * 根据merchantGroupId查询所有餐馆
     */
    List<Merchant> selectByMerchantGroupId(@Param("merchantGroupId") String merchantGroupId);
    
    /**
     * 查询所有显示的餐馆
     */
    List<Merchant> selectAllDisplay();
    
    /**
     * 更新餐馆（使用乐观锁）
     */
    int update(Merchant merchant);
    
    /**
     * 逻辑删除餐馆
     */
    int deleteById(Merchant merchant);
    
    /**
     * 获取餐馆的当前版本号
     */
    Long getVersion(@Param("merchantId") String merchantId);
}

