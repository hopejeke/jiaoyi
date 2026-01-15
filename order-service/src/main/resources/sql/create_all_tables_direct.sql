-- ============================================
-- 快速创建所有订单服务表（32张表/库）
-- ============================================
-- 说明：
-- 1. 此脚本创建 jiaoyi_order_0/1/2 数据库
-- 2. 每个库创建 32 张表：orders_00..orders_31, outbox_00..outbox_31
-- 3. 会先删除旧格式的表（orders_0, orders_1, orders_2, orders_3 等）
-- 4. 直接执行此脚本即可
-- ============================================

-- ============================================
-- 步骤 0：删除旧格式的表（如果存在）
-- ============================================
-- 注意：此步骤会删除旧格式的表（orders_0, orders_1, orders_2, orders_3 等）
-- 如果这些表中有重要数据，请先备份！

-- 删除 jiaoyi_order_0 中的旧表
USE jiaoyi_order_0;
DROP TABLE IF EXISTS orders_0;
DROP TABLE IF EXISTS orders_1;
DROP TABLE IF EXISTS orders_2;
DROP TABLE IF EXISTS orders_3;
DROP TABLE IF EXISTS orders_4;
DROP TABLE IF EXISTS orders_5;
DROP TABLE IF EXISTS orders_6;
DROP TABLE IF EXISTS orders_7;
DROP TABLE IF EXISTS orders_8;
DROP TABLE IF EXISTS orders_9;
DROP TABLE IF EXISTS order_outbox_0;
DROP TABLE IF EXISTS order_outbox_1;
DROP TABLE IF EXISTS order_outbox_2;
DROP TABLE IF EXISTS order_outbox_3;
DROP TABLE IF EXISTS order_outbox_4;
DROP TABLE IF EXISTS order_outbox_5;
DROP TABLE IF EXISTS order_outbox_6;
DROP TABLE IF EXISTS order_outbox_7;
DROP TABLE IF EXISTS order_outbox_8;
DROP TABLE IF EXISTS order_outbox_9;
DROP TABLE IF EXISTS outbox_0;
DROP TABLE IF EXISTS outbox_1;
DROP TABLE IF EXISTS outbox_2;
DROP TABLE IF EXISTS outbox_3;
DROP TABLE IF EXISTS outbox_4;
DROP TABLE IF EXISTS outbox_5;
DROP TABLE IF EXISTS outbox_6;
DROP TABLE IF EXISTS outbox_7;
DROP TABLE IF EXISTS outbox_8;
DROP TABLE IF EXISTS outbox_9;

-- 删除 jiaoyi_order_1 中的旧表
USE jiaoyi_order_1;
DROP TABLE IF EXISTS orders_0;
DROP TABLE IF EXISTS orders_1;
DROP TABLE IF EXISTS orders_2;
DROP TABLE IF EXISTS orders_3;
DROP TABLE IF EXISTS orders_4;
DROP TABLE IF EXISTS orders_5;
DROP TABLE IF EXISTS orders_6;
DROP TABLE IF EXISTS orders_7;
DROP TABLE IF EXISTS orders_8;
DROP TABLE IF EXISTS orders_9;
DROP TABLE IF EXISTS order_outbox_0;
DROP TABLE IF EXISTS order_outbox_1;
DROP TABLE IF EXISTS order_outbox_2;
DROP TABLE IF EXISTS order_outbox_3;
DROP TABLE IF EXISTS order_outbox_4;
DROP TABLE IF EXISTS order_outbox_5;
DROP TABLE IF EXISTS order_outbox_6;
DROP TABLE IF EXISTS order_outbox_7;
DROP TABLE IF EXISTS order_outbox_8;
DROP TABLE IF EXISTS order_outbox_9;
DROP TABLE IF EXISTS outbox_0;
DROP TABLE IF EXISTS outbox_1;
DROP TABLE IF EXISTS outbox_2;
DROP TABLE IF EXISTS outbox_3;
DROP TABLE IF EXISTS outbox_4;
DROP TABLE IF EXISTS outbox_5;
DROP TABLE IF EXISTS outbox_6;
DROP TABLE IF EXISTS outbox_7;
DROP TABLE IF EXISTS outbox_8;
DROP TABLE IF EXISTS outbox_9;

-- 删除 jiaoyi_order_2 中的旧表
USE jiaoyi_order_2;
DROP TABLE IF EXISTS orders_0;
DROP TABLE IF EXISTS orders_1;
DROP TABLE IF EXISTS orders_2;
DROP TABLE IF EXISTS orders_3;
DROP TABLE IF EXISTS orders_4;
DROP TABLE IF EXISTS orders_5;
DROP TABLE IF EXISTS orders_6;
DROP TABLE IF EXISTS orders_7;
DROP TABLE IF EXISTS orders_8;
DROP TABLE IF EXISTS orders_9;
DROP TABLE IF EXISTS order_outbox_0;
DROP TABLE IF EXISTS order_outbox_1;
DROP TABLE IF EXISTS order_outbox_2;
DROP TABLE IF EXISTS order_outbox_3;
DROP TABLE IF EXISTS order_outbox_4;
DROP TABLE IF EXISTS order_outbox_5;
DROP TABLE IF EXISTS order_outbox_6;
DROP TABLE IF EXISTS order_outbox_7;
DROP TABLE IF EXISTS order_outbox_8;
DROP TABLE IF EXISTS order_outbox_9;
DROP TABLE IF EXISTS outbox_0;
DROP TABLE IF EXISTS outbox_1;
DROP TABLE IF EXISTS outbox_2;
DROP TABLE IF EXISTS outbox_3;
DROP TABLE IF EXISTS outbox_4;
DROP TABLE IF EXISTS outbox_5;
DROP TABLE IF EXISTS outbox_6;
DROP TABLE IF EXISTS outbox_7;
DROP TABLE IF EXISTS outbox_8;
DROP TABLE IF EXISTS outbox_9;

-- ============================================
-- 步骤 1：创建数据库
-- ============================================
CREATE DATABASE IF NOT EXISTS jiaoyi_order_0 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS jiaoyi_order_1 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS jiaoyi_order_2 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- 步骤 2：在 jiaoyi_order_0 中创建 orders 表（32张）
-- ============================================
USE jiaoyi_order_0;

CREATE TABLE IF NOT EXISTS orders_00 (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '订单ID（雪花算法）',
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID',
    shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_type VARCHAR(20) NOT NULL COMMENT '订单类型：PICKUP/DELIVERY/SELF_DINE_IN',
    status INT NOT NULL DEFAULT 1 COMMENT '订单状态：1-已下单，2-已支付，3-制作中，4-已完成，5-已取消，7-支付失败，8-配送中，9-待接单',
    local_status INT NOT NULL DEFAULT 1 COMMENT '本地订单状态：1-已下单，100-成功，200-支付失败',
    kitchen_status INT NOT NULL DEFAULT 1 COMMENT '厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成',
    order_price TEXT COMMENT '订单价格信息（JSON）',
    customer_info TEXT COMMENT '客户信息（JSON）',
    delivery_address TEXT COMMENT '配送地址（JSON）',
    notes TEXT COMMENT '备注',
    pos_order_id VARCHAR(100) COMMENT 'POS系统订单ID',
    payment_method VARCHAR(50) COMMENT '支付方式',
    payment_status VARCHAR(50) COMMENT '支付状态',
    stripe_payment_intent_id VARCHAR(200) COMMENT 'Stripe支付意图ID',
    refund_amount DECIMAL(10,2) COMMENT '退款金额',
    refund_reason VARCHAR(500) COMMENT '退款原因',
    delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_shard_id (shard_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_delivery_id (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表_00';

CREATE TABLE IF NOT EXISTS orders_01 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_02 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_03 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_04 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_05 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_06 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_07 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_08 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_09 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_10 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_11 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_12 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_13 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_14 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_15 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_16 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_17 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_18 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_19 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_20 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_21 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_22 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_23 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_24 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_25 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_26 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_27 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_28 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_29 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_30 LIKE orders_00;
CREATE TABLE IF NOT EXISTS orders_31 LIKE orders_00;

-- ============================================
-- 步骤 3：在 jiaoyi_order_0 中创建 outbox 表（32张）
-- ============================================
USE jiaoyi_order_0;

CREATE TABLE IF NOT EXISTS outbox_00 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键',
    sharding_key VARCHAR(100) COMMENT '通用分片键',
    shard_id INT NOT NULL COMMENT '分片ID（0-1023）',
    event_id VARCHAR(255) COMMENT '事件ID',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag',
    message_key VARCHAR(255) COMMENT '消息Key',
    payload TEXT NOT NULL COMMENT '任务负载（JSON）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者',
    lock_time DATETIME COMMENT '锁定时间',
    lock_until DATETIME COMMENT '锁过期时间',
    last_error TEXT COMMENT '最后错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME COMMENT '完成时间',
    message_body TEXT COMMENT '消息体（兼容）',
    error_message TEXT COMMENT '错误信息（兼容）',
    sent_at DATETIME COMMENT '发送时间（兼容）',
    UNIQUE KEY uk_type_biz (type, biz_key),
    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_next_retry_time (next_retry_time),
    INDEX idx_sharding_key (sharding_key),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time),
    INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
    INDEX idx_cleanup (shard_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠任务表_00';

CREATE TABLE IF NOT EXISTS outbox_01 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_02 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_03 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_04 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_05 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_06 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_07 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_08 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_09 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_10 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_11 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_12 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_13 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_14 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_15 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_16 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_17 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_18 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_19 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_20 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_21 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_22 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_23 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_24 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_25 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_26 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_27 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_28 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_29 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_30 LIKE outbox_00;
CREATE TABLE IF NOT EXISTS outbox_31 LIKE outbox_00;

-- ============================================
-- 步骤 4：在 jiaoyi_order_1 中创建表（复制 jiaoyi_order_0 的表结构）
-- ============================================
USE jiaoyi_order_1;

CREATE TABLE IF NOT EXISTS orders_00 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_01 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_02 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_03 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_04 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_05 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_06 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_07 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_08 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_09 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_10 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_11 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_12 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_13 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_14 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_15 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_16 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_17 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_18 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_19 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_20 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_21 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_22 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_23 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_24 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_25 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_26 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_27 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_28 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_29 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_30 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_31 LIKE jiaoyi_order_0.orders_00;

CREATE TABLE IF NOT EXISTS outbox_00 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_01 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_02 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_03 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_04 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_05 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_06 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_07 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_08 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_09 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_10 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_11 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_12 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_13 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_14 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_15 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_16 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_17 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_18 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_19 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_20 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_21 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_22 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_23 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_24 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_25 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_26 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_27 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_28 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_29 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_30 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_31 LIKE jiaoyi_order_0.outbox_00;

-- ============================================
-- 步骤 5：在 jiaoyi_order_2 中创建表（复制 jiaoyi_order_0 的表结构）
-- ============================================
USE jiaoyi_order_2;

CREATE TABLE IF NOT EXISTS orders_00 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_01 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_02 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_03 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_04 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_05 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_06 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_07 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_08 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_09 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_10 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_11 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_12 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_13 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_14 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_15 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_16 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_17 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_18 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_19 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_20 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_21 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_22 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_23 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_24 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_25 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_26 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_27 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_28 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_29 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_30 LIKE jiaoyi_order_0.orders_00;
CREATE TABLE IF NOT EXISTS orders_31 LIKE jiaoyi_order_0.orders_00;

CREATE TABLE IF NOT EXISTS outbox_00 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_01 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_02 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_03 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_04 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_05 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_06 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_07 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_08 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_09 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_10 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_11 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_12 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_13 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_14 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_15 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_16 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_17 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_18 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_19 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_20 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_21 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_22 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_23 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_24 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_25 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_26 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_27 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_28 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_29 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_30 LIKE jiaoyi_order_0.outbox_00;
CREATE TABLE IF NOT EXISTS outbox_31 LIKE jiaoyi_order_0.outbox_00;

-- ============================================
-- 完成
-- ============================================
SELECT '所有表创建完成！' AS result;

