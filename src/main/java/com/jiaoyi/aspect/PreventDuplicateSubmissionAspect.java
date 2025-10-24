package com.jiaoyi.aspect;

import com.jiaoyi.annotation.PreventDuplicateSubmission;
import com.jiaoyi.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PreventDuplicateSubmissionAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(preventDuplicateSubmission)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicateSubmission preventDuplicateSubmission) throws Throwable {
        // 生成锁的key
        String lockKey = generateLockKey(joinPoint, preventDuplicateSubmission);
        
        // 获取Redisson分布式锁
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待1秒，锁持有时间不超过指定时间
            boolean lockAcquired = lock.tryLock(1, preventDuplicateSubmission.expireTime(), TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                // 锁获取失败，说明正在处理中
                log.warn("检测到重复提交，锁key: {}", lockKey);
                throw new RuntimeException(preventDuplicateSubmission.message());
            }
            
            log.info("成功获取锁，锁key: {}", lockKey);
            
            // 执行业务逻辑
            return joinPoint.proceed();
            
        } catch (InterruptedException e) {
            log.error("获取锁被中断，锁key: {}", lockKey, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
    }
    
    /**
     * 生成锁的key
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, PreventDuplicateSubmission annotation) {
        String key = annotation.key();
        
        if (!StringUtils.hasText(key)) {
            // 如果没有指定key，使用默认的key生成策略
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();
            
            // 使用类名+方法名+参数hash作为key
            Object[] args = joinPoint.getArgs();
            int argsHash = java.util.Arrays.hashCode(args);
            return String.format("duplicate_submission:%s.%s:%d", className, methodName, argsHash);
        }
        
        // 解析SpEL表达式
        try {
            Expression expression = parser.parseExpression(key);
            EvaluationContext context = new StandardEvaluationContext();
            
            // 设置方法参数到上下文
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            
            Object result = expression.getValue(context);
            return "duplicate_submission:" + result.toString();
        } catch (Exception e) {
            log.error("解析SpEL表达式失败: {}", key, e);
            // 如果解析失败，使用默认策略
            return "duplicate_submission:default:" + System.currentTimeMillis();
        }
    }
}
