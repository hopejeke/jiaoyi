package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.Inventory;
import com.jiaoyi.service.InventoryService;
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
     * 根据商品ID查询库存
     */
    @GetMapping("/product/{productId}")
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
    @PostMapping("/check-stock")
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
     * 检查库存请求DTO
     */
    public static class CheckStockRequest {
        private Long productId;
        private Integer quantity;
        
        public Long getProductId() {
            return productId;
        }
        
        public void setProductId(Long productId) {
            this.productId = productId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
