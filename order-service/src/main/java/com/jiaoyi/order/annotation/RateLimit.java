package com.jiaoyi.order.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 标记需要限流的接口方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * 限流速率（每秒允许的请求数）
     * 如果未指定，使用配置的默认值
     */
    double value() default 0;
}




