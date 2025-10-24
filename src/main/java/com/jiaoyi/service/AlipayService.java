package com.jiaoyi.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.jiaoyi.config.AlipayConfig;
import com.jiaoyi.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付宝支付服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlipayService {
    
    private final AlipayConfig alipayConfig;
    
    /**
     * 创建支付宝支付订单 (模拟支付)
     */
    public PaymentResponse createPayment(String orderNo, String subject, BigDecimal amount, String paymentNo) {
        log.info("创建模拟支付宝支付订单，订单号: {}, 金额: {}", orderNo, amount);
        
        PaymentResponse paymentResponse = new PaymentResponse();
        paymentResponse.setPaymentNo(paymentNo);
        paymentResponse.setPaymentMethod("ALIPAY");
        paymentResponse.setAmount(amount); // 直接使用传入的金额，不进行转换
        paymentResponse.setStatus("PENDING");
        paymentResponse.setRemark("模拟支付宝支付订单创建成功");
        
        // 不生成二维码，直接返回支付流水号用于前端处理
        paymentResponse.setQrCode(null);
        
        log.info("模拟支付宝支付订单创建成功，支付流水号: {}", paymentNo);
        
        return paymentResponse;
    }
    
    /**
     * 查询支付结果 (模拟支付查询)
     */
    public PaymentResponse queryPayment(String paymentNo) {
        log.info("查询模拟支付宝支付结果，支付流水号: {}", paymentNo);
        
        PaymentResponse paymentResponse = new PaymentResponse();
        paymentResponse.setPaymentNo(paymentNo);
        paymentResponse.setPaymentMethod("ALIPAY");
        paymentResponse.setStatus("PENDING"); // 模拟为待支付状态
        paymentResponse.setRemark("模拟支付宝支付查询成功");
        
        log.info("模拟支付宝支付查询成功，支付流水号: {}", paymentNo);
        return paymentResponse;
    }
}