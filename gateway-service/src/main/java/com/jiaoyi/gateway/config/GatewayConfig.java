package com.jiaoyi.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 网关配置
 */
@Configuration
public class GatewayConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


