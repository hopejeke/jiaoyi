package com.jiaoyi.order.config;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流配置
 * 使用 Guava RateLimiter 实现令牌桶限流
 */
@Configuration
@Slf4j
public class RateLimitConfig {
    
    /**
     * 下单接口限流速率（每秒允许的请求数）
     */
    @Value("${rate.limit.order.create:10}")
    private double orderCreateRate;
    
    /**
     * 退款接口限流速率（每秒允许的请求数）
     */
    @Value("${rate.limit.refund.create:5}")
    private double refundCreateRate;
    
    /**
     * 下单接口限流器
     */
    @Bean("orderCreateRateLimiter")
    public RateLimiter orderCreateRateLimiter() {
        RateLimiter limiter = RateLimiter.create(orderCreateRate);
        log.info("下单接口限流器初始化，速率: {} QPS", orderCreateRate);
        return limiter;
    }
    
    /**
     * 退款接口限流器
     */
    @Bean("refundCreateRateLimiter")
    public RateLimiter refundCreateRateLimiter() {
        RateLimiter limiter = RateLimiter.create(refundCreateRate);
        log.info("退款接口限流器初始化，速率: {} QPS", refundCreateRate);
        return limiter;
    }
}



