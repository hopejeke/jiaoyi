package com.jiaoyi.service;

import com.jiaoyi.entity.Product;
import com.jiaoyi.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 商品服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductMapper productMapper;
    
    /**
     * 根据ID获取商品
     */
    public Optional<Product> getProductById(Long id) {
        log.info("根据ID查询商品，ID: {}", id);
        return productMapper.selectById(id);
    }
    
    /**
     * 获取所有商品
     */
    public List<Product> getAllProducts() {
        log.info("查询所有商品");
        return productMapper.selectAll();
    }
    
    /**
     * 根据状态获取商品列表
     */
    public List<Product> getProductsByStatus(Product.ProductStatus status) {
        log.info("根据状态查询商品，状态: {}", status);
        return productMapper.selectByStatus(status);
    }
    
    /**
     * 根据分类获取商品列表
     */
    public List<Product> getProductsByCategory(String category) {
        log.info("根据分类查询商品，分类: {}", category);
        return productMapper.selectByCategory(category);
    }
    
    /**
     * 根据商品名称模糊查询
     */
    public List<Product> searchProductsByName(String name) {
        log.info("根据名称搜索商品，名称: {}", name);
        return productMapper.selectByNameLike(name);
    }
    
    /**
     * 分页获取商品列表
     */
    public List<Product> getProductsByPage(int pageNum, int pageSize) {
        log.info("分页查询商品，页码: {}, 大小: {}", pageNum, pageSize);
        int offset = (pageNum - 1) * pageSize;
        return productMapper.selectByPage(offset, pageSize);
    }
    
    /**
     * 获取商品总数
     */
    public int getProductCount() {
        log.info("查询商品总数");
        return productMapper.countAll();
    }
    
    /**
     * 创建商品
     */
    public Product createProduct(Product product) {
        log.info("创建商品，商品名称: {}", product.getProductName());
        productMapper.insert(product);
        return product;
    }
    
    /**
     * 更新商品
     */
    public Product updateProduct(Product product) {
        log.info("更新商品，商品ID: {}", product.getId());
        productMapper.update(product);
        return product;
    }
}
