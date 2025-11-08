package com.jiaoyi.controller;

import com.jiaoyi.entity.Inventory;
import com.jiaoyi.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 库存缓存管理控制器
 * 提供库存缓存的查询和管理功能
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory-cache")
@RequiredArgsConstructor
public class InventoryCacheController {

    private final InventoryService inventoryService;

    /**
     * 根据商品ID查询库存（优先从缓存）
     */
    @GetMapping("/product/{productId}")
    public Map<String, Object> getInventoryByProductId(@PathVariable Long productId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<Inventory> inventory = inventoryService.getInventoryByProductId(productId);
            if (inventory.isPresent()) {
                result.put("success", true);
                result.put("data", inventory.get());
                result.put("fromCache", inventoryService.hasInventoryCache(productId));
                result.put("cacheExpireTime", inventoryService.getInventoryCacheExpireTime(productId));
            } else {
                result.put("success", false);
                result.put("message", "商品不存在");
            }
        } catch (Exception e) {
            log.error("查询库存失败，商品ID: {}", productId, e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 查询库存不足的商品（优先从缓存）
     */
    @GetMapping("/low-stock")
    public Map<String, Object> getLowStockItems() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Inventory> lowStockItems = inventoryService.getLowStockItems();
            result.put("success", true);
            result.put("data", lowStockItems);
            result.put("count", lowStockItems.size());
        } catch (Exception e) {
            log.error("查询库存不足商品失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 刷新指定商品的库存缓存
     */
    @PostMapping("/refresh/{productId}")
    public Map<String, Object> refreshInventoryCache(@PathVariable Long productId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            inventoryService.refreshInventoryCache(productId);
            result.put("success", true);
            result.put("message", "库存缓存刷新成功");
        } catch (Exception e) {
            log.error("刷新库存缓存失败，商品ID: {}", productId, e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 刷新库存不足商品列表缓存
     */
    @PostMapping("/refresh/low-stock")
    public Map<String, Object> refreshLowStockItemsCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            inventoryService.refreshLowStockItemsCache();
            result.put("success", true);
            result.put("message", "库存不足商品列表缓存刷新成功");
        } catch (Exception e) {
            log.error("刷新库存不足商品列表缓存失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 清空所有库存缓存
     */
    @PostMapping("/clear")
    public Map<String, Object> clearAllInventoryCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            inventoryService.clearAllInventoryCache();
            result.put("success", true);
            result.put("message", "所有库存缓存已清空");
        } catch (Exception e) {
            log.error("清空库存缓存失败", e);
            result.put("success", false);
            result.put("message", "清空失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 检查库存缓存状态
     */
    @GetMapping("/status/{productId}")
    public Map<String, Object> getCacheStatus(@PathVariable Long productId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean hasCache = inventoryService.hasInventoryCache(productId);
            Long expireTime = inventoryService.getInventoryCacheExpireTime(productId);
            
            result.put("success", true);
            result.put("productId", productId);
            result.put("hasCache", hasCache);
            result.put("expireTime", expireTime);
            result.put("expireTimeText", expireTime > 0 ? expireTime + "秒" : "已过期");
        } catch (Exception e) {
            log.error("检查缓存状态失败，商品ID: {}", productId, e);
            result.put("success", false);
            result.put("message", "检查失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getCacheStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 这里可以添加更详细的缓存统计信息
            result.put("success", true);
            result.put("message", "缓存统计功能待实现");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            log.error("获取缓存统计失败", e);
            result.put("success", false);
            result.put("message", "获取统计失败: " + e.getMessage());
        }
        
        return result;
    }
}


