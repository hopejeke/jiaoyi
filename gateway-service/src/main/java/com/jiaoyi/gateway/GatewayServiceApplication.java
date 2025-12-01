package com.jiaoyi.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * API网关服务启动类
 * 负责：统一入口、路由转发、静态资源服务
 */
@SpringBootApplication(
    scanBasePackages = {"com.jiaoyi.gateway", "com.jiaoyi.common"}
)
@EnableFeignClients
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}


