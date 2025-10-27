package com.jiaoyi.controller;

import com.jiaoyi.annotation.PreventDuplicateSubmission;
import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.dto.CreateOrderRequest;
import com.jiaoyi.dto.OrderResponse;
import com.jiaoyi.dto.PaymentRequest;
import com.jiaoyi.dto.PaymentResponse;
import com.jiaoyi.entity.OrderStatus;
import com.jiaoyi.service.OrderService;
import com.jiaoyi.service.PaymentService;
import com.jiaoyi.util.CartFingerprintUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import com.github.pagehelper.PageInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderController {
    
    private final OrderService orderService;
    private final PaymentService paymentService;
    
    /**
     * 创建订单
     */
    @PostMapping
    @PreventDuplicateSubmission(key = "T(com.jiaoyi.util.CartFingerprintUtil).generateFingerprint(#request)", expireTime = 2, message = "请勿重复提交相同订单")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("接收到创建订单请求，用户ID: {}", request.getUserId());
        try {
            OrderResponse order = orderService.createOrder(request);
            log.info("订单创建成功，订单号: {}", order.getOrderNo());
            return ResponseEntity.ok(ApiResponse.success("订单创建成功", order));
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return ResponseEntity.ok(ApiResponse.error("创建订单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有订单
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        log.info("获取所有订单");
        try {
            List<OrderResponse> orders = orderService.getAllOrders();
            return ResponseEntity.ok(ApiResponse.success("获取订单列表成功", orders));
        } catch (Exception e) {
            log.error("获取订单列表失败", e);
            return ResponseEntity.ok(ApiResponse.error("获取订单列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据订单号查询订单
     */
    @GetMapping("/orderNo/{orderNo}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByOrderNo(@PathVariable String orderNo) {
        log.info("查询订单，订单号: {}", orderNo);
        Optional<OrderResponse> order = orderService.getOrderByOrderNo(orderNo);
        if (order.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(order.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
    }
    
    /**
     * 根据订单ID查询订单
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long orderId) {
        log.info("查询订单，订单ID: {}", orderId);
        Optional<OrderResponse> order = orderService.getOrderById(orderId);
        if (order.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(order.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
    }
    
    /**
     * 根据用户ID查询订单列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("查询用户订单列表，用户ID: {}", userId);
        List<OrderResponse> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    /**
     * 根据用户ID分页查询订单
     */
    @GetMapping("/user/{userId}/page")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        log.info("分页查询用户订单，用户ID: {}, 页码: {}, 大小: {}", userId, pageNum, pageSize);
        
        List<OrderResponse> orders = orderService.getOrdersByUserId(userId, pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    /**
     * 根据用户ID和状态查询订单列表
     */
    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUserIdAndStatus(
            @PathVariable Long userId,
            @PathVariable OrderStatus status) {
        
        log.info("查询用户指定状态订单，用户ID: {}, 状态: {}", userId, status);
        List<OrderResponse> orders = orderService.getOrdersByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    /**
     * 分页查询订单列表（管理员接口）
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrdersPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("分页查询订单列表，页码: {}, 大小: {}, 订单号: {}, 用户ID: {}, 状态: {}", 
                pageNum, pageSize, orderNo, userId, status);
        
        Map<String, Object> result = orderService.getOrdersPage(pageNum, pageSize, orderNo, userId, status, startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 更新订单状态
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<Void>> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {
        
        log.info("更新订单状态，订单ID: {}, 状态: {}", orderId, status);
        boolean success = orderService.updateOrderStatus(orderId, status);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("订单状态更新成功", null));
        } else {
            return ResponseEntity.ok(ApiResponse.error("订单状态更新失败"));
        }
    }
    
    /**
     * 支付订单
     */
    @PostMapping("/{orderId}/pay")
    @PreventDuplicateSubmission(key = "#orderId + '_pay'", expireTime = 60, message = "请勿重复提交支付")
    public ResponseEntity<ApiResponse<PaymentResponse>> payOrder(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest request) {
        log.info("支付订单，订单ID: {}, 支付方式: {}", orderId, request.getPaymentMethod());
        
        try {
            PaymentResponse paymentResponse = paymentService.processPayment(orderId, request);
            return ResponseEntity.ok(ApiResponse.success("支付处理成功", paymentResponse));
        } catch (Exception e) {
            log.error("支付处理失败", e);
            return ResponseEntity.ok(ApiResponse.error("支付处理失败: " + e.getMessage()));
        }
    }
    
    /**
     * 取消订单
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        log.info("取消订单，订单ID: {}", orderId);
        boolean success = orderService.cancelOrder(orderId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("订单取消成功", null));
        } else {
            return ResponseEntity.ok(ApiResponse.error("订单取消失败"));
        }
    }
}
