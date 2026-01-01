package com.jiaoyi.order.adapter;

import com.jiaoyi.order.adapter.PaymentAdapter;
import com.jiaoyi.order.enums.PaymentServiceEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 带 Fallback 的支付适配器包装器
 * 当主适配器失败时，自动降级处理
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentAdapterWithFallback {
    
    private final PaymentAdapterFactory adapterFactory;
    
    /**
     * 创建退款（带 Fallback）
     * 
     * @param request 退款请求
     * @return 退款结果
     */
    public PaymentAdapter.RefundResult createRefundWithFallback(PaymentAdapter.RefundRequest request) {
        PaymentAdapter adapter = adapterFactory.getAdapter(request.getPaymentService());
        
        try {
            // 尝试调用主适配器
            PaymentAdapter.RefundResult result = adapter.createRefund(request);
            
            if (result.isSuccess()) {
                return result;
            }
            
            // 如果失败，执行 Fallback
            log.warn("支付适配器调用失败，执行 Fallback，支付服务: {}, 错误: {}", 
                request.getPaymentService(), result.getErrorMessage());
            return handleFallback(request, result);
            
        } catch (Exception e) {
            log.error("支付适配器调用异常，执行 Fallback", e);
            return handleFallback(request, null);
        }
    }
    
    /**
     * Fallback 处理逻辑
     * 
     * @param request 退款请求
     * @param failedResult 失败的结果（如果有）
     * @return Fallback 结果
     */
    private PaymentAdapter.RefundResult handleFallback(PaymentAdapter.RefundRequest request, 
                                                       PaymentAdapter.RefundResult failedResult) {
        // Fallback 策略：
        // 1. 记录失败日志（用于后续人工处理）
        // 2. 返回 PROCESSING 状态，等待异步重试或人工处理
        // 3. 不直接返回失败，给系统重试的机会
        
        log.error("支付适配器 Fallback 触发，退款请求号: {}, 支付服务: {}, 原错误: {}", 
            request.getRequestNo(), 
            request.getPaymentService(),
            failedResult != null ? failedResult.getErrorMessage() : "未知错误");
        
        // 返回 PROCESSING 状态，表示已记录，等待后续处理
        PaymentAdapter.RefundResult fallbackResult = new PaymentAdapter.RefundResult();
        fallbackResult.setSuccess(false); // 标记为失败，但状态为 PROCESSING
        fallbackResult.setStatus(PaymentAdapter.RefundStatus.PROCESSING);
        fallbackResult.setErrorMessage("退款请求已记录，等待重试或人工处理");
        
        return fallbackResult;
    }
    
    /**
     * 查询退款状态（带 Fallback）
     * 
     * @param paymentService 支付服务类型
     * @param thirdPartyRefundId 第三方退款ID
     * @return 退款状态结果
     */
    public PaymentAdapter.RefundStatusResult queryRefundStatusWithFallback(
            PaymentServiceEnum paymentService, 
            String thirdPartyRefundId) {
        
        PaymentAdapter adapter = adapterFactory.getAdapter(paymentService);
        
        try {
            return adapter.queryRefundStatus(thirdPartyRefundId);
            
        } catch (Exception e) {
            log.error("查询退款状态异常，执行 Fallback", e);
            
            // Fallback：返回 PROCESSING 状态
            PaymentAdapter.RefundStatusResult fallbackResult = new PaymentAdapter.RefundStatusResult();
            fallbackResult.setSuccess(false);
            fallbackResult.setStatus(PaymentAdapter.RefundStatus.PROCESSING);
            fallbackResult.setErrorMessage("查询失败，状态未知");
            
            return fallbackResult;
        }
    }
}

