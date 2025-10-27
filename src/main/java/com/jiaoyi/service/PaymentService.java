package com.jiaoyi.service;

import com.jiaoyi.dto.PaymentRequest;
import com.jiaoyi.dto.PaymentResponse;
import com.jiaoyi.entity.Order;
import com.jiaoyi.entity.OrderStatus;
import com.jiaoyi.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    private final OrderMapper orderMapper;
    private final AlipayService alipayService;
    
    /**
     * 处理支付
     */
    @Transactional
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        log.info("处理支付，订单ID: {}, 支付方式: {}", orderId, request.getPaymentMethod());
        
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        
        // 2. 检查订单状态
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("订单状态不正确，无法支付");
        }
        
        // 3. 验证支付金额（使用实际支付金额进行比较）
        BigDecimal expectedAmount = order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount();
        if (expectedAmount.compareTo(request.getAmount()) != 0) {
            log.error("支付金额不匹配，订单ID: {}, 期望金额: {}, 实际金额: {}", orderId, expectedAmount, request.getAmount());
            throw new RuntimeException("支付金额不匹配");
        }
        
        // 4. 生成支付流水号
        String paymentNo = generatePaymentNo();
        
        // 5. 调用第三方支付平台
        PaymentResponse paymentResponse = callThirdPartyPayment(order, request, paymentNo);
        
        // 6. 如果支付成功，更新订单状态
        if ("SUCCESS".equals(paymentResponse.getStatus())) {
            updateOrderStatus(orderId, OrderStatus.PAID);
            log.info("支付成功，订单ID: {}, 支付流水号: {}", orderId, paymentNo);
        }
        
        return paymentResponse;
    }
    
    /**
     * 调用第三方支付平台
     */
    private PaymentResponse callThirdPartyPayment(Order order, PaymentRequest request, String paymentNo) {
        log.info("调用第三方支付平台，订单ID: {}, 支付方式: {}", order.getId(), request.getPaymentMethod());
        
        // 根据支付方式调用不同的支付服务
        switch (request.getPaymentMethod().toUpperCase()) {
            case "ALIPAY":
                return callAlipayPayment(order, request, paymentNo);
            case "WECHAT":
                return callWechatPayment(order, request, paymentNo);
            case "BANK":
                return callBankPayment(order, request, paymentNo);
            default:
                throw new RuntimeException("不支持的支付方式: " + request.getPaymentMethod());
        }
    }
    
    /**
     * 调用支付宝支付
     */
    private PaymentResponse callAlipayPayment(Order order, PaymentRequest request, String paymentNo) {
        log.info("调用支付宝支付，订单ID: {}", order.getId());
        
        // 直接使用请求中的金额（已经是元为单位）
        BigDecimal amount = request.getAmount();
        
        // 调用支付宝服务
        return alipayService.createPayment(
            order.getOrderNo(),
            "订单支付：" + order.getOrderNo(),
            amount,
            paymentNo
        );
    }
    
    /**
     * 调用微信支付
     */
    private PaymentResponse callWechatPayment(Order order, PaymentRequest request, String paymentNo) {
        log.info("调用微信支付，订单ID: {}", order.getId());
        
        // TODO: 集成微信支付
        PaymentResponse response = new PaymentResponse();
        response.setPaymentNo(paymentNo);
        response.setStatus("PENDING");
        response.setPaymentMethod("WECHAT");
        response.setAmount(request.getAmount());
        response.setRemark("微信支付功能待开发");
        
        return response;
    }
    
    /**
     * 调用银行卡支付
     */
    private PaymentResponse callBankPayment(Order order, PaymentRequest request, String paymentNo) {
        log.info("调用银行卡支付，订单ID: {}", order.getId());
        
        // TODO: 集成银行卡支付
        PaymentResponse response = new PaymentResponse();
        response.setPaymentNo(paymentNo);
        response.setStatus("PENDING");
        response.setPaymentMethod("BANK");
        response.setAmount(request.getAmount());
        response.setRemark("银行卡支付功能待开发");
        
        return response;
    }
    
    /**
     * 更新订单状态
     */
    private void updateOrderStatus(Long orderId, OrderStatus status) {
        log.info("更新订单状态，订单ID: {}, 状态: {}", orderId, status);
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setStatus(status);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateStatus(orderId, status);
            log.info("订单状态更新成功，订单ID: {}, 新状态: {}", orderId, status);
        } else {
            log.warn("订单不存在，订单ID: {}", orderId);
        }
    }
    
    /**
     * 处理支付成功
     */
    public void handlePaymentSuccess(String paymentNo, String thirdPartyTradeNo) {
        log.info("处理支付成功，支付流水号: {}, 第三方交易号: {}", paymentNo, thirdPartyTradeNo);
        
        try {
            // 更新支付状态为成功
            // 这里可以添加支付记录更新逻辑
            
            // 更新订单状态为已支付
            int updatedRows = orderMapper.updateStatusToPaidIfPending(paymentNo);
            if (updatedRows == 0) {
                log.warn("订单状态已变更，支付失败，订单号: {}", paymentNo);
                return;
            }
            
            log.info("支付成功处理完成，支付流水号: {}", paymentNo);
        } catch (Exception e) {
            log.error("处理支付成功异常", e);
            throw new RuntimeException("处理支付成功失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成支付流水号
     */
    private String generatePaymentNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
