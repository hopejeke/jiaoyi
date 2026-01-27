package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车Mapper接口
 */
@Mapper
public interface ShoppingCartMapper {
    
    /**
     * 插入购物车
     */
    int insert(ShoppingCart cart);
    
    /**
     * 根据ID查询购物车
     */
    ShoppingCart selectById(@Param("id") Long id);
    
    /**
     * 根据用户ID和门店ID查询购物车（用户购物车）
     */
    ShoppingCart selectByUserIdAndStoreId(
        @Param("userId") Long userId,
        @Param("storeId") Long storeId,
        @Param("tableId") Integer tableId
    );
    
    /**
     * 根据桌码ID和门店ID查询购物车（桌码购物车）
     */
    ShoppingCart selectByTableIdAndStoreId(
        @Param("tableId") Integer tableId,
        @Param("storeId") Long storeId
    );
    
    /**
     * 更新购物车（使用乐观锁）
     */
    int update(ShoppingCart cart);
    
    /**
     * 更新购物车总金额和总数量
     */
    int updateTotal(@Param("id") Long id, 
                    @Param("totalAmount") java.math.BigDecimal totalAmount,
                    @Param("totalQuantity") Integer totalQuantity,
                    @Param("version") Long version);
    
    /**
     * 删除购物车
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 删除过期购物车
     */
    int deleteExpiredCarts(@Param("expireTime") LocalDateTime expireTime);
    
    /**
     * 根据用户ID查询所有购物车
     */
    List<ShoppingCart> selectByUserId(@Param("userId") Long userId);
}


