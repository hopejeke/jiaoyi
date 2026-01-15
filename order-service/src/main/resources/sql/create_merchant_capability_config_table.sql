-- 创建商户高峰拒单配置表
-- 用于存储每个商户的高峰拒单配置参数和当前状态
-- 注意：此表需要分片，分片键为 merchant_id

-- 在 jiaoyi_0, jiaoyi_1, jiaoyi_2 三个数据库中分别创建3个分片表
-- 表名：merchant_capability_config_0, merchant_capability_config_1, merchant_capability_config_2

CREATE TABLE IF NOT EXISTS merchant_capability_config_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用',
    qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）',
    time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）',
    closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）',
    next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）',
    re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）',
    operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动',
    operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动',
    operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动',
    operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_id (merchant_id),
    INDEX idx_enable (enable),
    INDEX idx_next_open_at (next_open_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表_库0_分片0';

CREATE TABLE IF NOT EXISTS merchant_capability_config_1 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用',
    qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）',
    time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）',
    closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）',
    next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）',
    re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）',
    operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动',
    operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动',
    operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动',
    operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_id (merchant_id),
    INDEX idx_enable (enable),
    INDEX idx_next_open_at (next_open_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表_库0_分片1';

CREATE TABLE IF NOT EXISTS merchant_capability_config_2 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用',
    qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）',
    time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）',
    closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）',
    next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）',
    re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）',
    operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动',
    operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动',
    operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动',
    operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_id (merchant_id),
    INDEX idx_enable (enable),
    INDEX idx_next_open_at (next_open_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表_库0_分片2';

-- 注意：需要在所有分片数据库（jiaoyi_0, jiaoyi_1, jiaoyi_2）中执行此脚本
-- 每个数据库创建3个分片表，共9个表



