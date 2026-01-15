-- ============================================
-- 创建分片表脚本（商品服务）
-- ============================================
-- 说明：
-- 1. 此脚本用于创建 jiaoyi_product_0/1/2 数据库并在每个库中创建分片表
-- 2. store_products 表：store_products_0..store_products_2（每库3张表）
-- 3. product_sku 表：product_sku_0..product_sku_2（每库3张表）
-- 4. inventory 表：inventory_0..inventory_2（每库3张表）
-- 5. outbox 表：outbox（每库1张表，统一表名，通过数据库隔离）
-- ============================================

-- ============================================
-- 步骤 1：创建数据库
-- ============================================
CREATE DATABASE IF NOT EXISTS jiaoyi_product_0 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS jiaoyi_product_1 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS jiaoyi_product_2 
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- 步骤 2：在 jiaoyi_product_0 数据库中创建表
-- ============================================
USE jiaoyi_product_0;

-- 创建 store_products_0..store_products_2 表
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_store_products_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('store_products_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                price DECIMAL(10,2),
                category VARCHAR(100),
                image_url VARCHAR(500),
                is_active TINYINT(1) DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_category (category),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_store_products_tables();
DROP PROCEDURE IF EXISTS create_store_products_tables;

-- 创建 product_sku_0..product_sku_2 表
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_product_sku_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('product_sku_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL,
                sku_code VARCHAR(100) NOT NULL,
                name VARCHAR(255),
                price DECIMAL(10,2),
                stock INT DEFAULT 0,
                attributes TEXT,
                is_delete TINYINT(1) DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_product_id (product_id),
                INDEX idx_sku_code (sku_code),
                INDEX idx_is_delete (is_delete)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品SKU表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_product_sku_tables();
DROP PROCEDURE IF EXISTS create_product_sku_tables;

-- 创建 inventory_0..inventory_2 表
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_inventory_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                sku_id BIGINT,
                quantity INT DEFAULT 0,
                stock_mode VARCHAR(50) DEFAULT ''NORMAL'',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_product_id (product_id),
                INDEX idx_sku_id (sku_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''库存表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_inventory_tables();
DROP PROCEDURE IF EXISTS create_inventory_tables;

-- 创建 outbox 表（统一表名，通过数据库隔离）
CREATE TABLE IF NOT EXISTS outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）',
    sharding_key VARCHAR(100) COMMENT '通用分片键（业务方可以存 merchantId, storeId, userId 等）',
    shard_id INT COMMENT '分片ID（用于扫描优化，不再用于分库路由，保留用于兼容）',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）',
    payload TEXT NOT NULL COMMENT '任务负载（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）',
    lock_time DATETIME COMMENT '锁定时间',
    lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）',
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
    INDEX idx_sharding_key (sharding_key),
    INDEX idx_sharding_key_status (sharding_key, status),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time),
    INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
    INDEX idx_cleanup (shard_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠任务表（Outbox Pattern）';

-- ============================================
-- jiaoyi_product_1 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_store_products_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('store_products_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                price DECIMAL(10,2),
                category VARCHAR(100),
                image_url VARCHAR(500),
                is_active TINYINT(1) DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_category (category),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_store_products_tables();
DROP PROCEDURE IF EXISTS create_store_products_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_product_sku_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('product_sku_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL,
                sku_code VARCHAR(100) NOT NULL,
                name VARCHAR(255),
                price DECIMAL(10,2),
                stock INT DEFAULT 0,
                attributes TEXT,
                is_delete TINYINT(1) DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_product_id (product_id),
                INDEX idx_sku_code (sku_code),
                INDEX idx_is_delete (is_delete)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品SKU表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_product_sku_tables();
DROP PROCEDURE IF EXISTS create_product_sku_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_inventory_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                sku_id BIGINT,
                quantity INT DEFAULT 0,
                stock_mode VARCHAR(50) DEFAULT ''NORMAL'',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_product_id (product_id),
                INDEX idx_sku_id (sku_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''库存表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_inventory_tables();
DROP PROCEDURE IF EXISTS create_inventory_tables;

CREATE TABLE IF NOT EXISTS outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）',
    sharding_key VARCHAR(100) COMMENT '通用分片键（业务方可以存 merchantId, storeId, userId 等）',
    shard_id INT COMMENT '分片ID（用于扫描优化，不再用于分库路由，保留用于兼容）',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）',
    payload TEXT NOT NULL COMMENT '任务负载（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）',
    lock_time DATETIME COMMENT '锁定时间',
    lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）',
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
    INDEX idx_sharding_key (sharding_key),
    INDEX idx_sharding_key_status (sharding_key, status),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time),
    INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
    INDEX idx_cleanup (shard_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠任务表（Outbox Pattern）';

-- ============================================
-- 步骤 4：在 jiaoyi_product_2 数据库中创建表
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_store_products_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('store_products_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                price DECIMAL(10,2),
                category VARCHAR(100),
                image_url VARCHAR(500),
                is_active TINYINT(1) DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_category (category),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_store_products_tables();
DROP PROCEDURE IF EXISTS create_store_products_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_product_sku_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('product_sku_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL,
                sku_code VARCHAR(100) NOT NULL,
                name VARCHAR(255),
                price DECIMAL(10,2),
                stock INT DEFAULT 0,
                attributes TEXT,
                is_delete TINYINT(1) DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_product_id (product_id),
                INDEX idx_sku_code (sku_code),
                INDEX idx_is_delete (is_delete)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''商品SKU表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_product_sku_tables();
DROP PROCEDURE IF EXISTS create_product_sku_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_inventory_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                sku_id BIGINT,
                quantity INT DEFAULT 0,
                stock_mode VARCHAR(50) DEFAULT ''NORMAL'',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_store_id (store_id),
                INDEX idx_product_id (product_id),
                INDEX idx_sku_id (sku_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''库存表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_inventory_tables();
DROP PROCEDURE IF EXISTS create_inventory_tables;

CREATE TABLE IF NOT EXISTS outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）',
    biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）',
    sharding_key VARCHAR(100) COMMENT '通用分片键（业务方可以存 merchantId, storeId, userId 等）',
    shard_id INT COMMENT '分片ID（用于扫描优化，不再用于分库路由，保留用于兼容）',
    topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）',
    tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）',
    payload TEXT NOT NULL COMMENT '任务负载（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）',
    lock_time DATETIME COMMENT '锁定时间',
    lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）',
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
    INDEX idx_sharding_key (sharding_key),
    INDEX idx_sharding_key_status (sharding_key, status),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_lock_owner (lock_owner),
    INDEX idx_status_next_retry (status, next_retry_time),
    INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
    INDEX idx_cleanup (shard_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠任务表（Outbox Pattern）';

