package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.dto.PaymentResponse;
import com.jiaoyi.service.AlipayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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
    
    /**
     * 支付宝支付回调
     */
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        log.info("收到支付宝支付回调");
        
        try {
            // 获取回调参数
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                log.info("回调参数: {} = {}", entry.getKey(), String.join(",", entry.getValue()));
            }
            
            // TODO: 验证签名和更新订单状态
            // 这里需要根据支付宝的回调参数来验证支付结果
            
            return "success";
        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            return "fail";
        }
    }
    
    /**
     * 支付宝支付返回
     */
    @GetMapping("/alipay/return")
    public String alipayReturn(HttpServletRequest request) {
        log.info("收到支付宝支付返回");
        
        try {
            // 获取返回参数
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                log.info("返回参数: {} = {}", entry.getKey(), String.join(",", entry.getValue()));
            }
            
            // TODO: 处理支付返回结果
            
            return "支付处理完成";
        } catch (Exception e) {
            log.error("处理支付宝返回异常", e);
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
