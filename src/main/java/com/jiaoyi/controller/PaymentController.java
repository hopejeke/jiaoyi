package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.dto.PaymentResponse;
import com.jiaoyi.entity.OrderStatus;
import com.jiaoyi.mapper.OrderMapper;
import com.jiaoyi.service.AlipayService;
import com.jiaoyi.service.PaymentService;
import com.jiaoyi.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    
    private final AlipayService alipayService;
    private final PaymentService paymentService;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    
    /**
     * 支付宝支付回调
     */
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        log.info("收到支付宝支付回调通知");
        
        try {
            // 获取所有请求参数
            Map<String, String> params = new HashMap<>();
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                String paramValue = request.getParameter(paramName);
                params.put(paramName, paramValue);
                log.info("回调参数: {} = {}", paramName, paramValue);
            }
            
            // 处理支付结果
            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmount = params.get("total_amount");
            String tradeNo = params.get("trade_no");
            
            log.info("支付回调处理 - 订单号: {}, 状态: {}, 金额: {}, 支付宝交易号: {}", 
                    outTradeNo, tradeStatus, totalAmount, tradeNo);
            
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 支付成功，更新订单状态和库存
                log.info("开始处理支付成功回调，支付流水号: {}", outTradeNo);
                
                try {
                    // 1. 更新订单状态为已支付
                    // 现在 outTradeNo 就是订单号
                    int updatedRows = orderMapper.updateStatusToPaidIfPending(outTradeNo);
                    if (updatedRows == 0) {
                        log.warn("订单状态已变更，支付失败，订单号: {}", outTradeNo);
                        return "FAIL";
                    }
                    log.info("订单状态更新为已支付，订单号: {}", outTradeNo);
                    
                    // 2. 扣减库存（将锁定的库存转换为实际扣减）
                    log.info("开始扣减库存，订单号: {}", outTradeNo);
                    inventoryService.deductStockByOrderNo(outTradeNo);
                    
                    log.info("支付成功处理完成，订单号: {}", outTradeNo);
                    return "success";
                } catch (Exception e) {
                    log.error("处理支付成功回调异常，支付流水号: {}", outTradeNo, e);
                    return "fail";
                }
            } else {
                log.warn("支付状态异常: {}", tradeStatus);
                return "fail";
            }
            
        } catch (Exception e) {
            log.error("处理支付回调异常", e);
            return "fail";
        }
    }
    
    /**
     * 支付宝支付返回
     */
    @GetMapping("/alipay/return")
    public String alipayReturn(HttpServletRequest request) {
        log.info("收到支付宝支付同步返回");
        
        try {
            // 获取所有请求参数
            Map<String, String> params = new HashMap<>();
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                String paramValue = request.getParameter(paramName);
                params.put(paramName, paramValue);
                log.info("返回参数: {} = {}", paramName, paramValue);
            }
            
            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            
            log.info("支付返回处理 - 订单号: {}, 状态: {}", outTradeNo, tradeStatus);
            
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                return "支付成功！订单号: " + outTradeNo;
            } else {
                return "支付处理中，请稍后查询订单状态";
            }
            
        } catch (Exception e) {
            log.error("处理支付返回异常", e);
            return "支付处理异常";
        }
    }
    
    /**
     * 查询支付结果
     */
    @GetMapping("/query/{paymentNo}")
    public ApiResponse<PaymentResponse> queryPayment(@PathVariable String paymentNo) {
        log.info("查询支付结果，支付流水号: {}", paymentNo);
        
        try {
            PaymentResponse response = alipayService.queryPayment(paymentNo);
            return ApiResponse.success("查询成功", response);
        } catch (Exception e) {
            log.error("查询支付结果失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
