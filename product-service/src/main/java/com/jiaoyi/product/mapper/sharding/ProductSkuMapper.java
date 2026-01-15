package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 商品SKU Mapper接口
 * 
 * @author Administrator
 */
@Mapper
public interface ProductSkuMapper {
    
    /**
     * 插入SKU
     */
    void insert(ProductSku sku);
    
    /**
     * 根据ID查询SKU
     * 注意：由于 product_sku 表是分片表（基于 store_id），selectById 没有分片键可能查询不到
     * 建议使用 selectByProductIdAndId 或 selectByStoreIdAndId
     */
    Optional<ProductSku> selectById(@Param("id") Long id);
    
    /**
     * 根据商品ID和SKU ID查询SKU（推荐，包含分片键）
     */
    Optional<ProductSku> selectByProductIdAndId(@Param("productId") Long productId, @Param("id") Long id);
    
    /**
     * 根据店铺ID和SKU ID查询SKU（推荐，包含分片键）
     */
    Optional<ProductSku> selectByStoreIdAndId(@Param("storeId") Long storeId, @Param("id") Long id);
    
    /**
     * 根据商品ID查询所有SKU
     */
    List<ProductSku> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据商品ID和SKU编码查询SKU
     */
    Optional<ProductSku> selectByProductIdAndSkuCode(@Param("productId") Long productId, @Param("skuCode") String skuCode);
    
    /**
     * 更新SKU
     */
    int update(ProductSku sku);
    
    /**
     * 逻辑删除SKU（根据ID，支持乐观锁）
     */
    int deleteById(ProductSku sku);
}

