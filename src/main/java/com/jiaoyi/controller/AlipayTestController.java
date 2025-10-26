package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.config.AlipayConfig;
import com.jiaoyi.service.AlipayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 支付宝测试控制器
 */
@RestController
@RequestMapping("/api/alipay-test")
@RequiredArgsConstructor
@Slf4j
public class AlipayTestController {
    
    private final AlipayService alipayService;
    private final AlipayConfig alipayConfig;
    
    /**
     * 测试支付宝配置
     */
    @GetMapping("/config")
    public ApiResponse<Object> testConfig() {
        log.info("测试支付宝配置");
        
        return ApiResponse.success("配置信息", new Object() {
            public String appId = alipayConfig.getAppId();
            public String gatewayUrl = alipayConfig.getGatewayUrl();
            public String notifyUrl = alipayConfig.getNotifyUrl();
            public String returnUrl = alipayConfig.getReturnUrl();
            public String signType = alipayConfig.getSignType();
            public String charset = alipayConfig.getCharset();
            public String format = alipayConfig.getFormat();
        });
    }
    
    /**
     * 测试创建支付订单
     */
    @PostMapping("/create-payment")
    public ApiResponse<Object> testCreatePayment(@RequestParam String orderNo, 
                                                @RequestParam BigDecimal amount) {
        log.info("测试创建支付订单，订单号: {}, 金额: {}", orderNo, amount);
        
        try {
            var response = alipayService.createPayment(orderNo, "测试订单", amount, "PAY" + System.currentTimeMillis());
            return ApiResponse.success("支付订单创建成功", response);
        } catch (Exception e) {
            log.error("创建支付订单失败", e);
            return ApiResponse.error("创建支付订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试查询支付状态
     */
    @GetMapping("/query-payment/{orderNo}")
    public ApiResponse<Object> testQueryPayment(@PathVariable String orderNo) {
        log.info("测试查询支付状态，订单号: {}", orderNo);
        
        try {
            var response = alipayService.queryPayment(orderNo);
            return ApiResponse.success("查询支付状态成功", response);
        } catch (Exception e) {
            log.error("查询支付状态失败", e);
            return ApiResponse.error("查询支付状态失败: " + e.getMessage());
        }
    }
}
