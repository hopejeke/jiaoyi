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
     */
    Optional<ProductSku> selectById(@Param("id") Long id);
    
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

