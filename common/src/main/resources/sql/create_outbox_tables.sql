-- ============================================
-- Outbox 表建表 SQL
-- ============================================
-- 说明：
-- 1. 订单服务使用 order_outbox 表
-- 2. 库存服务使用 stock_outbox 表
-- 3. 两张表结构完全一致，只表名不同
-- ============================================

-- 订单服务的 outbox 表
CREATE TABLE IF NOT EXISTS order_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）',
    shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）',
    payload TEXT NOT NULL COMMENT '任务负载（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）',
    lock_time DATETIME COMMENT '锁定时间',
    last_error TEXT COMMENT '最后错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME COMMENT '完成时间（SUCCESS 时记录）',
    message_body TEXT COMMENT '消息体（兼容旧字段，新版本使用 payload）',
    error_message TEXT COMMENT '错误信息（兼容旧字段）',
    sent_at DATETIME COMMENT '发送时间（兼容旧字段）',
    UNIQUE KEY uk_type_biz (type, biz_key),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_next_retry_time (next_retry_time),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单服务可靠任务表（Outbox Pattern）';

-- 库存服务的 outbox 表
CREATE TABLE IF NOT EXISTS stock_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）',
    shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）',
    payload TEXT NOT NULL COMMENT '任务负载（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）',
    lock_time DATETIME COMMENT '锁定时间',
    last_error TEXT COMMENT '最后错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME COMMENT '完成时间（SUCCESS 时记录）',
    message_body TEXT COMMENT '消息体（兼容旧字段，新版本使用 payload）',
    error_message TEXT COMMENT '错误信息（兼容旧字段）',
    sent_at DATETIME COMMENT '发送时间（兼容旧字段）',
    UNIQUE KEY uk_type_biz (type, biz_key),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_next_retry_time (next_retry_time),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存服务可靠任务表（Outbox Pattern）';



