package com.jiaoyi.mapper;

import com.jiaoyi.entity.StoreProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StoreProductMapper {
    
    /**
     * 插入店铺商品
     */
    void insert(StoreProduct storeProduct);
    
    /**
     * 更新店铺商品
     */
    void update(StoreProduct storeProduct);
    
    /**
     * 根据ID查询店铺商品
     */
    Optional<StoreProduct> selectById(Long id);
    
    /**
     * 根据店铺ID和商品名称查询（用于检查重复）
     */
    Optional<StoreProduct> selectByStoreIdAndProductName(@Param("storeId") Long storeId, @Param("productName") String productName);
    
    /**
     * 根据店铺ID查询所有商品
     */
    List<StoreProduct> selectByStoreId(Long storeId);
    
    /**
     * 根据店铺ID和状态查询商品
     */
    List<StoreProduct> selectByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") StoreProduct.StoreProductStatus status);
    
    /**
     * 根据商品名称模糊查询（跨店铺）
     */
    List<StoreProduct> selectByProductName(@Param("productName") String productName);
    
    /**
     * 删除店铺商品
     */
    int deleteById(Long id);
    
    /**
     * 根据店铺ID删除所有商品
     */
    int deleteByStoreId(Long storeId);
}

