package com.jiaoyi.product.config;

import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 */
@Configuration
public class RocketMQConfig {

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
    
    // 库存同步到 POS 相关 Topic（原 POI 库存已合并到 Inventory，沿用同一 topic 便于 POS 消费）
    public static final String INVENTORY_STOCK_SYNC_TOPIC = "inventory-stock-sync-topic";
    public static final String INVENTORY_STOCK_SYNC_TAG = "sync-to-pos";
    public static final String INVENTORY_STOCK_SYNC_CONSUMER_GROUP = "inventory-stock-sync-consumer-group";
}

