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
    
    /**
     * 支付成功Topic
     */
    public static final String PAYMENT_SUCCEEDED_TOPIC = "payment-succeeded-topic";
    
    /**
     * 支付成功Consumer Group
     */
    public static final String PAYMENT_SUCCEEDED_CONSUMER_GROUP = "payment-succeeded-consumer-group";
    
    /**
     * 支付成功Tag
     */
    public static final String PAYMENT_SUCCEEDED_TAG = "succeeded";
    
    /**
     * 库存扣减Topic
     */
    public static final String DEDUCT_STOCK_TOPIC = "deduct-stock-topic";
    
    /**
     * 库存扣减Consumer Group
     */
    public static final String DEDUCT_STOCK_CONSUMER_GROUP = "deduct-stock-consumer-group";
    
    /**
     * 库存扣减Tag
     */
    public static final String DEDUCT_STOCK_TAG = "deduct";
}


