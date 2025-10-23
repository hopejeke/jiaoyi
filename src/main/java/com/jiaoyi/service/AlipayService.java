package com.jiaoyi.service;

import com.jiaoyi.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付宝支付服务 (模拟版本)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlipayService {
    
    /**
     * 创建支付订单 (模拟版本)
     */
    public PaymentResponse createPayment(String orderNo, String subject, BigDecimal amount, String paymentNo) {
        log.info("创建模拟支付宝支付订单，订单号: {}, 金额: {}", orderNo, amount);
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentNo(paymentNo);
        response.setPaymentMethod("ALIPAY");
        response.setAmount(amount.multiply(new BigDecimal("100")).longValue()); // 转换为分
        response.setStatus("PENDING");
        response.setRemark("模拟支付宝支付订单创建成功");
        
        // 生成模拟的支付URL
        String mockPayUrl = "http://localhost:8080/mock-payment.html?orderNo=" + orderNo + "&amount=" + amount;
        response.setPayUrl(mockPayUrl);
        
        log.info("模拟支付宝支付订单创建成功，订单号: {}, 支付URL: {}", orderNo, response.getPayUrl());
        return response;
    }
    
    /**
     * 查询支付结果 (模拟版本)
     */
    public PaymentResponse queryPayment(String paymentNo) {
        log.info("查询模拟支付宝支付结果，支付流水号: {}", paymentNo);
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentNo(paymentNo);
        response.setPaymentMethod("ALIPAY");
        response.setStatus("SUCCESS");
        response.setPayTime(LocalDateTime.now());
        response.setRemark("模拟支付宝支付成功");
        
        log.info("模拟支付宝支付查询成功，支付流水号: {}", paymentNo);
        return response;
    }
}