package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.dto.OrderReconciliationResponse;
import com.jiaoyi.order.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 对账控制器
 * 提供订单资金分解和退款分解接口
 */
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationController {
    
    private final ReconciliationService reconciliationService;
    
    /**
     * 获取订单对账信息（资金分解 + 退款分解）
     * 
     * @param orderId 订单ID
     * @return 对账响应
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<OrderReconciliationResponse>> getOrderReconciliation(
            @PathVariable Long orderId) {
        
        log.info("查询订单对账信息，订单ID: {}", orderId);
        
        try {
            OrderReconciliationResponse response = reconciliationService.getOrderReconciliation(orderId);
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("查询订单对账信息失败，订单ID: {}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
}




