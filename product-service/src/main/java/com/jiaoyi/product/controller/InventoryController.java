package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.dto.ChannelDeductBatchRequest;
import com.jiaoyi.product.dto.ChannelDeductRequest;
import com.jiaoyi.product.dto.ChannelDeductResult;
import com.jiaoyi.product.dto.PosOfflineReplayRequest;
import com.jiaoyi.product.dto.PosOfflineReplayResult;
import com.jiaoyi.product.dto.StockSyncFromPosRequest;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.InventoryOversellRecord;
import com.jiaoyi.product.entity.InventoryTransaction;
import com.jiaoyi.product.service.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 库存管理控制器（唯一扣库存入口：门店+商品+SKU 锁/扣/还）
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
    public ResponseEntity<ApiResponse<List<Inventory>>> getAllInventory(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long skuId) {
        log.info("获取库存信息，店铺ID: {}, 商品ID: {}, SKU ID: {}", storeId, productId, skuId);
        List<Inventory> inventoryList;

        if (storeId != null && productId != null && skuId != null) {
            Optional<Inventory> inventory = inventoryService.getInventoryBySkuId(storeId, productId, skuId);
            inventoryList = inventory.map(List::of).orElse(List.of());
        } else if (storeId != null && productId != null) {
            Optional<Inventory> inventory = inventoryService.getInventoryByStoreIdAndProductId(storeId, productId);
            inventoryList = inventory.map(List::of).orElse(List.of());
        } else if (storeId != null) {
            inventoryList = inventoryService.getInventoryByStoreId(storeId);
        } else if (productId != null) {
            inventoryList = inventoryService.getInventoryByProductIdWithSku(productId);
        } else {
            inventoryList = inventoryService.getAllInventory();
        }

        return ResponseEntity.ok(ApiResponse.success(inventoryList));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryById(@PathVariable Long inventoryId) {
        log.info("查询库存，库存ID: {}", inventoryId);
        try {
            List<Inventory> allInventories = inventoryService.getAllInventory();
            Inventory inventory = allInventories.stream()
                    .filter(inv -> inv.getId().equals(inventoryId))
                    .findFirst().orElse(null);
            if (inventory != null) {
                return ResponseEntity.ok(ApiResponse.success(inventory));
            } else {
                return ResponseEntity.ok(ApiResponse.error(404, "库存不存在"));
            }
        } catch (Exception e) {
            log.error("查询库存失败，库存ID: {}", inventoryId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询库存失败: " + e.getMessage()));
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryByProductId(@PathVariable Long productId) {
        Optional<Inventory> inventory = inventoryService.getInventoryByProductId(productId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "商品库存不存在"));
        }
    }

    @GetMapping("/store/{storeId}/product/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryByStoreIdAndProductId(
            @PathVariable Long storeId, @PathVariable Long productId) {
        Optional<Inventory> inventory = inventoryService.getInventoryByStoreIdAndProductId(storeId, productId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "商品库存不存在"));
        }
    }

    @GetMapping("/store/{storeId}/product/{productId}/sku/{skuId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryBySkuId(
            @PathVariable Long storeId, @PathVariable Long productId, @PathVariable Long skuId) {
        Optional<Inventory> inventory = inventoryService.getInventoryBySkuId(storeId, productId, skuId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "SKU库存不存在"));
        }
    }

    @GetMapping("/product/{productId}/skus")
    public ResponseEntity<ApiResponse<List<Inventory>>> getInventoryByProductIdWithSku(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryByProductIdWithSku(productId)));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<List<Inventory>>> getInventoryByStoreId(@PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryByStoreId(storeId)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStockItems(
            @RequestParam(required = false) Long storeId) {
        List<Inventory> lowStockItems = storeId != null
            ? inventoryService.getLowStockItemsByStoreId(storeId)
            : inventoryService.getLowStockItems();
        return ResponseEntity.ok(ApiResponse.success(lowStockItems));
    }

    @PutMapping("/update/{inventoryId}")
    public ResponseEntity<ApiResponse<Inventory>> updateInventory(
            @PathVariable Long inventoryId,
            @RequestBody UpdateInventoryRequest request) {
        log.info("【Controller】接收更新请求，库存ID: {}", inventoryId);
        try {
            Inventory inventory = inventoryService.updateInventory(
                    inventoryId, request.getStockMode(), request.getCurrentStock(),
                    request.getMinStock(), request.getMaxStock());
            return ResponseEntity.ok(ApiResponse.success("库存更新成功", inventory));
        } catch (Exception e) {
            log.error("更新库存失败，库存ID: {}", inventoryId, e);
            return ResponseEntity.ok(ApiResponse.error(400, "库存更新失败: " + e.getMessage()));
        }
    }

    @PostMapping("/create-for-sku")
    public ResponseEntity<ApiResponse<Inventory>> createInventoryForSku(
            @RequestBody CreateInventoryForSkuRequest request) {
        try {
            Inventory.StockMode stockMode = Inventory.StockMode.UNLIMITED;
            if (request.getStockMode() != null) {
                try {
                    stockMode = Inventory.StockMode.valueOf(request.getStockMode().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的库存模式: {}，使用默认值 UNLIMITED", request.getStockMode());
                }
            }
            Inventory inventory = inventoryService.createInventoryForSku(
                    request.getStoreId(), request.getProductId(), request.getSkuId(),
                    request.getProductName(), request.getSkuName(), stockMode,
                    request.getCurrentStock() != null ? request.getCurrentStock() : 0,
                    request.getMinStock() != null ? request.getMinStock() : 0,
                    request.getMaxStock());
            return ResponseEntity.ok(ApiResponse.success("库存记录创建成功", inventory));
        } catch (Exception e) {
            log.error("创建库存记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, "创建库存记录失败: " + e.getMessage()));
        }
    }

    @PostMapping("/set-stock")
    public ResponseEntity<ApiResponse<Inventory>> setStock(@RequestBody SetStockRequest request) {
        try {
            Inventory inventory = inventoryService.setStock(
                request.getStoreId(), request.getProductId(), request.getSkuId(),
                request.getStock(), request.getMinStock(), request.getMaxStock());
            return ResponseEntity.ok(ApiResponse.success("库存设置成功", inventory));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, "库存设置失败: " + e.getMessage()));
        }
    }

    // ========================= 门店/渠道库存（路径 /poi/stock/*） =========================

    @PostMapping("/poi/stock/sync-from-pos")
    public ResponseEntity<ApiResponse<Void>> poiSyncFromPos(@RequestBody StockSyncFromPosRequest request) {
        try {
            inventoryService.syncFromPos(request);
            return ResponseEntity.ok(ApiResponse.success("同步成功", null));
        } catch (Exception e) {
            log.error("POS库存同步失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/update")
    public ResponseEntity<ApiResponse<Void>> poiUpdateStock(@RequestBody StockSyncFromPosRequest request) {
        try {
            inventoryService.updateStockFromCloud(request);
            return ResponseEntity.ok(ApiResponse.success("设置成功", null));
        } catch (Exception e) {
            log.error("设置库存失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/detail")
    public ResponseEntity<ApiResponse<Inventory>> poiStockDetail(
            @RequestParam Long storeId, @RequestParam Long productId,
            @RequestParam(required = false) Long skuId) {
        try {
            Inventory inventory = inventoryService.getInventoryWithChannels(storeId, productId, skuId);
            if (inventory == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "库存不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(inventory));
        } catch (Exception e) {
            log.error("查询库存详情失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/log")
    public ResponseEntity<ApiResponse<List<InventoryTransaction>>> poiStockLog(
            @RequestParam Long inventoryId,
            @RequestParam(defaultValue = "100") Integer limit) {
        try {
            List<InventoryTransaction> logs = inventoryService.getInventoryTransactionLogs(inventoryId, limit);
            return ResponseEntity.ok(ApiResponse.success(logs));
        } catch (Exception e) {
            log.error("查询库存变动记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/replay-offline")
    public ResponseEntity<ApiResponse<PosOfflineReplayResult>> poiReplayOffline(
            @RequestBody PosOfflineReplayRequest request) {
        try {
            PosOfflineReplayResult result = inventoryService.replayOfflineEvents(request);
            return ResponseEntity.ok(ApiResponse.success(
                result.isOversellDetected()
                    ? "回放完成，检测到超卖（" + result.getOversellQuantity() + "份），请店长确认"
                    : "回放完成", result));
        } catch (Exception e) {
            log.error("POS离线事件回放失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/deduct-by-channel")
    public ResponseEntity<ApiResponse<ChannelDeductResult>> poiDeductByChannel(
            @RequestBody ChannelDeductRequest request) {
        try {
            ChannelDeductResult result = inventoryService.deductByChannel(request);
            return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
        } catch (Exception e) {
            log.error("渠道库存扣减失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/deduct-by-channel/batch")
    public ResponseEntity<ApiResponse<Void>> poiDeductByChannelBatch(
            @RequestBody ChannelDeductBatchRequest request) {
        try {
            inventoryService.deductByChannelBatch(request);
            return ResponseEntity.ok(ApiResponse.success("扣减成功", null));
        } catch (Exception e) {
            log.error("渠道批量扣减失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/allocate-channel-quotas")
    public ResponseEntity<ApiResponse<Void>> poiAllocateChannelQuotas(
            @RequestParam Long storeId, @RequestParam Long productId,
            @RequestParam(required = false) Long skuId) {
        try {
            inventoryService.allocateChannelQuotas(storeId, productId, skuId);
            return ResponseEntity.ok(ApiResponse.success("渠道额度分配成功", null));
        } catch (Exception e) {
            log.error("渠道额度分配失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PutMapping("/poi/stock/allocation-mode")
    public ResponseEntity<ApiResponse<Void>> poiSetAllocationMode(@RequestBody SetAllocationModeRequest request) {
        try {
            inventoryService.setAllocationMode(
                request.getStoreId(), request.getProductId(), request.getSkuId(),
                request.getAllocationMode());
            return ResponseEntity.ok(ApiResponse.success("分配模式已更新", null));
        } catch (Exception e) {
            log.error("设置分配模式失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/poi/stock/channel-safety")
    public ResponseEntity<ApiResponse<Void>> poiUpdateChannelSafety(
            @RequestBody UpdateChannelSafetyRequest request) {
        try {
            inventoryService.updateChannelPriorityAndSafetyStock(
                request.getChannelId(), request.getChannelPriority(), request.getSafetyStock());
            return ResponseEntity.ok(ApiResponse.success("渠道安全线配置已更新", null));
        } catch (Exception e) {
            log.error("更新渠道安全线配置失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/return-by-order")
    public ResponseEntity<ApiResponse<Void>> poiReturnByOrder(@RequestParam String orderId) {
        try {
            inventoryService.returnStockByOrderId(orderId);
            return ResponseEntity.ok(ApiResponse.success("归还处理完成", null));
        } catch (Exception e) {
            log.error("按订单归还库存失败 orderId={}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @GetMapping("/poi/stock/oversell-records")
    public ResponseEntity<ApiResponse<List<InventoryOversellRecord>>> poiOversellRecords(
            @RequestParam Long storeId,
            @RequestParam(required = false) String status) {
        try {
            List<InventoryOversellRecord> records = inventoryService.getOversellRecords(storeId, status);
            return ResponseEntity.ok(ApiResponse.success(records));
        } catch (Exception e) {
            log.error("查询超卖记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/resolve-oversell")
    public ResponseEntity<ApiResponse<Void>> poiResolveOversell(
            @RequestParam Long recordId,
            @RequestParam String status,
            @RequestParam(required = false) String resolvedBy,
            @RequestParam(required = false) String remark) {
        try {
            inventoryService.resolveOversellRecord(recordId, status, resolvedBy, remark);
            return ResponseEntity.ok(ApiResponse.success("超卖记录已处理", null));
        } catch (Exception e) {
            log.error("处理超卖记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    // ========================= 内部请求DTO =========================

    @Data
    public static class CheckStockRequest {
        private Long productId;
        private Long skuId;
        private Integer quantity;
    }

    @Data
    public static class SetStockRequest {
        private Long storeId;
        private Long productId;
        private Long skuId;
        private Integer stock;
        private Integer minStock;
        private Integer maxStock;
    }

    @Data
    public static class SetAllocationModeRequest {
        private Long storeId;
        private Long productId;
        private Long skuId;
        private String allocationMode;
    }

    @Data
    public static class UpdateChannelSafetyRequest {
        private Long channelId;
        private Integer channelPriority;
        private BigDecimal safetyStock;
    }

    @Data
    public static class UpdateInventoryRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("stockMode")
        private String stockMode;

        @com.fasterxml.jackson.annotation.JsonProperty("currentStock")
        private Integer currentStock;

        @com.fasterxml.jackson.annotation.JsonProperty("minStock")
        private Integer minStock;

        @com.fasterxml.jackson.annotation.JsonProperty("maxStock")
        private Integer maxStock;
    }

    static class CreateInventoryForSkuRequest {
        private Long storeId;
        private Long productId;
        private Long skuId;
        private String productName;
        private String skuName;
        private String stockMode;
        private Integer currentStock;
        private Integer minStock;
        private Integer maxStock;

        public Long getStoreId() { return storeId; }
        public void setStoreId(Long storeId) { this.storeId = storeId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSkuName() { return skuName; }
        public void setSkuName(String skuName) { this.skuName = skuName; }
        public String getStockMode() { return stockMode; }
        public void setStockMode(String stockMode) { this.stockMode = stockMode; }
        public Integer getCurrentStock() { return currentStock; }
        public void setCurrentStock(Integer currentStock) { this.currentStock = currentStock; }
        public Integer getMinStock() { return minStock; }
        public void setMinStock(Integer minStock) { this.minStock = minStock; }
        public Integer getMaxStock() { return maxStock; }
        public void setMaxStock(Integer maxStock) { this.maxStock = maxStock; }
    }
}
