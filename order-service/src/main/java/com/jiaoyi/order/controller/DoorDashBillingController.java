package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.service.DoorDashBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * DoorDash 账单对账控制器
 */
@RestController
@RequestMapping("/api/doordash/billing")
@RequiredArgsConstructor
@Slf4j
public class DoorDashBillingController {
    
    private final DoorDashBillingService billingService;
    
    /**
     * 获取 DoorDash 账单并执行对账
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 对账结果
     */
    @GetMapping("/reconcile")
    public ResponseEntity<ApiResponse<String>> reconcileBill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("执行 DoorDash 账单对账，开始日期: {}, 结束日期: {}", startDate, endDate);
        
        try {
            // 1. 获取 DoorDash 账单
            DoorDashBillingService.DoorDashBillResponse bill = billingService.getBill(startDate, endDate);
            
            // 2. 执行对账
            billingService.reconcileBill(bill);
            
            return ResponseEntity.ok(ApiResponse.success("对账完成，账单项数量: " + bill.getItems().size()));
            
        } catch (Exception e) {
            log.error("DoorDash 账单对账失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "对账失败: " + e.getMessage()));
        }
    }
}

