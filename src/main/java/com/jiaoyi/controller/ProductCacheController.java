package com.jiaoyi.controller;

import com.jiaoyi.entity.Product;
import com.jiaoyi.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 商品缓存管理控制器
 */
@RestController
@RequestMapping("/api/product-cache")
@RequiredArgsConstructor
@Slf4j
public class ProductCacheController {

    private final ProductService productService;

    /**
     * 根据商品ID查询商品（优先从缓存）
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductById(@PathVariable Long productId) {
        Optional<Product> product = productService.getProductById(productId);
        if (product.isPresent()) {
            return ResponseEntity.ok(product.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 查询所有商品（优先从缓存）
     */
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * 根据状态查询商品（优先从缓存）
     */
    @GetMapping("/products/status/{status}")
    public ResponseEntity<List<Product>> getProductsByStatus(@PathVariable String status) {
        try {
            Product.ProductStatus productStatus = Product.ProductStatus.valueOf(status.toUpperCase());
            List<Product> products = productService.getProductsByStatus(productStatus);
            return ResponseEntity.ok(products);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 根据分类查询商品（优先从缓存）
     */
    @GetMapping("/products/category/{category}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable String category) {
        List<Product> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(products);
    }

    /**
     * 刷新单个商品缓存
     */
    @PostMapping("/refresh/{productId}")
    public ResponseEntity<String> refreshProductCache(@PathVariable Long productId) {
        productService.refreshProductCache(productId);
        return ResponseEntity.ok("商品ID " + productId + " 的缓存已刷新。");
    }

    /**
     * 刷新商品列表缓存
     */
    @PostMapping("/refresh/list")
    public ResponseEntity<String> refreshProductListCache() {
        productService.refreshProductListCache();
        return ResponseEntity.ok("商品列表缓存已刷新。");
    }

    /**
     * 清空所有商品缓存
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearAllProductCache() {
        productService.clearAllProductCache();
        return ResponseEntity.ok("所有商品缓存已清空。");
    }

    /**
     * 获取单个商品缓存状态
     */
    @GetMapping("/status/{productId}")
    public ResponseEntity<String> getProductCacheStatus(@PathVariable Long productId) {
        boolean hasCache = productService.hasProductCache(productId);
        if (hasCache) {
            Long expireTime = productService.getProductCacheExpireTime(productId);
            return ResponseEntity.ok("商品ID " + productId + " 的缓存存在，剩余时间: " + expireTime + " 秒。");
        } else {
            return ResponseEntity.ok("商品ID " + productId + " 的缓存不存在。");
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<String> getCacheStats() {
        return ResponseEntity.ok("商品缓存统计信息：\n" +
                "单个商品缓存TTL: 30分钟\n" +
                "商品列表缓存TTL: 10分钟\n" +
                "分类商品列表缓存TTL: 10分钟\n" +
                "状态商品列表缓存TTL: 10分钟\n" +
                "（实际统计数据需要集成Redis监控工具）");
    }
}

