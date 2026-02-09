package com.jiaoyi.order.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要权限注解
 * 用于标记需要特定权限才能访问的接口
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /**
     * 需要的权限列表（满足其中任意一个即可）
     */
    String[] value();

    /**
     * 是否需要同时拥有所有权限（默认false，只需满足其中一个）
     */
    boolean requireAll() default false;
}
