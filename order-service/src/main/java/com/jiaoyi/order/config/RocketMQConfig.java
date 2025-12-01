package com.jiaoyi.order.config;

/**
 * RocketMQ配置常量
 * 订单服务相关的Topic和Consumer Group
 */
public class RocketMQConfig {
    
    /**
     * 订单超时Topic
     */
    public static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    
    /**
     * 订单超时Consumer Group
     */
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "order-timeout-consumer-group";
    
    /**
     * 订单超时Tag
     */
    public static final String ORDER_TIMEOUT_TAG = "timeout";
    
    /**
     * 订单超时Producer Group
     */
    public static final String ORDER_TIMEOUT_PRODUCER_GROUP = "order-timeout-producer-group";
}


