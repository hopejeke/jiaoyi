package com.jiaoyi.order.controller;

import com.jiaoyi.order.config.RouteCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由缓存管理 Controller
 * 提供路由缓存的查询和刷新接口（用于扩容时主动刷新）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/route-cache")
@RequiredArgsConstructor
public class RouteCacheController {
    
    private final RouteCache routeCache;
    
    /**
     * 获取路由缓存状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("initialized", routeCache.isInitialized());
        result.put("lastUpdateTime", routeCache.getLastUpdateTime());
        result.put("routeCount", routeCache.isInitialized() ? routeCache.getAllRoutes().size() : 0);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 强制刷新路由缓存（用于扩容时立即刷新，避免等待定时任务）
     * 
     * 使用场景：
     * 1. 扩容时修改了 shard_bucket_route 表后，调用此接口立即刷新所有实例的缓存
     * 2. 可以通过脚本批量调用所有实例的此接口，确保缓存一致性
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> forceRefresh() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = routeCache.forceRefresh();
            result.put("success", success);
            result.put("message", success ? "路由缓存刷新成功" : "路由缓存刷新失败");
            result.put("lastUpdateTime", routeCache.getLastUpdateTime());
            result.put("routeCount", routeCache.getAllRoutes().size());
            
            if (success) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            log.error("强制刷新路由缓存失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 获取所有路由映射（用于调试和验证）
     */
    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> getAllRoutes() {
        Map<String, Object> result = new HashMap<>();
        result.put("routes", routeCache.getAllRoutes());
        result.put("count", routeCache.getAllRoutes().size());
        result.put("lastUpdateTime", routeCache.getLastUpdateTime());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 查询指定 shard_id 的路由（用于调试）
     */
    @GetMapping("/route/{shardId}")
    public ResponseEntity<Map<String, Object>> getRoute(@PathVariable int shardId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String dsName = routeCache.getDataSourceName(shardId);
            String status = routeCache.getStatus(shardId);
            
            result.put("shardId", shardId);
            result.put("dsName", dsName);
            result.put("status", status);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(400).body(result);
        }
    }
}

