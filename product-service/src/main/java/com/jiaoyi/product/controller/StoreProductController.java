package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.service.InventoryService;
import com.jiaoyi.product.service.StoreProductService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 店铺商品控制器
 */
@RestController
@RequestMapping("/api/store-products")
@RequiredArgsConstructor
@Slf4j
public class StoreProductController {
    
    private final StoreProductService storeProductService;
    private final InventoryService inventoryService;
    private final com.jiaoyi.product.service.ProductSkuService productSkuService;
    
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
     * 通过商户ID和商品ID获取商品信息（包含SKU列表）
     * 用于订单服务查询商品信息
     */
    @GetMapping("/merchant/{merchantId}/{productId}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getProductByMerchantIdAndId(
            @PathVariable String merchantId,
            @PathVariable Long productId) {
        log.info("通过商户ID和商品ID获取商品信息，商户ID: {}, 商品ID: {}", merchantId, productId);
        
        // 1. 获取商户信息以获取storeId
        Long storeId = storeProductService.getStoreIdByMerchantId(merchantId);
        if (storeId == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "商户不存在"));
        }
        
        // 2. 查询商品信息
        Optional<StoreProduct> productOpt = storeProductService.getStoreProductByIdFromDb(storeId, productId);
        if (!productOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
        }
        
        StoreProduct product = productOpt.get();
        
        // 3. 查询SKU列表
        List<com.jiaoyi.product.entity.ProductSku> skus = productSkuService.getSkusByProductId(productId);
        
        // 4. 查询每个SKU的库存信息
        List<java.util.Map<String, Object>> skuList = new java.util.ArrayList<>();
        for (com.jiaoyi.product.entity.ProductSku sku : skus) {
            java.util.Map<String, Object> skuMap = new java.util.HashMap<>();
            skuMap.put("id", sku.getId());
            skuMap.put("skuCode", sku.getSkuCode());
            skuMap.put("skuName", sku.getSkuName());
            skuMap.put("skuPrice", sku.getSkuPrice());
            skuMap.put("skuAttributes", sku.getSkuAttributes());
            skuMap.put("skuImage", sku.getSkuImage());
            skuMap.put("status", sku.getStatus());
            
            // 查询SKU库存
            Optional<Inventory> inventoryOpt = inventoryService.getInventoryBySkuId(storeId, productId, sku.getId());
            if (inventoryOpt.isPresent()) {
                Inventory inventory = inventoryOpt.get();
                skuMap.put("currentStock", inventory.getCurrentStock());
                skuMap.put("lockedStock", inventory.getLockedStock());
            } else {
                skuMap.put("currentStock", 0);
                skuMap.put("lockedStock", 0);
            }
            
            skuList.add(skuMap);
        }
        
        // 5. 构建响应
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", product.getId());
        response.put("storeId", product.getStoreId());
        response.put("productName", product.getProductName());
        response.put("description", product.getDescription());
        response.put("unitPrice", product.getUnitPrice());
        response.put("productImage", product.getProductImage());
        response.put("category", product.getCategory());
        response.put("status", product.getStatus());
        response.put("skus", skuList);
        
        return ResponseEntity.ok(ApiResponse.success("查询成功", response));
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
     * 创建店铺商品（使用Outbox模式）
     * 
     * 【Outbox模式流程】：
     * 1. 在同一个本地事务中：插入商品、创建库存、写入outbox表
     * 2. 事务提交后，定时任务扫描outbox表并发送消息到RocketMQ
     * 3. 消费者接收消息并更新缓存
     */
    @PostMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<StoreProduct>> createStoreProduct(
            @PathVariable Long storeId,
            @RequestBody StoreProduct storeProduct) {
        log.info("创建店铺商品（Outbox模式），店铺ID: {}, 商品名称: {}", storeId, storeProduct.getProductName());
        
        try {
            StoreProduct createdProduct = storeProductService.createStoreProduct(storeId, storeProduct);
            log.info("店铺商品创建成功，商品ID: {}", createdProduct.getId());
            return ResponseEntity.ok(ApiResponse.success("创建成功", createdProduct));
        } catch (Exception e) {
            log.error("创建店铺商品失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新店铺商品（使用Outbox模式）
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreProduct>> updateStoreProduct(
            @PathVariable Long id,
            @RequestBody StoreProduct storeProduct) {
        log.info("更新店铺商品（Outbox模式），商品ID: {}", id);
        storeProduct.setId(id);
        
        try {
            storeProductService.updateStoreProduct(storeProduct);
            
            // 重新查询更新后的商品
            Optional<StoreProduct> updated = storeProductService.getStoreProductById(id);
            if (updated.isPresent()) {
                log.info("店铺商品更新成功，商品ID: {}", id);
                return ResponseEntity.ok(ApiResponse.success("更新成功", updated.get()));
            } else {
                return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
            }
        } catch (Exception e) {
            log.error("更新店铺商品失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除店铺商品（使用Outbox模式）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreProduct(@PathVariable Long id) {
        log.info("删除店铺商品（Outbox模式），商品ID: {}", id);
        
        // 获取storeId（可选，用于优化）
        Long storeId = null;
        Optional<StoreProduct> existing = storeProductService.getStoreProductById(id);
        if (existing.isPresent()) {
            storeId = existing.get().getStoreId();
        }
        
        try {
            storeProductService.deleteStoreProduct(id, storeId);
            log.info("店铺商品删除成功，商品ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除店铺商品失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }

    // ==================== B端商家专用接口（直接读DB，不走缓存） ====================
    // 这些接口用于商家后台页面，确保修改后立即看到最新数据，不受缓存延迟影响

    /**
     * 通过商户ID获取该商户的所有商品（包含SKU列表）
     * 用于前端页面展示商户商品列表
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getProductsByMerchantId(
            @PathVariable String merchantId) {
        log.info("通过商户ID获取商品列表，商户ID: {}", merchantId);
        
        // 1. 获取商户信息以获取storeId
        Long storeId = storeProductService.getStoreIdByMerchantId(merchantId);
        if (storeId == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "商户不存在"));
        }
        
        // 2. 查询商品列表
        List<StoreProduct> products = storeProductService.getStoreProductsFromDb(storeId);
        
        // 3. 为每个商品查询SKU列表
        List<java.util.Map<String, Object>> productList = new java.util.ArrayList<>();
        for (StoreProduct product : products) {
            java.util.Map<String, Object> productMap = new java.util.HashMap<>();
            productMap.put("id", product.getId());
            productMap.put("storeId", product.getStoreId());
            productMap.put("productName", product.getProductName());
            productMap.put("description", product.getDescription());
            productMap.put("unitPrice", product.getUnitPrice());
            productMap.put("productImage", product.getProductImage());
            productMap.put("category", product.getCategory());
            productMap.put("status", product.getStatus());
            
            // 查询SKU列表
            List<com.jiaoyi.product.entity.ProductSku> skus = productSkuService.getSkusByProductId(product.getId());
            List<java.util.Map<String, Object>> skuList = new java.util.ArrayList<>();
            for (com.jiaoyi.product.entity.ProductSku sku : skus) {
                java.util.Map<String, Object> skuMap = new java.util.HashMap<>();
                skuMap.put("id", sku.getId());
                skuMap.put("skuCode", sku.getSkuCode());
                skuMap.put("skuName", sku.getSkuName());
                skuMap.put("skuPrice", sku.getSkuPrice());
                skuMap.put("skuAttributes", sku.getSkuAttributes());
                skuMap.put("skuImage", sku.getSkuImage());
                skuMap.put("status", sku.getStatus());
                
                // 查询SKU库存
                Optional<Inventory> inventoryOpt = inventoryService.getInventoryBySkuId(storeId, product.getId(), sku.getId());
                if (inventoryOpt.isPresent()) {
                    Inventory inventory = inventoryOpt.get();
                    skuMap.put("currentStock", inventory.getCurrentStock());
                    skuMap.put("lockedStock", inventory.getLockedStock());
                } else {
                    skuMap.put("currentStock", 0);
                    skuMap.put("lockedStock", 0);
                }
                
                skuList.add(skuMap);
            }
            productMap.put("skus", skuList);
            
            productList.add(productMap);
        }
        
        return ResponseEntity.ok(ApiResponse.success("查询成功", productList));
    }
    
    /**
     * B端商家：根据店铺ID获取该店铺的所有商品（直接读DB）
     * 用于商家后台页面，确保修改后立即看到最新数据
     */
    @GetMapping("/merchant/store/{storeId}")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getStoreProductsByStoreIdForMerchant(@PathVariable Long storeId) {
        log.info("B端商家获取店铺商品列表（直接读DB），店铺ID: {}", storeId);
        List<StoreProduct> storeProducts = storeProductService.getStoreProductsFromDb(storeId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", storeProducts));
    }

    /**
     * B端商家：根据ID获取店铺商品详情（直接读DB）
     * 用于商家后台页面，确保修改后立即看到最新数据
     * 如果提供了storeId，使用包含分片键的查询（推荐）
     */
    @GetMapping("/merchant/product/{id}")
    public ResponseEntity<ApiResponse<StoreProduct>> getStoreProductByIdForMerchant(
            @PathVariable Long id,
            @RequestParam(required = false) Long storeId) {
        log.info("B端商家获取店铺商品详情（直接读DB），ID: {}, 店铺ID: {}", id, storeId);
        
        Optional<StoreProduct> storeProduct;
        if (storeId != null) {
            // 使用包含分片键的查询（推荐，性能更好）
            storeProduct = storeProductService.getStoreProductByIdFromDb(storeId, id);
        } else {
            // 兼容旧接口，广播查询所有分片
            storeProduct = storeProductService.getStoreProductByIdFromDb(id);
        }
        
        return storeProduct.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "商品不存在")));
    }

    /**
     * B端商家：根据店铺ID和状态获取商品列表（直接读DB）
     */
    @GetMapping("/merchant/store/{storeId}/status/{status}")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> getStoreProductsByStatusForMerchant(
            @PathVariable Long storeId,
            @PathVariable String status) {
        log.info("B端商家根据状态获取店铺商品列表（直接读DB），店铺ID: {}, 状态: {}", storeId, status);
        StoreProduct.StoreProductStatus productStatus = StoreProduct.StoreProductStatus.valueOf(status.toUpperCase());
        List<StoreProduct> storeProducts = storeProductService.getStoreProductsByStatusFromDb(storeId, productStatus);
        return ResponseEntity.ok(ApiResponse.success("查询成功", storeProducts));
    }

    /**
     * B端商家：搜索店铺商品（按店铺ID和商品名称，直接读DB）
     */
    @GetMapping("/merchant/store/{storeId}/search")
    public ResponseEntity<ApiResponse<List<StoreProduct>>> searchStoreProductsForMerchant(
            @PathVariable Long storeId,
            @RequestParam String name) {
        log.info("B端商家搜索店铺商品（直接读DB），店铺ID: {}, 关键词: {}", storeId, name);
        List<StoreProduct> filteredProducts = storeProductService.searchStoreProductsFromDb(storeId, name);
        return ResponseEntity.ok(ApiResponse.success("搜索成功", filteredProducts));
    }

    /**
     * B端商家：获取店铺商品总数（直接读DB）
     */
    @GetMapping("/merchant/store/{storeId}/count")
    public ResponseEntity<ApiResponse<Integer>> getStoreProductCountForMerchant(@PathVariable Long storeId) {
        log.info("B端商家获取店铺商品总数（直接读DB），店铺ID: {}", storeId);
        int count = storeProductService.getStoreProductCountFromDb(storeId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", count));
    }
    
    /**
     * 设置商品库存（商品级别或SKU级别）
     */
    @PostMapping("/{productId}/inventory/set-stock")
    public ResponseEntity<ApiResponse<Inventory>> setProductStock(
            @PathVariable Long productId,
            @RequestBody SetStockRequest request) {
        log.info("设置商品库存，商品ID: {}, SKU ID: {}, 库存: {}", productId, request.getSkuId(), request.getStock());
        
        // 获取商品信息以获取storeId
        Optional<StoreProduct> product = storeProductService.getStoreProductByIdFromDb(productId);
        if (!product.isPresent()) {
            return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
        }
        
        Long storeId = product.get().getStoreId();
        
        try {
            Inventory inventory = inventoryService.setStock(
                storeId,
                productId,
                request.getSkuId(),
                request.getStock(),
                request.getMinStock(),
                request.getMaxStock()
            );
            return ResponseEntity.ok(ApiResponse.success("库存设置成功", inventory));
        } catch (Exception e) {
            log.error("设置库存失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "库存设置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 设置库存请求DTO
     */
    @Data
    public static class SetStockRequest {
        private Long skuId; // 如果为null，表示商品级别库存
        private Integer stock; // 库存数量
        private Integer minStock; // 最低库存预警线
        private Integer maxStock; // 最大库存容量
    }
    
}

