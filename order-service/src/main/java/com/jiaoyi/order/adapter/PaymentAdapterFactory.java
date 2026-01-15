package com.jiaoyi.order.adapter;

import com.jiaoyi.order.enums.PaymentServiceEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付适配器工厂
 * 根据支付服务类型返回对应的适配器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentAdapterFactory {
    
    private final StripePaymentAdapter stripeAdapter;
    private final AlipayPaymentAdapter alipayAdapter;
    
    // 适配器缓存
    private final Map<PaymentServiceEnum, PaymentAdapter> adapterCache = new ConcurrentHashMap<>();
    
    /**
     * 获取支付适配器
     * 
     * @param paymentService 支付服务类型
     * @return 支付适配器
     */
    public PaymentAdapter getAdapter(PaymentServiceEnum paymentService) {
        if (paymentService == null) {
            throw new IllegalArgumentException("支付服务类型不能为空");
        }
        
        return adapterCache.computeIfAbsent(paymentService, service -> {
            switch (service) {
                case STRIPE:
                    return stripeAdapter;
                case ALIPAY:
                    return alipayAdapter;
                default:
                    throw new IllegalArgumentException("不支持的支付服务类型: " + service);
            }
        });
    }
}






