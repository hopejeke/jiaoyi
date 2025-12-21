package com.jiaoyi.product.mapper.sharding;

import com.jiaoyi.product.entity.MenuItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 菜单项信息Mapper接口
 */
@Mapper
public interface MenuItemMapper {
    
    /**
     * 插入菜单项信息
     */
    void insert(MenuItem menuItem);
    
    /**
     * 根据ID查询菜单项信息
     */
    Optional<MenuItem> selectById(@Param("id") Long id);
    
    /**
     * 根据merchantId和itemId查询菜单项信息（包含分片键）
     */
    Optional<MenuItem> selectByMerchantIdAndItemId(
            @Param("merchantId") String merchantId, 
            @Param("itemId") Long itemId);
    
    /**
     * 根据merchantId查询所有菜单项信息
     */
    List<MenuItem> selectByMerchantId(@Param("merchantId") String merchantId);
    
    /**
     * 更新菜单项信息（使用乐观锁）
     */
    int update(MenuItem menuItem);
    
    /**
     * 删除菜单项信息
     */
    int deleteById(MenuItem menuItem);
    
    /**
     * 获取菜单项信息的当前版本号
     */
    Long getVersion(@Param("merchantId") String merchantId, @Param("itemId") Long itemId);
}

