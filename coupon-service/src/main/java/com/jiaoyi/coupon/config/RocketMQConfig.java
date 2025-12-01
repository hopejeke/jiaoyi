package com.jiaoyi.coupon.config;

import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类（Coupon Service）
 * 目前 coupon-service 不需要发送 RocketMQ 消息
 * 如果需要，可以在这里配置相关的 Topic 和 Producer Group
 */
@Configuration
public class RocketMQConfig {
    
    // 如果需要发送消息，可以在这里定义 Topic 和 Producer Group
    // 例如：
    // public static final String COUPON_TOPIC = "coupon-topic";
    // public static final String COUPON_PRODUCER_GROUP = "coupon-producer-group";
}


