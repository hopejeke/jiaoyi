package com.jiaoyi.product.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring Context Holder
 * 
 * 用于在非 Spring 管理的类中获取 Spring Bean
 * 主要用于 ShardingSphere 自定义分片算法中访问 Spring Bean
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * 获取 Spring Bean
     * 
     * @param clazz Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext 未初始化，无法获取 Bean");
        }
        return context.getBean(clazz);
    }

    /**
     * 获取 Spring Bean（按名称）
     * 
     * @param name Bean 名称
     * @return Bean 实例
     */
    public static Object getBean(String name) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext 未初始化，无法获取 Bean");
        }
        return context.getBean(name);
    }

    /**
     * 检查 ApplicationContext 是否已初始化
     * 
     * @return true 如果已初始化，false 否则
     */
    public static boolean isInitialized() {
        return context != null;
    }
}


