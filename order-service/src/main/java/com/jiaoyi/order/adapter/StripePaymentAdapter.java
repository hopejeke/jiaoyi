package com.jiaoyi.order.adapter;

import com.jiaoyi.order.config.StripeConfig;
import com.jiaoyi.order.enums.PaymentServiceEnum;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stripe 支付适配器
 * 实现超时控制和 Fallback
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StripePaymentAdapter implements PaymentAdapter {
    
    private final StripeConfig stripeConfig;
    
    // 默认超时时间（秒）
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    
    @Override
    public RefundResult createRefund(RefundRequest request) {
        if (!stripeConfig.getEnabled()) {
            return RefundResult.failed("Stripe 支付未启用");
        }
        
        if (request.getPaymentIntentId() == null || request.getPaymentIntentId().isEmpty()) {
            return RefundResult.failed("Payment Intent ID 不能为空");
        }
        
        try {
            // 使用 CompletableFuture 实现超时控制
            CompletableFuture<RefundResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeRefund(request);
                } catch (Exception e) {
                    log.error("Stripe 退款执行失败", e);
                    return RefundResult.failed("Stripe 退款执行失败: " + e.getMessage());
                }
            });
            
            // 等待结果，带超时
            RefundResult result = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result;
            
        } catch (TimeoutException e) {
            log.error("Stripe 退款超时，Payment Intent ID: {}, 超时时间: {}秒", 
                request.getPaymentIntentId(), DEFAULT_TIMEOUT_SECONDS);
            return RefundResult.failed("Stripe 退款超时");
            
        } catch (Exception e) {
            log.error("Stripe 退款异常", e);
            return RefundResult.failed("Stripe 退款异常: " + e.getMessage());
        }
    }
    
    /**
     * 执行退款（实际调用 Stripe API）
     */
    private RefundResult executeRefund(RefundRequest request) {
        try {
            // 将金额转换为分（Stripe 使用最小货币单位）
            long amountInCents = request.getRefundAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();
            
            // 构建退款参数
            Map<String, Object> params = new HashMap<>();
            params.put("payment_intent", request.getPaymentIntentId());
            params.put("amount", amountInCents);
            params.put("reason", "requested_by_customer");
            params.put("reverse_transfer", true); // 自动回补平台抽成
            
            // 调用 Stripe API
            Refund stripeRefund = Refund.create(params);
            
            // 解析状态
            RefundStatus status = parseStripeStatus(stripeRefund.getStatus());
            
            RefundResult result = new RefundResult();
            result.setSuccess(true);
            result.setThirdPartyRefundId(stripeRefund.getId());
            result.setStatus(status);
            
            log.info("Stripe 退款成功，退款ID: {}, 状态: {}", 
                stripeRefund.getId(), stripeRefund.getStatus());
            
            return result;
            
        } catch (StripeException e) {
            log.error("Stripe API 调用失败", e);
            return RefundResult.failed("Stripe API 错误: " + e.getMessage());
        }
    }
    
    /**
     * 解析 Stripe 退款状态
     */
    private RefundStatus parseStripeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return RefundStatus.PROCESSING;
        }
        
        switch (stripeStatus.toLowerCase()) {
            case "succeeded":
                return RefundStatus.SUCCEEDED;
            case "pending":
            case "processing":
                return RefundStatus.PROCESSING;
            case "failed":
            case "canceled":
                return RefundStatus.FAILED;
            default:
                return RefundStatus.PROCESSING;
        }
    }
    
    @Override
    public RefundStatusResult queryRefundStatus(String thirdPartyRefundId) {
        if (!stripeConfig.getEnabled()) {
            return RefundStatusResult.failed("Stripe 支付未启用");
        }
        
        try {
            CompletableFuture<RefundStatusResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Refund refund = Refund.retrieve(thirdPartyRefundId);
                    RefundStatus status = parseStripeStatus(refund.getStatus());
                    
                    RefundStatusResult result = new RefundStatusResult();
                    result.setSuccess(true);
                    result.setStatus(status);
                    return result;
                    
                } catch (StripeException e) {
                    log.error("查询 Stripe 退款状态失败", e);
                    return RefundStatusResult.failed("查询失败: " + e.getMessage());
                }
            });
            
            return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (TimeoutException e) {
            log.error("查询 Stripe 退款状态超时，退款ID: {}", thirdPartyRefundId);
            return RefundStatusResult.failed("查询超时");
            
        } catch (Exception e) {
            log.error("查询 Stripe 退款状态异常", e);
            return RefundStatusResult.failed("查询异常: " + e.getMessage());
        }
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        if (stripeConfig.getEnabled() && stripeConfig.getSecretKey() != null) {
            Stripe.apiKey = stripeConfig.getSecretKey();
            log.info("Stripe Payment Adapter 初始化成功");
        } else {
            log.warn("Stripe Payment Adapter 未启用或配置缺失");
        }
    }
}

