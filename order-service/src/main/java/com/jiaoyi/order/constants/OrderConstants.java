package com.jiaoyi.order.constants;

import java.math.BigDecimal;

/**
 * 订单业务常量
 */
public final class OrderConstants {

    private OrderConstants() {
        throw new IllegalStateException("Constant class");
    }

    // ========== 订单超时配置 ==========
    /**
     * 订单超时时间（分钟）
     */
    public static final int ORDER_TIMEOUT_MINUTES = 30;

    /**
     * 订单自动取消延迟时间（分钟）
     */
    public static final int ORDER_AUTO_CANCEL_DELAY_MINUTES = 30;

    // ========== 订单限制 ==========
    /**
     * 单个订单最大商品数量
     */
    public static final int MAX_ORDER_ITEMS = 100;

    /**
     * 最小订单金额
     */
    public static final BigDecimal MIN_ORDER_AMOUNT = new BigDecimal("0.01");

    /**
     * 最大订单金额
     */
    public static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("999999.99");

    // ========== 分布式锁配置 ==========
    /**
     * 用户级锁等待时间（秒）
     */
    public static final int USER_LOCK_WAIT_SECONDS = 5;

    /**
     * 用户级锁持有时间（秒）
     */
    public static final int USER_LOCK_LEASE_SECONDS = 20;

    /**
     * 订单内容级锁等待时间（秒）
     */
    public static final int CONTENT_LOCK_WAIT_SECONDS = 3;

    /**
     * 订单内容级锁持有时间（秒）
     */
    public static final int CONTENT_LOCK_LEASE_SECONDS = 15;

    /**
     * 支付创建锁等待时间（秒）
     */
    public static final int PAYMENT_LOCK_WAIT_SECONDS = 3;

    /**
     * 支付创建锁持有时间（秒）
     */
    public static final int PAYMENT_LOCK_LEASE_SECONDS = 30;

    /**
     * 支付回调锁等待时间（秒）
     */
    public static final int PAYMENT_CALLBACK_LOCK_WAIT_SECONDS = 3;

    /**
     * 支付回调锁持有时间（秒）
     */
    public static final int PAYMENT_CALLBACK_LOCK_LEASE_SECONDS = 30;

    // ========== Redis Key前缀 ==========
    /**
     * 订单创建用户级锁Key前缀
     */
    public static final String ORDER_CREATE_USER_LOCK_PREFIX = "order:create:user:";

    /**
     * 订单创建内容级锁Key前缀
     */
    public static final String ORDER_CREATE_CONTENT_LOCK_PREFIX = "order:create:content:";

    /**
     * 支付创建锁Key前缀
     */
    public static final String PAYMENT_CREATE_LOCK_PREFIX = "payment:create:";

    /**
     * 支付回调锁Key前缀
     */
    public static final String PAYMENT_CALLBACK_LOCK_PREFIX = "stripe:webhook:payment:success:";

    // ========== 重试配置 ==========
    /**
     * 库存解锁最大重试次数
     */
    public static final int STOCK_UNLOCK_MAX_RETRIES = 3;

    /**
     * 支付回调最大重试次数
     */
    public static final int PAYMENT_CALLBACK_MAX_RETRIES = 3;

    /**
     * Outbox任务最大重试次数
     */
    public static final int OUTBOX_MAX_RETRY_COUNT = 20;

    // ========== 价格配置 ==========
    /**
     * 价格小数位数
     */
    public static final int PRICE_DECIMAL_SCALE = 2;

    /**
     * DoorDash配送费缓冲比例
     */
    public static final BigDecimal DOORDASH_FEE_BUFFER_RATE = new BigDecimal("0.10");

    // ========== 日期时间格式 ==========
    /**
     * 时间格式：HH:mm
     */
    public static final String TIME_FORMAT_HH_MM = "HH:mm";

    /**
     * 日期时间格式：yyyy-MM-dd HH:mm:ss
     */
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // ========== 默认值 ==========
    /**
     * 默认版本号
     */
    public static final Long DEFAULT_VERSION = 1L;

    /**
     * 默认状态：已下单
     */
    public static final Integer DEFAULT_ORDER_STATUS = 1;

    /**
     * 默认本地状态：已下单
     */
    public static final Integer DEFAULT_LOCAL_STATUS = 1;

    /**
     * 默认厨房状态：待送厨
     */
    public static final Integer DEFAULT_KITCHEN_STATUS = 1;
}
