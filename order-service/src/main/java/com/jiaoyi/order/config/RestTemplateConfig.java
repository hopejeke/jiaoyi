package com.jiaoyi.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * 用于 HTTP 客户端调用（DoorDash API 等）
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * 创建 RestTemplate Bean
     * 配置超时时间等参数
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
        return restTemplate;
    }
    
    /**
     * 配置 HTTP 请求工厂
     * 设置连接超时和读取超时
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 连接超时 5 秒
        factory.setReadTimeout(10000);    // 读取超时 10 秒
        return factory;
    }
}












