package com.jiaoyi.mapper;

import com.jiaoyi.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper {
    
    /**
     * 插入商品
     */
    void insert(Product product);
    
    /**
     * 更新商品
     */
    void update(Product product);
    
    /**
     * 根据ID查询商品
     */
    Optional<Product> selectById(Long id);
    
    /**
     * 查询所有商品
     */
    List<Product> selectAll();
    
    /**
     * 根据状态查询商品
     */
    List<Product> selectByStatus(Product.ProductStatus status);
    
    /**
     * 根据分类查询商品
     */
    List<Product> selectByCategory(String category);
    
    /**
     * 根据商品名称模糊查询
     */
    List<Product> selectByNameLike(String name);
    
    /**
     * 统计商品总数
     */
    int countAll();
    
    /**
     * 分页查询商品
     */
    List<Product> selectByPage(@Param("offset") int offset, @Param("limit") int limit);
}
