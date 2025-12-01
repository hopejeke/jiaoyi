package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.service.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 库存管理控制器
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {
    
    private final InventoryService inventoryService;
    
    /**
     * 获取所有库存信息（兼容前端 /api/inventory 接口）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Inventory>>> getAllInventory() {
        log.info("获取所有库存信息（兼容前端 /api/inventory 接口）");
        // 这里需要实现获取所有库存的逻辑
        // 暂时返回空列表，或者可以通过其他方式实现
        // 注意：如果数据量大，建议使用分页查询
        List<Inventory> allInventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(ApiResponse.success(allInventory));
    }
    
    /**
     * 根据商品ID查询库存
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryByProductId(@PathVariable Long productId) {
        log.info("查询商品库存，商品ID: {}", productId);
        Optional<Inventory> inventory = inventoryService.getInventoryByProductId(productId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "商品库存不存在"));
        }
    }
    
    /**
     * 查询库存不足的商品
     */
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStockItems() {
        log.info("查询库存不足的商品");
        List<Inventory> lowStockItems = inventoryService.getLowStockItems();
        return ResponseEntity.ok(ApiResponse.success(lowStockItems));
    }
    
    /**
     * 检查库存是否充足
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<Boolean>> checkStock(@RequestBody CheckStockRequest request) {
        log.info("检查库存，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());
        try {
            inventoryService.checkAndLockStock(request.getProductId(), request.getQuantity());
            return ResponseEntity.ok(ApiResponse.success(true));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 锁定库存
     */
    @PostMapping("/{productId}/lock")
    public ResponseEntity<ApiResponse<Void>> lockStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        log.info("锁定库存，商品ID: {}, 数量: {}", productId, quantity);
        try {
            inventoryService.checkAndLockStock(productId, quantity);
            return ResponseEntity.ok(ApiResponse.success("锁定成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 解锁库存
     */
    @PostMapping("/{productId}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        log.info("解锁库存，商品ID: {}, 数量: {}", productId, quantity);
        try {
            inventoryService.unlockStock(productId, quantity, null);
            return ResponseEntity.ok(ApiResponse.success("解锁成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 扣减库存
     */
    @PostMapping("/{productId}/deduct")
    public ResponseEntity<ApiResponse<Void>> deductStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        log.info("扣减库存，商品ID: {}, 数量: {}", productId, quantity);
        try {
            inventoryService.deductStock(productId, quantity, null);
            return ResponseEntity.ok(ApiResponse.success("扣减成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 批量锁定库存
     */
    @PostMapping("/lock/batch")
    public ResponseEntity<ApiResponse<Void>> lockStockBatch(@RequestBody LockStockBatchRequest request) {
        log.info("批量锁定库存，商品数量: {}", request.getProductIds().size());
        try {
            inventoryService.checkAndLockStockBatch(request.getProductIds(), request.getQuantities());
            return ResponseEntity.ok(ApiResponse.success("批量锁定成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 批量解锁库存
     */
    @PostMapping("/unlock/batch")
    public ResponseEntity<ApiResponse<Void>> unlockStockBatch(@RequestBody UnlockStockBatchRequest request) {
        log.info("批量解锁库存，订单ID: {}, 商品数量: {}", request.getOrderId(), request.getProductIds().size());
        try {
            inventoryService.unlockStockBatch(request.getProductIds(), request.getQuantities(), request.getOrderId());
            return ResponseEntity.ok(ApiResponse.success("批量解锁成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 批量扣减库存
     */
    @PostMapping("/deduct/batch")
    public ResponseEntity<ApiResponse<Void>> deductStockBatch(@RequestBody DeductStockBatchRequest request) {
        log.info("批量扣减库存，订单ID: {}, 商品数量: {}", request.getOrderId(), request.getProductIds().size());
        try {
            inventoryService.deductStockBatch(request.getProductIds(), request.getQuantities(), request.getOrderId());
            return ResponseEntity.ok(ApiResponse.success("批量扣减成功", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 设置库存数量（商品级别或SKU级别）
     */
    @PostMapping("/set-stock")
    public ResponseEntity<ApiResponse<Inventory>> setStock(@RequestBody SetStockRequest request) {
        log.info("设置库存数量，店铺ID: {}, 商品ID: {}, SKU ID: {}, 库存: {}", 
                request.getStoreId(), request.getProductId(), request.getSkuId(), request.getStock());
        try {
            Inventory inventory = inventoryService.setStock(
                request.getStoreId(),
                request.getProductId(),
                request.getSkuId(),
                request.getStock(),
                request.getMinStock(),
                request.getMaxStock()
            );
            return ResponseEntity.ok(ApiResponse.success("库存设置成功", inventory));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, "库存设置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 检查库存请求DTO
     */
    @Data
    public static class CheckStockRequest {
        private Long productId;
        private Integer quantity;
    }
    
    /**
     * 批量锁定库存请求DTO
     */
    @Data
    public static class LockStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
    }
    
    /**
     * 批量解锁库存请求DTO
     */
    @Data
    public static class UnlockStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
        private Long orderId;
    }
    
    /**
     * 批量扣减库存请求DTO
     */
    @Data
    public static class DeductStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
        private Long orderId;
    }
    
    /**
     * 设置库存请求DTO
     */
    @Data
    public static class SetStockRequest {
        private Long storeId;
        private Long productId;
        private Long skuId; // 如果为null，表示商品级别库存
        private Integer stock; // 库存数量
        private Integer minStock; // 最低库存预警线
        private Integer maxStock; // 最大库存容量
    }
}
