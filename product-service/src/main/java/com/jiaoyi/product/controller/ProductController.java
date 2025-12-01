package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.service.StoreProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品控制器（兼容前端 /api/products 接口）
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    
    private final StoreProductService storeProductService;
    
    /**
     * 获取所有商品（兼容前端 /api/products 接口）
     * 返回所有店铺的商品列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getAllProducts() {
        log.info("获取所有商品（兼容前端 /api/products 接口）");
        List<StoreProduct> products = storeProductService.getAllStoreProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }
    
    /**
     * 根据ID获取商品详情（兼容前端 /api/products/{id} 接口）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreProduct>> getProductById(@PathVariable Long id) {
        log.info("获取商品详情（兼容前端 /api/products/{id} 接口），ID: {}", id);
        return storeProductService.getStoreProductById(id)
                .map(product -> ResponseEntity.ok(ApiResponse.success(product)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "商品不存在")));
    }
}


