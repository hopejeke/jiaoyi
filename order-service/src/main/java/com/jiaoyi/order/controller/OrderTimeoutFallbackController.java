package com.jiaoyi.order.controller;

import com.jiaoyi.order.service.OrderTimeoutFallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单超时兜底任务管理控制器
 * 用于测试和监控兜底任务
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/order-timeout-fallback")
@RequiredArgsConstructor
public class OrderTimeoutFallbackController {

    private final OrderTimeoutFallbackService orderTimeoutFallbackService;

    /**
     * 手动触发兜底检查
     */
    @PostMapping("/manual-check")
    public String manualCheck() {
        log.info("收到手动触发兜底检查请求");
        try {
            orderTimeoutFallbackService.manualCheck();
            return "兜底检查已触发，请查看日志";
        } catch (Exception e) {
            log.error("手动触发兜底检查失败", e);
            return "兜底检查触发失败: " + e.getMessage();
        }
    }

    /**
     * 获取兜底任务状态
     */
    @GetMapping("/status")
    public String getStatus() {
        return orderTimeoutFallbackService.getFallbackStatus();
    }

    /**
     * 处理单个超时订单（用于测试）
     */
    @PostMapping("/process-order/{orderId}")
    public String processOrder(@PathVariable Long orderId) {
        log.info("收到处理单个超时订单请求: {}", orderId);
        try {
            orderTimeoutFallbackService.processTimeoutOrderById(orderId);
            return "订单 " + orderId + " 处理完成";
        } catch (Exception e) {
            log.error("处理订单 {} 失败", orderId, e);
            return "处理订单失败: " + e.getMessage();
        }
    }
}


