package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.dto.RefundRequest;
import com.jiaoyi.order.dto.RefundResponse;
import com.jiaoyi.order.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 退款控制器
 */
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@Slf4j
public class RefundController {
    
    private final RefundService refundService;
    
    /**
     * 创建退款
     */
    @PostMapping
    @com.jiaoyi.order.annotation.RateLimit
    public ResponseEntity<ApiResponse<RefundResponse>> createRefund(@RequestBody RefundRequest request) {
        log.info("创建退款请求，订单ID: {}, 退款类型: {}, 请求号: {}", 
            request.getOrderId(), request.getRefundType(), request.getRequestNo());
        
        try {
            // 验证请求
            if (request.getOrderId() == null) {
                return ResponseEntity.ok(ApiResponse.error("订单ID不能为空"));
            }
            if (request.getRequestNo() == null || request.getRequestNo().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("退款请求号不能为空"));
            }
            if (request.getRefundType() == null) {
                return ResponseEntity.ok(ApiResponse.error("退款类型不能为空"));
            }
            
            RefundResponse response = refundService.createRefund(request);
            return ResponseEntity.ok(ApiResponse.success("退款申请已提交", response));
        } catch (Exception e) {
            log.error("创建退款失败", e);
            return ResponseEntity.ok(ApiResponse.error("创建退款失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询退款单详情
     */
    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefundDetail(@PathVariable Long refundId) {
        log.info("查询退款单详情，退款ID: {}", refundId);
        
        try {
            RefundResponse response = refundService.getRefundDetail(refundId);
            return ResponseEntity.ok(ApiResponse.success("查询成功", response));
        } catch (Exception e) {
            log.error("查询退款单详情失败", e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询订单的退款列表
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsByOrderId(@PathVariable Long orderId) {
        log.info("查询订单退款列表，订单ID: {}", orderId);
        
        try {
            List<RefundResponse> refunds = refundService.getRefundsByOrderId(orderId);
            return ResponseEntity.ok(ApiResponse.success("查询成功", refunds));
        } catch (Exception e) {
            log.error("查询订单退款列表失败", e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }
}

