package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.dto.ChannelDeductBatchRequest;
import com.jiaoyi.product.dto.ChannelDeductRequest;
import com.jiaoyi.product.dto.ChannelDeductResult;
import com.jiaoyi.product.dto.PosOfflineReplayRequest;
import com.jiaoyi.product.dto.PosOfflineReplayResult;
import com.jiaoyi.product.dto.StockSyncFromPosRequest;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.OversellRecord;
import com.jiaoyi.product.entity.PoiItemStock;
import com.jiaoyi.product.entity.PoiItemStockLog;
import com.jiaoyi.product.service.InventoryService;
import com.jiaoyi.product.service.PoiItemStockService;
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
    private final PoiItemStockService poiItemStockService;

    /**
     * 获取所有库存信息（兼容前端 /api/inventory 接口）
     * 注意：如果数据量大，建议使用分页查询
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Inventory>>> getAllInventory(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long skuId) {
        log.info("获取库存信息，店铺ID: {}, 商品ID: {}, SKU ID: {}", storeId, productId, skuId);
        List<Inventory> inventoryList;
        
        if (storeId != null && productId != null && skuId != null) {
            // 查询指定店铺、商品、SKU的库存
            Optional<Inventory> inventory = inventoryService.getInventoryBySkuId(storeId, productId, skuId);
            inventoryList = inventory.map(List::of).orElse(List.of());
        } else if (storeId != null && productId != null) {
            // 查询指定店铺、商品的库存（商品级别或所有SKU）
            if (skuId == null) {
                Optional<Inventory> inventory = inventoryService.getInventoryByStoreIdAndProductId(storeId, productId);
                inventoryList = inventory.map(List::of).orElse(List.of());
            } else {
                Optional<Inventory> inventory = inventoryService.getInventoryBySkuId(storeId, productId, skuId);
                inventoryList = inventory.map(List::of).orElse(List.of());
            }
        } else if (storeId != null) {
            // 查询指定店铺的所有库存
            inventoryList = inventoryService.getInventoryByStoreId(storeId);
        } else if (productId != null) {
            // 查询指定商品的所有SKU库存
            inventoryList = inventoryService.getInventoryByProductIdWithSku(productId);
        } else {
            // 查询所有库存
            inventoryList = inventoryService.getAllInventory();
        }
        
        return ResponseEntity.ok(ApiResponse.success(inventoryList));
    }
    
    /**
     * 根据库存ID查询库存
     * 注意：由于 inventory 表是分片表（基于 store_id），selectById 没有分片键可能查询不到
     * 这里通过 selectAll 遍历查询（性能较差，但能保证正确性）
     * 更好的方案：前端传递 store_id 和 product_id，使用有分片键的查询
     */
    @GetMapping("/{inventoryId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryById(@PathVariable Long inventoryId) {
        log.info("查询库存，库存ID: {}", inventoryId);
        try {
            // 由于分片表需要分片键，selectById 可能无法正确路由
            // 临时方案：通过 selectAll 查询所有分片（性能较差）
            List<Inventory> allInventories = inventoryService.getAllInventory();
            Inventory inventory = allInventories.stream()
                    .filter(inv -> inv.getId().equals(inventoryId))
                    .findFirst()
                    .orElse(null);
            
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
    
    /**
     * 根据商品ID查询库存（商品级别，兼容旧接口）
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
     * 根据店铺ID和商品ID查询库存
     */
    @GetMapping("/store/{storeId}/product/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryByStoreIdAndProductId(
            @PathVariable Long storeId,
            @PathVariable Long productId) {
        log.info("查询商品库存，店铺ID: {}, 商品ID: {}", storeId, productId);
        Optional<Inventory> inventory = inventoryService.getInventoryByStoreIdAndProductId(storeId, productId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "商品库存不存在"));
        }
    }
    
    /**
     * 根据店铺ID、商品ID和SKU ID查询库存
     */
    @GetMapping("/store/{storeId}/product/{productId}/sku/{skuId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventoryBySkuId(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @PathVariable Long skuId) {
        log.info("查询SKU库存，店铺ID: {}, 商品ID: {}, SKU ID: {}", storeId, productId, skuId);
        Optional<Inventory> inventory = inventoryService.getInventoryBySkuId(storeId, productId, skuId);
        if (inventory.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(inventory.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "SKU库存不存在"));
        }
    }
    
    /**
     * 根据商品ID查询所有SKU库存
     */
    @GetMapping("/product/{productId}/skus")
    public ResponseEntity<ApiResponse<List<Inventory>>> getInventoryByProductIdWithSku(@PathVariable Long productId) {
        log.info("查询商品所有SKU库存，商品ID: {}", productId);
        List<Inventory> inventoryList = inventoryService.getInventoryByProductIdWithSku(productId);
        return ResponseEntity.ok(ApiResponse.success(inventoryList));
    }
    
    /**
     * 根据店铺ID查询所有库存
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<List<Inventory>>> getInventoryByStoreId(@PathVariable Long storeId) {
        log.info("查询店铺所有库存，店铺ID: {}", storeId);
        List<Inventory> inventoryList = inventoryService.getInventoryByStoreId(storeId);
        return ResponseEntity.ok(ApiResponse.success(inventoryList));
    }
    
    /**
     * 查询库存不足的商品
     */
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStockItems(
            @RequestParam(required = false) Long storeId) {
        log.info("查询库存不足的商品，店铺ID: {}", storeId);
        List<Inventory> lowStockItems;
        if (storeId != null) {
            lowStockItems = inventoryService.getLowStockItemsByStoreId(storeId);
        } else {
            lowStockItems = inventoryService.getLowStockItems();
        }
        return ResponseEntity.ok(ApiResponse.success(lowStockItems));
    }
    
    /**
     * 更新库存信息（包括库存数量、最低库存预警线、最大库存容量等）
     */
    @PutMapping("/update/{inventoryId}")
    public ResponseEntity<ApiResponse<Inventory>> updateInventory(
            @PathVariable Long inventoryId,
            @RequestBody UpdateInventoryRequest request) {
        log.info("【Controller】接收更新请求，库存ID: {}, stockMode: '{}' (null: {}), currentStock: {}, minStock: {}, maxStock: {}", 
                inventoryId, request.getStockMode(), request.getStockMode() == null, 
                request.getCurrentStock(), request.getMinStock(), request.getMaxStock());
        try {
            Inventory inventory = inventoryService.updateInventory(
                    inventoryId,
                    request.getStockMode(),
                    request.getCurrentStock(),
                    request.getMinStock(),
                    request.getMaxStock()
            );
            return ResponseEntity.ok(ApiResponse.success("库存更新成功", inventory));
        } catch (Exception e) {
            log.error("更新库存失败，库存ID: {}", inventoryId, e);
            return ResponseEntity.ok(ApiResponse.error(400, "库存更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 为SKU创建库存记录（如果不存在）
     * 用于修复旧数据：为已存在但没有库存记录的SKU创建库存记录
     */
    @PostMapping("/create-for-sku")
    public ResponseEntity<ApiResponse<Inventory>> createInventoryForSku(
            @RequestBody CreateInventoryForSkuRequest request) {
        log.info("为SKU创建库存记录，店铺ID: {}, 商品ID: {}, SKU ID: {}, 库存模式: {}", 
                request.getStoreId(), request.getProductId(), request.getSkuId(), request.getStockMode());
        try {
            // 转换 stockMode 字符串为枚举
            Inventory.StockMode stockMode = Inventory.StockMode.UNLIMITED;
            if (request.getStockMode() != null) {
                try {
                    stockMode = Inventory.StockMode.valueOf(request.getStockMode().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的库存模式: {}，使用默认值 UNLIMITED", request.getStockMode());
                }
            }
            
            Inventory inventory = inventoryService.createInventoryForSku(
                    request.getStoreId(),
                    request.getProductId(),
                    request.getSkuId(),
                    request.getProductName(),
                    request.getSkuName(),
                    stockMode,
                    request.getCurrentStock() != null ? request.getCurrentStock() : 0,
                    request.getMinStock() != null ? request.getMinStock() : 0,
                    request.getMaxStock()
            );
            return ResponseEntity.ok(ApiResponse.success("库存记录创建成功", inventory));
        } catch (Exception e) {
            log.error("创建库存记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, "创建库存记录失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建库存记录请求
     */
    static class CreateInventoryForSkuRequest {
        private Long storeId;
        private Long productId;
        private Long skuId;
        private String productName;
        private String skuName;
        private String stockMode; // 库存模式：UNLIMITED 或 LIMITED
        private Integer currentStock; // 当前库存数量
        private Integer minStock; // 最低库存预警线
        private Integer maxStock; // 最大库存容量
        
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

    // ========================= 门店/渠道库存（原 PoiItemStock，路径 /poi/stock/*） =========================

    @PostMapping("/poi/stock/sync-from-pos")
    public ResponseEntity<ApiResponse<Void>> poiSyncFromPos(@RequestBody StockSyncFromPosRequest request) {
        try {
            poiItemStockService.syncFromPos(request);
            return ResponseEntity.ok(ApiResponse.success("同步成功", null));
        } catch (Exception e) {
            log.error("POS库存同步失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/update")
    public ResponseEntity<ApiResponse<Void>> poiUpdateStock(@RequestBody StockSyncFromPosRequest request) {
        try {
            poiItemStockService.updateStock(request);
            return ResponseEntity.ok(ApiResponse.success("设置成功", null));
        } catch (Exception e) {
            log.error("设置库存失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/detail")
    public ResponseEntity<ApiResponse<PoiItemStock>> poiStockDetail(
            @RequestParam String brandId, @RequestParam String storeId, @RequestParam Long objectId) {
        try {
            PoiItemStock stock = poiItemStockService.getStockDetail(brandId, storeId, objectId);
            if (stock == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "库存不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(stock));
        } catch (Exception e) {
            log.error("查询库存详情失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/log")
    public ResponseEntity<ApiResponse<List<PoiItemStockLog>>> poiStockLog(
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) Long stockId,
            @RequestParam(defaultValue = "100") Integer limit) {
        try {
            List<PoiItemStockLog> logs = poiItemStockService.getStockLogs(brandId, storeId, stockId, limit);
            return ResponseEntity.ok(ApiResponse.success(logs));
        } catch (Exception e) {
            log.error("查询库存变动记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/replay-offline")
    public ResponseEntity<ApiResponse<PosOfflineReplayResult>> poiReplayOffline(@RequestBody PosOfflineReplayRequest request) {
        try {
            PosOfflineReplayResult result = poiItemStockService.replayOfflineEvents(request);
            return ResponseEntity.ok(ApiResponse.success(result.isOversellDetected()
                    ? "回放完成，检测到超卖（" + result.getOversellQuantity() + "份），请店长确认" : "回放完成", result));
        } catch (Exception e) {
            log.error("POS离线事件回放失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    /** 按渠道扣库存（单品） */
    @PostMapping("/poi/stock/deduct-by-channel")
    public ResponseEntity<ApiResponse<ChannelDeductResult>> poiDeductByChannel(@RequestBody ChannelDeductRequest request) {
        try {
            ChannelDeductResult result = poiItemStockService.deductByChannel(request);
            return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
        } catch (Exception e) {
            log.error("渠道库存扣减失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** 按渠道批量扣减（订单多品，下单时调） */
    @PostMapping("/poi/stock/deduct-by-channel/batch")
    public ResponseEntity<ApiResponse<Void>> poiDeductByChannelBatch(@RequestBody ChannelDeductBatchRequest request) {
        try {
            poiItemStockService.deductByChannelBatch(request);
            return ResponseEntity.ok(ApiResponse.success("扣减成功", null));
        } catch (Exception e) {
            log.error("渠道批量扣减失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/poi/stock/allocate-channel-quotas")
    public ResponseEntity<ApiResponse<Void>> poiAllocateChannelQuotas(
            @RequestParam String brandId, @RequestParam String storeId, @RequestParam Long objectId) {
        try {
            poiItemStockService.allocateChannelQuotas(brandId, storeId, objectId);
            return ResponseEntity.ok(ApiResponse.success("渠道额度分配成功", null));
        } catch (Exception e) {
            log.error("渠道额度分配失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    /** 设置渠道库存分配模式：WEIGHTED_QUOTA（加权配额）| SAFETY_STOCK（安全线保护） */
    @PutMapping("/poi/stock/allocation-mode")
    public ResponseEntity<ApiResponse<Void>> poiSetAllocationMode(@RequestBody SetAllocationModeRequest request) {
        try {
            poiItemStockService.setAllocationMode(
                request.getBrandId(), request.getStoreId(), request.getObjectId(),
                request.getAllocationMode());
            return ResponseEntity.ok(ApiResponse.success("分配模式已更新", null));
        } catch (Exception e) {
            log.error("设置分配模式失败", e);
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** 更新渠道优先级与安全线（方案二 SAFETY_STOCK 时使用，运营后台配置） */
    @PutMapping("/poi/stock/channel-safety")
    public ResponseEntity<ApiResponse<Void>> poiUpdateChannelSafety(@RequestBody UpdateChannelSafetyRequest request) {
        try {
            poiItemStockService.updateChannelPriorityAndSafetyStock(
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
            poiItemStockService.returnStockByOrderId(orderId);
            return ResponseEntity.ok(ApiResponse.success("归还处理完成", null));
        } catch (Exception e) {
            log.error("按订单归还库存失败 orderId={}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    @GetMapping("/poi/stock/oversell-records")
    public ResponseEntity<ApiResponse<List<OversellRecord>>> poiOversellRecords(
            @RequestParam String brandId,
            @RequestParam String storeId,
            @RequestParam(required = false) String status) {
        try {
            List<OversellRecord> records = poiItemStockService.getOversellRecords(brandId, storeId, status);
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
            poiItemStockService.resolveOversellRecord(recordId, status, resolvedBy, remark);
            return ResponseEntity.ok(ApiResponse.success("超卖记录已处理", null));
        } catch (Exception e) {
            log.error("处理超卖记录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    /**
     * 检查库存请求DTO（基于SKU）
     */
    @Data
    public static class CheckStockRequest {
        private Long productId;
        private Long skuId;
        private Integer quantity;
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
    
    /**
     * 设置分配模式请求：WEIGHTED_QUOTA | SAFETY_STOCK
     */
    @Data
    public static class SetAllocationModeRequest {
        private String brandId;
        private String storeId;
        private Long objectId;
        private String allocationMode;
    }

    /**
     * 更新渠道优先级与安全线请求（方案二用）
     */
    @Data
    public static class UpdateChannelSafetyRequest {
        private Long channelId;
        private Integer channelPriority;
        private BigDecimal safetyStock;
    }

    /**
     * 更新库存请求DTO
     */
    @Data
    public static class UpdateInventoryRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("stockMode")
        private String stockMode; // 库存模式：UNLIMITED 或 LIMITED
        
        @com.fasterxml.jackson.annotation.JsonProperty("currentStock")
        private Integer currentStock; // 当前库存数量
        
        @com.fasterxml.jackson.annotation.JsonProperty("minStock")
        private Integer minStock; // 最低库存预警线
        
        @com.fasterxml.jackson.annotation.JsonProperty("maxStock")
        private Integer maxStock; // 最大库存容量
    }
}
