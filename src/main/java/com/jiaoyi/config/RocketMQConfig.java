package com.jiaoyi.config;

import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 */
@Configuration
public class RocketMQConfig {

    // 订单超时相关Topic
    public static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    public static final String ORDER_TIMEOUT_TAG = "timeout";
    
    // 订单超时消费者组
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "order-timeout-consumer-group";
    
    // 商品缓存更新相关Topic
    public static final String PRODUCT_CACHE_UPDATE_TOPIC = "product-cache-update-topic";
    public static final String PRODUCT_CACHE_UPDATE_TAG = "update";
    
    // 商品缓存更新生产者组
    public static final String PRODUCT_CACHE_UPDATE_PRODUCER_GROUP = "product-cache-update-producer-group";
    
    // 商品缓存更新消费者组
    public static final String PRODUCT_CACHE_UPDATE_CONSUMER_GROUP = "product-cache-update-consumer-group";
    
    // 库存缓存更新相关Topic
    public static final String INVENTORY_CACHE_UPDATE_TOPIC = "inventory-cache-update-topic";
    public static final String INVENTORY_CACHE_UPDATE_TAG = "update";
    
    // 库存缓存更新生产者组
    public static final String INVENTORY_CACHE_UPDATE_PRODUCER_GROUP = "inventory-cache-update-producer-group";
    
    // 库存缓存更新消费者组
    public static final String INVENTORY_CACHE_UPDATE_CONSUMER_GROUP = "inventory-cache-update-consumer-group";
}
