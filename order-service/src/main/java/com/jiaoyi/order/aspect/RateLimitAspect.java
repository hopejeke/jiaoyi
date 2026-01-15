package com.jiaoyi.order.aspect;

import com.google.common.util.concurrent.RateLimiter;
import com.jiaoyi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 限流切面
 * 使用 AOP 实现接口限流
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {
    
    @Qualifier("orderCreateRateLimiter")
    private final RateLimiter orderCreateRateLimiter;
    
    @Qualifier("refundCreateRateLimiter")
    private final RateLimiter refundCreateRateLimiter;
    
    /**
     * 下单接口限流
     */
    @Around("@annotation(com.jiaoyi.order.annotation.RateLimit) && execution(* com.jiaoyi.order.controller.OrderController.createOrder(..))")
    public Object limitOrderCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimit(joinPoint, orderCreateRateLimiter, "下单接口");
    }
    
    /**
     * 退款接口限流
     */
    @Around("@annotation(com.jiaoyi.order.annotation.RateLimit) && execution(* com.jiaoyi.order.controller.RefundController.createRefund(..))")
    public Object limitRefundCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimit(joinPoint, refundCreateRateLimiter, "退款接口");
    }
    
    /**
     * 执行限流逻辑
     */
    private Object executeWithRateLimit(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter, String apiName) throws Throwable {
        // 尝试获取令牌（非阻塞）
        if (!rateLimiter.tryAcquire()) {
            log.warn("{}限流触发，请求被拒绝", apiName);
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
        
        // 获取令牌成功，执行原方法
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("{}执行失败", apiName, e);
            throw e;
        }
    }
}






