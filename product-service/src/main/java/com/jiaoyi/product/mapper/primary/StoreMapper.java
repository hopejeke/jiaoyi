package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.Store;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StoreMapper {
    
    /**
     * 插入店铺
     */
    void insert(Store store);
    
    /**
     * 更新店铺
     */
    void update(Store store);
    
    /**
     * 根据ID查询店铺
     */
    Optional<Store> selectById(Long id);
    
    /**
     * 根据店铺编码查询店铺
     */
    Optional<Store> selectByCode(String storeCode);
    
    /**
     * 查询所有店铺
     */
    List<Store> selectAll();
    
    /**
     * 根据状态查询店铺
     */
    List<Store> selectByStatus(Store.StoreStatus status);
    
    /**
     * 根据店铺名称模糊查询
     */
    List<Store> selectByNameLike(String name);
    
    /**
     * 统计店铺总数
     */
    int countAll();
    
    /**
     * 分页查询店铺
     */
    List<Store> selectByPage(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 根据ID删除店铺
     */
    int deleteById(Long id);
    
    /**
     * 递增店铺的商品列表版本号（原子操作）
     * 用于在商品创建/删除时更新版本号
     */
    int incrementProductListVersion(Long storeId);
    
    /**
     * 获取店铺的商品列表版本号
     */
    Long getProductListVersion(Long storeId);
}

