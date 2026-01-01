package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.entity.DoorDashRetryTask;
import com.jiaoyi.order.mapper.DoorDashRetryTaskMapper;
import com.jiaoyi.order.service.DoorDashRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DoorDash 重试任务管理 Controller
 * 提供人工介入接口
 */
@RestController
@RequestMapping("/api/doordash-retry")
@RequiredArgsConstructor
@Slf4j
public class DoorDashRetryController {
    
    private final DoorDashRetryTaskMapper retryTaskMapper;
    private final DoorDashRetryService retryService;
    
    /**
     * 查询需要人工介入的任务列表
     */
    @GetMapping("/manual-intervention")
    public ResponseEntity<ApiResponse<List<DoorDashRetryTask>>> getManualInterventionTasks(
            @RequestParam(required = false) String merchantId,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        
        log.info("查询需要人工介入的任务，merchantId: {}, offset: {}, limit: {}", merchantId, offset, limit);
        
        try {
            List<DoorDashRetryTask> tasks = retryTaskMapper.selectManualInterventionTasks(merchantId, offset, limit);
            return ResponseEntity.ok(ApiResponse.success("查询成功", tasks));
        } catch (Exception e) {
            log.error("查询需要人工介入的任务失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据订单ID查询重试任务
     */
    @GetMapping("/order/{merchantId}/{orderId}")
    public ResponseEntity<ApiResponse<DoorDashRetryTask>> getRetryTaskByOrderId(
            @PathVariable String merchantId,
            @PathVariable Long orderId) {
        
        log.info("查询重试任务，merchantId: {}, orderId: {}", merchantId, orderId);
        
        try {
            DoorDashRetryTask task = retryTaskMapper.selectByMerchantIdAndOrderId(merchantId, orderId);
            if (task == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "重试任务不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success("查询成功", task));
        } catch (Exception e) {
            log.error("查询重试任务失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 手动触发重试
     */
    @PostMapping("/{taskId}/retry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manualRetry(@PathVariable Long taskId) {
        log.info("手动触发重试，任务ID: {}", taskId);
        
        try {
            boolean success = retryService.manualRetry(taskId);
            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("taskId", taskId);
                result.put("message", "重试已触发");
                return ResponseEntity.ok(ApiResponse.success("重试已触发", result));
            } else {
                return ResponseEntity.ok(ApiResponse.error(400, "重试失败：任务不存在或状态不正确"));
            }
        } catch (Exception e) {
            log.error("手动触发重试失败，任务ID: {}", taskId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "重试失败: " + e.getMessage()));
        }
    }
    
    /**
     * 标记任务为需要人工介入
     */
    @PostMapping("/{taskId}/manual-intervention")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAsManualIntervention(
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> request) {
        
        log.info("标记任务为需要人工介入，任务ID: {}", taskId);
        
        try {
            String note = request != null ? request.get("note") : null;
            
            retryTaskMapper.updateManualIntervention(
                    taskId,
                    java.time.LocalDateTime.now(),
                    note != null ? note : "标记为需要人工介入"
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("message", "已标记为需要人工介入");
            return ResponseEntity.ok(ApiResponse.success("已标记为需要人工介入", result));
        } catch (Exception e) {
            log.error("标记任务为需要人工介入失败，任务ID: {}", taskId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "操作失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取任务统计信息（优化：直接在 SQL 中统计，避免查询所有数据）
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @RequestParam(required = false) String merchantId) {
        
        log.info("获取任务统计信息，merchantId: {}", merchantId);
        
        try {
            // 直接在 SQL 中统计各状态的任务数量
            List<Map<String, Object>> statusCounts = retryTaskMapper.countByStatus(merchantId);
            
            Map<String, Object> stats = new HashMap<>();
            long total = 0;
            
            // 初始化所有状态为 0
            stats.put("pending", 0L);
            stats.put("retrying", 0L);
            stats.put("success", 0L);
            stats.put("failed", 0L);
            stats.put("manual", 0L);
            
            // 填充统计数据
            for (Map<String, Object> statusCount : statusCounts) {
                String status = (String) statusCount.get("status");
                Long count = ((Number) statusCount.get("count")).longValue();
                total += count;
                
                switch (status) {
                    case "PENDING":
                        stats.put("pending", count);
                        break;
                    case "RETRYING":
                        stats.put("retrying", count);
                        break;
                    case "SUCCESS":
                        stats.put("success", count);
                        break;
                    case "FAILED":
                        stats.put("failed", count);
                        break;
                    case "MANUAL":
                        stats.put("manual", count);
                        break;
                }
            }
            
            stats.put("total", total);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", stats));
        } catch (Exception e) {
            log.error("获取任务统计信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
}

