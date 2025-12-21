package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.StoreProduct;
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
     * 更新店铺商品（使用乐观锁）
     * @param storeProduct 商品对象，必须包含id和version（用于乐观锁校验）
     * @return 受影响的行数，如果为0表示乐观锁冲突
     */
    int update(StoreProduct storeProduct);
    
    /**
     * 根据ID查询店铺商品（兼容旧接口，建议使用 selectByStoreIdAndId）
     */
    Optional<StoreProduct> selectById(Long id);
    
    /**
     * 根据店铺ID和商品ID查询（推荐，包含分片键）
     */
    Optional<StoreProduct> selectByStoreIdAndId(@Param("storeId") Long storeId, @Param("id") Long id);
    
    /**
     * 根据店铺ID和商品名称查询（用于检查重复）
     */
    Optional<StoreProduct> selectByStoreIdAndProductName(@Param("storeId") Long storeId, @Param("productName") String productName);
    
    /**
     * 根据店铺ID和商品名称查询（包括已删除的，用于恢复商品）
     */
    Optional<StoreProduct> selectByStoreIdAndProductNameIncludeDeleted(@Param("storeId") Long storeId, @Param("productName") String productName);
    
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
     * 查询所有店铺商品（跨店铺，未删除的）
     * 注意：如果数据量大，建议使用分页查询
     */
    List<StoreProduct> selectAll();
    
    /**
     * 删除店铺商品（逻辑删除）
     * @param storeProduct 商品对象，删除后version会通过selectKey自动设置到此对象中
     */
    int deleteById(StoreProduct storeProduct);
    
    /**
     * 恢复已删除的商品（将 is_delete 设置为 0）
     */
    int restoreById(Long id);
    
    /**
     * 根据店铺ID删除所有商品
     */
    int deleteByStoreId(Long storeId);
    
    /**
     * 递增商品的版本号（原子操作）
     * 用于在商品创建/更新/删除时更新版本号
     */
    int incrementVersion(Long productId);
    
    /**
     * 获取商品的版本号
     */
    Long getVersion(Long productId);
}

