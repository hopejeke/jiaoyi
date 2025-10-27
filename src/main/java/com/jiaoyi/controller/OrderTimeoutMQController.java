package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.service.OrderTimeoutMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 订单超时RocketMQ测试控制器
 */
@RestController
@RequestMapping("/api/order-timeout-rocketmq")
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutMQController {

    private final OrderTimeoutMessageService orderTimeoutMessageService;

    /**
     * 手动取消指定订单（用于测试）
     */
    @PostMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<String>> cancelOrderManually(@PathVariable Long orderId) {
        log.info("手动取消订单，订单ID: {}", orderId);
        
        try {
            boolean success = orderTimeoutMessageService.cancelOrderManually(orderId);
            
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("订单取消成功", "订单ID: " + orderId));
            } else {
                return ResponseEntity.ok(ApiResponse.error("订单取消失败，可能订单不存在或状态不正确"));
            }
        } catch (Exception e) {
            log.error("手动取消订单失败", e);
            return ResponseEntity.ok(ApiResponse.error("取消订单时发生异常: " + e.getMessage()));
        }
    }

    /**
     * 发送测试延迟消息
     */
    @PostMapping("/send-test-message")
    public ResponseEntity<ApiResponse<String>> sendTestMessage(
            @RequestParam Long orderId,
            @RequestParam String orderNo,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int delayMinutes) {
        
        log.info("发送测试延迟消息，订单ID: {}, 延迟: {}分钟", orderId, delayMinutes);
        
        try {
            orderTimeoutMessageService.sendOrderTimeoutMessage(orderId, orderNo, userId, delayMinutes);
            return ResponseEntity.ok(ApiResponse.success("测试延迟消息发送成功", 
                    String.format("订单ID: %d, 延迟: %d分钟", orderId, delayMinutes)));
        } catch (Exception e) {
            log.error("发送测试延迟消息失败", e);
            return ResponseEntity.ok(ApiResponse.error("发送测试延迟消息失败: " + e.getMessage()));
        }
    }
}
