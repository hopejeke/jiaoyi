package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.config.StripeConfig;
import com.jiaoyi.order.dto.PaymentResponse;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.Payment;
import com.jiaoyi.order.enums.PaymentStatusEnum;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.PaymentMapper;
import com.jiaoyi.order.service.AlipayService;
import com.jiaoyi.order.service.PaymentService;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.mapper.OrderItemMapper;
import com.jiaoyi.order.entity.OrderItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final PaymentMapper paymentMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductServiceClient productServiceClient;
    private final StripeConfig stripeConfig;
    private final ObjectMapper objectMapper;
    
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
                // 支付成功，更新订单状态和库存（参照 OO 项目的支付回调处理）
                log.info("开始处理支付成功回调，订单号: {}, 支付宝交易号: {}", outTradeNo, tradeNo);
                
                try {
                    // 1. 先查询订单当前状态（outTradeNo 是订单ID的字符串形式）
                    Long orderId = Long.parseLong(outTradeNo);
                    Order order = orderMapper.selectById(orderId);
                    if (order == null) {
                        log.warn("订单不存在，订单ID: {}", orderId);
                        return "FAIL";
                    }
                    
                    // 2. 检查订单状态
                    if (com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode().equals(order.getStatus())) {
                        log.warn("订单已取消，但收到支付成功回调，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
                        // 订单已取消但支付成功，需要特殊处理
                        return handleCancelledOrderPayment(order, outTradeNo);
                    }
                    
                    if (com.jiaoyi.order.enums.OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
                        log.info("订单已支付，重复回调（幂等处理），订单ID: {}", orderId);
                        return "success"; // 幂等处理
                    }
                    
                    if (!com.jiaoyi.order.enums.OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
                        log.warn("订单状态不允许支付，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
                        return "FAIL";
                    }
                    
                    // 3. 使用 PaymentService 处理支付成功（包含幂等性检查和原子更新，以及超时检查）
                    boolean paymentProcessed = paymentService.handlePaymentSuccess(outTradeNo, tradeNo);
                    
                    // 如果订单超时已退款，不继续处理（不扣减库存）
                    if (!paymentProcessed) {
                        log.warn("订单超时已退款，不继续处理，订单号: {}", outTradeNo);
                        return "success"; // 告知支付宝处理成功（已退款）
                    }
                    
                    // 4. 扣减库存（通过 Feign Client 调用 product-service）
                    log.info("开始扣减库存，订单号: {}", outTradeNo);
                    // 查询订单项
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    if (orderItems != null && !orderItems.isEmpty()) {
                        List<Long> productIds = orderItems.stream()
                                .filter(item -> item.getProductId() != null)
                                .map(OrderItem::getProductId)
                                .collect(java.util.stream.Collectors.toList());
                        List<Long> skuIds = orderItems.stream()
                                .filter(item -> item.getSkuId() != null)
                                .map(OrderItem::getSkuId)
                                .collect(java.util.stream.Collectors.toList());
                        List<Integer> quantities = orderItems.stream()
                                .map(OrderItem::getQuantity)
                                .collect(java.util.stream.Collectors.toList());
                        
                        if (!productIds.isEmpty() && !skuIds.isEmpty() && productIds.size() == skuIds.size()) {
                            // 调用批量扣减库存接口（SKU级别）
                            ProductServiceClient.DeductStockBatchRequest deductRequest = new ProductServiceClient.DeductStockBatchRequest();
                            deductRequest.setProductIds(productIds);
                            deductRequest.setSkuIds(skuIds);
                            deductRequest.setQuantities(quantities);
                            deductRequest.setOrderId(order.getId());
                            productServiceClient.deductStockBatch(deductRequest);
                            log.info("库存扣减成功（SKU级别），订单号: {}, 商品数量: {}", outTradeNo, productIds.size());
                        } else {
                            log.warn("订单项缺少productId或skuId，无法扣减库存，订单号: {}", outTradeNo);
                        }
                    } else {
                        log.warn("订单项为空，无法扣减库存，订单号: {}", outTradeNo);
                    }
                    
                    log.info("支付成功处理完成，订单号: {}", outTradeNo);
                    return "success";
                } catch (Exception e) {
                    log.error("处理支付成功回调异常，订单号: {}", outTradeNo, e);
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
     * 查询支付结果（通过订单ID查询）
     */
    @GetMapping("/query/order/{orderId}")
    public ApiResponse<PaymentResponse> queryPaymentByOrderId(@PathVariable Long orderId) {
        log.info("查询支付结果，订单ID: {}", orderId);
        
        try {
            // 通过订单ID查询支付记录
            List<Payment> payments = paymentMapper.selectByOrderId(orderId);
            if (payments == null || payments.isEmpty()) {
                return ApiResponse.error("未找到支付记录");
            }
            
            // 返回最新的支付记录
            Payment payment = payments.get(0);
            PaymentResponse response = convertPaymentToResponse(payment);
            return ApiResponse.success("查询成功", response);
        } catch (Exception e) {
            log.error("查询支付结果失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询支付结果（通过支付ID查询）
     */
    @GetMapping("/query/{paymentId}")
    public ApiResponse<PaymentResponse> queryPaymentById(@PathVariable Long paymentId) {
        log.info("查询支付结果，支付ID: {}", paymentId);
        
        try {
            Payment payment = paymentMapper.selectById(paymentId);
            if (payment == null) {
                return ApiResponse.error("未找到支付记录");
            }
            
            PaymentResponse response = convertPaymentToResponse(payment);
            return ApiResponse.success("查询成功", response);
        } catch (Exception e) {
            log.error("查询支付结果失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取 Stripe 配置（前端使用）
     */
    @GetMapping("/stripe/config")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStripeConfig() {
        log.info("获取 Stripe 配置");
        
        Map<String, String> config = new HashMap<>();
        config.put("publishableKey", stripeConfig.getPublishableKey() != null ? stripeConfig.getPublishableKey() : "");
        config.put("enabled", String.valueOf(stripeConfig.getEnabled() != null && stripeConfig.getEnabled()));
        
        return ResponseEntity.ok(ApiResponse.success("获取成功", config));
    }
    
    /**
     * 发起支付请求
     */
    @PostMapping("/pay/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @PathVariable Long orderId,
            @RequestBody com.jiaoyi.order.dto.PaymentRequest request) {
        log.info("收到支付请求，订单ID: {}, 支付方式: {}", orderId, request.getPaymentMethod());
        try {
            PaymentResponse response = paymentService.processPayment(orderId, request);
            return ResponseEntity.ok(ApiResponse.success("支付请求处理成功", response));
        } catch (Exception e) {
            log.error("支付请求处理失败，订单ID: {}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "支付请求处理失败: " + e.getMessage()));
        }
    }
    
    /**
     * 处理已取消订单的支付回调
     * 这种情况需要退款：订单已取消但支付成功了
     */
    private String handleCancelledOrderPayment(Order order, String outTradeNo) {
        // 从 orderPrice JSON 中解析金额
        java.math.BigDecimal amount = parseOrderPrice(order);
        log.warn("处理已取消订单的支付回调，订单号: {}, 订单ID: {}, 支付金额: {}", 
                outTradeNo, order.getId(), amount);
        
        try {
            // 1. 记录异常情况（用于后续人工处理）
            log.error("异常情况：订单已取消但支付成功，需要退款处理，订单号: {}, 订单ID: {}, 支付金额: {}", 
                    outTradeNo, order.getId(), amount);
            
            // 2. 发起退款（这里需要调用支付平台的退款接口）
            boolean refundSuccess = processRefund(order, outTradeNo, amount);
            
            if (refundSuccess) {
                log.info("已取消订单退款成功，订单号: {}, 退款金额: {}", outTradeNo, amount);
                return "success";
            } else {
                log.error("已取消订单退款失败，订单号: {}, 退款金额: {}", outTradeNo, amount);
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
    private boolean processRefund(Order order, String outTradeNo, java.math.BigDecimal amount) {
        try {
            log.info("发起退款，订单号: {}, 退款金额: {}, 退款原因: 订单超时取消自动退款", 
                    outTradeNo, amount);
            
            // 调用支付宝退款接口
            boolean refundSuccess = alipayService.refund(outTradeNo, amount, "订单超时取消自动退款");
            
            if (refundSuccess) {
                log.info("退款成功，订单号: {}, 退款金额: {}", outTradeNo, amount);
            } else {
                log.error("退款失败，需要人工处理，订单号: {}, 退款金额: {}", outTradeNo, amount);
            }
            
            return refundSuccess;
            
        } catch (Exception e) {
            log.error("退款处理异常，订单号: {}", outTradeNo, e);
            return false;
        }
    }
    
    /**
     * 从订单价格JSON中解析总金额
     */
    private java.math.BigDecimal parseOrderPrice(Order order) {
        if (order.getOrderPrice() == null) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr.startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> orderPrice = mapper.readValue(orderPriceStr, java.util.Map.class);
                Object totalObj = orderPrice.get("total");
                if (totalObj != null) {
                    return new java.math.BigDecimal(totalObj.toString());
                }
            }
        } catch (Exception e) {
            log.error("解析订单价格失败，订单ID: {}", order.getId(), e);
        }
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * 将 Payment 实体转换为 PaymentResponse
     */
    private PaymentResponse convertPaymentToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setPaymentMethod(payment.getPaymentService() != null ? payment.getPaymentService().getCode() : null);
        response.setAmount(payment.getAmount());
        
        if (payment.getStatus() != null && payment.getStatus().equals(PaymentStatusEnum.SUCCESS.getCode())) {
            response.setStatus("SUCCESS");
        } else if (payment.getStatus() != null && payment.getStatus().equals(PaymentStatusEnum.FAILED.getCode())) {
            response.setStatus("FAILED");
        } else {
            response.setStatus("PENDING");
        }
        
        response.setThirdPartyTradeNo(payment.getThirdPartyTradeNo());
        response.setPayTime(payment.getUpdateTime());
        
        // 解析二维码
        if (payment.getExtra() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = objectMapper.readValue(payment.getExtra(), Map.class);
                if (extra.containsKey("qrCode")) {
                    response.setQrCode(extra.get("qrCode").toString());
                    response.setPayUrl(extra.get("qrCode").toString());
                }
            } catch (Exception e) {
                log.warn("解析支付额外信息失败", e);
            }
        }
        
        return response;
    }
}

