package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.StoreProduct;
import com.jiaoyi.service.StoreProductMsgTransactionService;
import com.jiaoyi.service.StoreProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 店铺商品控制器
 */
@RestController
@RequestMapping("/api/store-products")
@RequiredArgsConstructor
@Slf4j
public class StoreProductController {
    
    private final StoreProductService storeProductService;
    private final StoreProductMsgTransactionService storeProductMsgTransactionService;
    
    /**
     * 获取所有店铺商品（可跨店铺）
     * 注意：如果数据量大，建议使用分页查询
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getAllStoreProducts() {
        log.info("获取所有店铺商品");
        List<StoreProduct> allProducts = storeProductService.getAllStoreProducts();
        return ResponseEntity.ok(ApiResponse.success("查询成功", allProducts));
    }
    
    /**
     * 根据店铺ID获取该店铺的所有商品
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getStoreProductsByStoreId(@PathVariable Long storeId) {
        log.info("获取店铺商品列表，店铺ID: {}", storeId);
        List<StoreProduct> storeProducts = storeProductService.getStoreProducts(storeId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", storeProducts));
    }
    
    /**
     * 根据ID获取店铺商品详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreProduct>> getStoreProductById(@PathVariable Long id) {
        log.info("获取店铺商品详情，ID: {}", id);
        Optional<StoreProduct> storeProduct = storeProductService.getStoreProductById(id);
        return storeProduct.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "商品不存在")));
    }
    
    /**
     * 根据店铺ID和状态获取商品列表
     */
    @GetMapping("/store/{storeId}/status/{status}")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getStoreProductsByStatus(
            @PathVariable Long storeId,
            @PathVariable String status) {
        log.info("根据状态获取店铺商品列表，店铺ID: {}, 状态: {}", storeId, status);
        StoreProduct.StoreProductStatus productStatus = StoreProduct.StoreProductStatus.valueOf(status.toUpperCase());
        List<StoreProduct> storeProducts = storeProductService.getStoreProductsByStatus(storeId, productStatus);
        return ResponseEntity.ok(ApiResponse.success("查询成功", storeProducts));
    }
    
    /**
     * 根据店铺ID和分类获取商品列表
     */
    @GetMapping("/store/{storeId}/category/{category}")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getStoreProductsByCategory(
            @PathVariable Long storeId,
            @PathVariable String category) {
        log.info("根据分类获取店铺商品列表，店铺ID: {}, 分类: {}", storeId, category);
        List<StoreProduct> allProducts = storeProductService.getStoreProducts(storeId);
        List<StoreProduct> filteredProducts = allProducts.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("查询成功", filteredProducts));
    }
    
    /**
     * 搜索店铺商品（按店铺ID和商品名称）
     */
    @GetMapping("/store/{storeId}/search")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> searchStoreProducts(
            @PathVariable Long storeId,
            @RequestParam String name) {
        log.info("搜索店铺商品，店铺ID: {}, 关键词: {}", storeId, name);
        List<StoreProduct> allProducts = storeProductService.getStoreProducts(storeId);
        List<StoreProduct> filteredProducts = allProducts.stream()
                .filter(p -> p.getProductName() != null && 
                           p.getProductName().toLowerCase().contains(name.toLowerCase()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("搜索成功", filteredProducts));
    }
    
    /**
     * 跨店铺搜索商品（按商品名称）
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> searchAllStoreProducts(@RequestParam String name) {
        log.info("跨店铺搜索商品，关键词: {}", name);
        List<StoreProduct> products = storeProductService.searchStoreProductsByName(name);
        return ResponseEntity.ok(ApiResponse.success("搜索成功", products));
    }
    
    /**
     * 获取店铺商品总数
     */
    @GetMapping("/store/{storeId}/count")
    public ResponseEntity<ApiResponse<Integer>> getStoreProductCount(@PathVariable Long storeId) {
        log.info("获取店铺商品总数，店铺ID: {}", storeId);
        int count = storeProductService.getStoreProductCount(storeId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", count));
    }
    
    /**
     * 创建店铺商品
     * 
     * 【RocketMQ事务消息流程】
     * 1. Controller调用消息服务发送半消息
     * 2. executeLocalTransaction中调用StoreProductService.createStoreProductInternal()执行数据库INSERT
     * 3. 如果INSERT成功，提交半消息；如果失败，回滚
     */
    @PostMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<StoreProduct>> createStoreProduct(
            @PathVariable Long storeId,
            @RequestBody StoreProduct storeProduct) {
        log.info("创建店铺商品，店铺ID: {}, 商品名称: {}", storeId, storeProduct.getProductName());
        
        // 使用AtomicReference来传递insert后的productId
        AtomicReference<Long> productIdRef = new AtomicReference<>();
        
        // 发送事务消息（半消息），数据库操作在executeLocalTransaction中执行
        storeProductMsgTransactionService.createInMsgTransaction(storeId, storeProduct, productIdRef);
        
        // 从AtomicReference获取insert后的ID（executeLocalTransaction已执行完成）
        Long productId = productIdRef.get();
        if (productId == null) {
            return ResponseEntity.ok(ApiResponse.error(500, "店铺商品创建失败：未获取到productId"));
        }
        
        storeProduct.setId(productId);
        storeProduct.setStoreId(storeId);
        log.info("店铺商品创建成功，商品ID: {}", productId);
        return ResponseEntity.ok(ApiResponse.success("创建成功", storeProduct));
    }
    
    /**
     * 更新店铺商品
     * 
     * 【RocketMQ事务消息流程】
     * 1. Controller调用消息服务发送半消息
     * 2. executeLocalTransaction中调用StoreProductService.updateStoreProductInternal()执行数据库UPDATE
     * 3. 如果UPDATE成功，提交半消息；如果失败，回滚
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreProduct>> updateStoreProduct(
            @PathVariable Long id,
            @RequestBody StoreProduct storeProduct) {
        log.info("更新店铺商品，商品ID: {}", id);
        storeProduct.setId(id);
        
        // 获取storeId（从storeProduct对象或从数据库查询）
        Long storeId = storeProduct.getStoreId();
        if (storeId == null) {
            // 如果storeId为空，从数据库查询
            Optional<StoreProduct> existing = storeProductService.getStoreProductById(id);
            if (existing.isPresent()) {
                storeId = existing.get().getStoreId();
                storeProduct.setStoreId(storeId);
            } else {
                return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
            }
        }
        
        // 发送事务消息（半消息），数据库操作在executeLocalTransaction中执行
        storeProductMsgTransactionService.updateInMsgTransaction(id, storeProduct);
        
        log.info("店铺商品更新消息已发送，商品ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success("更新成功", storeProduct));
    }
    
    /**
     * 删除店铺商品
     * 
     * 【RocketMQ事务消息流程】
     * 1. Controller调用消息服务发送半消息
     * 2. executeLocalTransaction中调用StoreProductService.deleteStoreProductInternal()执行数据库DELETE
     * 3. 如果DELETE成功，提交半消息；如果失败，回滚
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreProduct(@PathVariable Long id) {
        log.info("删除店铺商品，商品ID: {}", id);
        
        // 获取storeId（可选，用于优化）
        Long storeId = null;
        Optional<StoreProduct> existing = storeProductService.getStoreProductById(id);
        if (existing.isPresent()) {
            storeId = existing.get().getStoreId();
        }
        
        // 发送事务消息（半消息），数据库操作在executeLocalTransaction中执行
        storeProductMsgTransactionService.deleteInMsgTransaction(id, storeId);
        
        log.info("店铺商品删除消息已发送，商品ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }
    
}

