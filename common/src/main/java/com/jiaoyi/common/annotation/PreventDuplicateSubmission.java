package com.jiaoyi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreventDuplicateSubmission {
    
    /**
     * 锁的key，支持SpEL表达式
     * 例如: "#userId + '_' + #orderNo"
     */
    String key() default "";
    
    /**
     * 锁的过期时间（秒）
     */
    int expireTime() default 30;
    
    /**
     * 重复提交提示信息
     */
    String message() default "请勿重复提交";
}
