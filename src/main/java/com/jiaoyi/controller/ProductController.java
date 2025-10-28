package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.Product;
import com.jiaoyi.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 商品控制器
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    
    private final ProductService productService;
    
    /**
     * 获取所有商品
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        log.info("获取所有商品");
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success("查询成功", products));
    }
    
    /**
     * 根据ID获取商品详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long id) {
        log.info("获取商品详情，ID: {}", id);
        Optional<Product> product = productService.getProductById(id);
        return product.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "商品不存在")));
    }
    
    /**
     * 根据状态获取商品列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsByStatus(@PathVariable Product.ProductStatus status) {
        log.info("根据状态获取商品列表，状态: {}", status);
        List<Product> products = productService.getProductsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("查询成功", products));
    }
    
    /**
     * 根据分类获取商品列表
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsByCategory(@PathVariable String category) {
        log.info("根据分类获取商品列表，分类: {}", category);
        List<Product> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success("查询成功", products));
    }
    
    /**
     * 搜索商品
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Product>>> searchProducts(@RequestParam String name) {
        log.info("搜索商品，关键词: {}", name);
        List<Product> products = productService.searchProductsByName(name);
        return ResponseEntity.ok(ApiResponse.success("搜索成功", products));
    }
    
    /**
     * 分页获取商品列表
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsByPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("分页获取商品列表，页码: {}, 大小: {}", pageNum, pageSize);
        List<Product> products = productService.getProductsByPage(pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success("查询成功", products));
    }
    
    /**
     * 获取商品总数
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Integer>> getProductCount() {
        log.info("获取商品总数");
        int count = productService.getProductCount();
        return ResponseEntity.ok(ApiResponse.success("查询成功", count));
    }
    
    /**
     * 创建商品
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody Product product) {
        log.info("创建商品，商品名称: {}", product.getProductName());
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.ok(ApiResponse.success("创建成功", createdProduct));
    }
    
    /**
     * 更新商品
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        log.info("更新商品，商品ID: {}", id);
        product.setId(id);
        Product updatedProduct = productService.updateProduct(product);
        return ResponseEntity.ok(ApiResponse.success("更新成功", updatedProduct));
    }
}
