package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.dto.PaymentResponse;
import com.jiaoyi.entity.Order;
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
                    // 1. 先查询订单当前状态
                    Order order = orderMapper.selectByOrderNo(outTradeNo);
                    if (order == null) {
                        log.warn("订单不存在，支付流水号: {}", outTradeNo);
                        return "FAIL";
                    }
                    
                    // 2. 检查订单状态
                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        log.warn("订单已取消，但收到支付成功回调，订单号: {}, 当前状态: {}", outTradeNo, order.getStatus());
                        // 订单已取消但支付成功，需要特殊处理
                        return handleCancelledOrderPayment(order, outTradeNo);
                    }
                    
                    if (order.getStatus() == OrderStatus.PAID) {
                        log.info("订单已支付，重复回调，订单号: {}", outTradeNo);
                        return "success"; // 幂等处理
                    }
                    
                    if (order.getStatus() != OrderStatus.PENDING) {
                        log.warn("订单状态不允许支付，订单号: {}, 当前状态: {}", outTradeNo, order.getStatus());
                        return "FAIL";
                    }
                    
                    // 3. 更新订单状态为已支付（原子操作）
                    int updatedRows = orderMapper.updateStatusToPaidIfPending(outTradeNo);
                    if (updatedRows == 0) {
                        log.warn("订单状态已变更，支付失败，订单号: {}", outTradeNo);
                        return "FAIL";
                    }
                    log.info("订单状态更新为已支付，订单号: {}", outTradeNo);
                    
                    // 4. 扣减库存（将锁定的库存转换为实际扣减）
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
    
    /**
     * 处理已取消订单的支付回调
     * 这种情况需要退款：订单已取消但支付成功了
     */
    private String handleCancelledOrderPayment(Order order, String outTradeNo) {
        log.warn("处理已取消订单的支付回调，订单号: {}, 订单ID: {}, 支付金额: {}", 
                outTradeNo, order.getId(), order.getActualAmount());
        
        try {
            // 1. 记录异常情况（用于后续人工处理）
            log.error("异常情况：订单已取消但支付成功，需要退款处理，订单号: {}, 订单ID: {}, 支付金额: {}", 
                    outTradeNo, order.getId(), order.getActualAmount());
            
            // 2. 发起退款（这里需要调用支付平台的退款接口）
            boolean refundSuccess = processRefund(order, outTradeNo);
            
            if (refundSuccess) {
                log.info("已取消订单退款成功，订单号: {}, 退款金额: {}", outTradeNo, order.getActualAmount());
                return "success";
            } else {
                log.error("已取消订单退款失败，订单号: {}, 退款金额: {}", outTradeNo, order.getActualAmount());
                return "FAIL";
            }
            
        } catch (Exception e) {
            log.error("处理已取消订单支付回调异常，订单号: {}", outTradeNo, e);
            return "FAIL";
        }
    }
    
    /**
     * 处理退款
     */
    private boolean processRefund(Order order, String outTradeNo) {
        try {
            log.info("发起退款，订单号: {}, 退款金额: {}, 退款原因: 订单超时取消自动退款", 
                    outTradeNo, order.getActualAmount());
            
            // 调用支付宝退款接口
            boolean refundSuccess = alipayService.refund(outTradeNo, order.getActualAmount(), "订单超时取消自动退款");
            
            if (refundSuccess) {
                log.info("退款成功，订单号: {}, 退款金额: {}", outTradeNo, order.getActualAmount());
            } else {
                log.error("退款失败，需要人工处理，订单号: {}, 退款金额: {}", outTradeNo, order.getActualAmount());
            }
            
            return refundSuccess;
            
        } catch (Exception e) {
            log.error("退款处理异常，订单号: {}", outTradeNo, e);
            return false;
        }
    }
}
