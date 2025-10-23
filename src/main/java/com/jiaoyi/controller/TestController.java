package com.jiaoyi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 测试控制器
 */
@Controller
public class TestController {
    
    /**
     * 支付测试页面
     */
    @GetMapping("/test-payment")
    public String testPayment() {
        return "forward:/test-payment.html";
    }
}
