package com.jiaoyi.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API网关服务启动类
 * 基于 Spring Cloud Gateway 的响应式网关
 * 使用 WebFlux 非阻塞模型，支持高并发
 */
@SpringBootApplication(
    // Gateway 使用 WebFlux，不需要 Spring MVC 的全局异常处理器
    scanBasePackages = {"com.jiaoyi.gateway", "com.jiaoyi.common"},
    exclude = {
        // 排除 Spring MVC 相关的自动配置
        org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class
    }
)
@EnableDiscoveryClient
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}


