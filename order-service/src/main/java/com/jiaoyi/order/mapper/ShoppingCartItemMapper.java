package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.ShoppingCartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车项Mapper接口
 */
@Mapper
public interface ShoppingCartItemMapper {
    
    /**
     * 插入购物车项
     */
    int insert(ShoppingCartItem item);
    
    /**
     * 批量插入购物车项
     */
    int insertBatch(@Param("items") List<ShoppingCartItem> items);
    
    /**
     * 根据ID查询购物车项
     */
    ShoppingCartItem selectById(@Param("id") Long id);
    
    /**
     * 根据购物车ID查询所有购物车项
     */
    List<ShoppingCartItem> selectByCartId(@Param("cartId") Long cartId);
    
    /**
     * 根据购物车ID和商品ID查询购物车项
     */
    ShoppingCartItem selectByCartIdAndProductId(
        @Param("cartId") Long cartId,
        @Param("productId") Long productId,
        @Param("skuId") Long skuId
    );
    
    /**
     * 更新购物车项（使用乐观锁）
     */
    int update(ShoppingCartItem item);
    
    /**
     * 更新购物车项数量
     */
    int updateQuantity(@Param("id") Long id,
                       @Param("quantity") Integer quantity,
                       @Param("subtotal") java.math.BigDecimal subtotal,
                       @Param("version") Long version);
    
    /**
     * 删除购物车项
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据购物车ID删除所有购物车项
     */
    int deleteByCartId(@Param("cartId") Long cartId);
    
    /**
     * 根据购物车ID和商品ID删除购物车项
     */
    int deleteByCartIdAndProductId(
        @Param("cartId") Long cartId,
        @Param("productId") Long productId,
        @Param("skuId") Long skuId
    );
}


