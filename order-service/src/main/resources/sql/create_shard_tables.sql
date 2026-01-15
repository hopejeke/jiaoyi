-- ============================================
-- 创建分片表脚本（订单服务，每库32张表）
-- ============================================
-- 说明：
-- 1. 此脚本用于创建 jiaoyi_order_0/1/2 数据库并在每个库中创建 32 张物理表
-- 2. orders 表：orders_00..orders_31
-- 3. outbox 表：outbox_00..outbox_31（统一表名，通过数据库隔离）
-- 4. 执行前请先确保已创建 shard_bucket_route 路由表
-- ============================================

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
-- 步骤 2：在 jiaoyi_order_0 数据库中创建表
-- ============================================
USE jiaoyi_order_0;

-- 创建 orders_00..orders_31 表
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_orders_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT NOT NULL PRIMARY KEY COMMENT ''订单ID（雪花算法）'',
                merchant_id VARCHAR(100) NOT NULL COMMENT ''餐馆ID（用于分片）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算）'',
                user_id BIGINT NOT NULL COMMENT ''用户ID'',
                order_type VARCHAR(50) NOT NULL COMMENT ''订单类型（DINE_IN/TAKEOUT/DELIVERY）'',
                status INT NOT NULL DEFAULT 1 COMMENT ''订单状态：1-已下单，100-已支付，-1-已取消等'',
                local_status INT NOT NULL DEFAULT 1 COMMENT ''本地订单状态：1-已下单，100-成功，200-支付失败等'',
                kitchen_status INT DEFAULT 1 COMMENT ''厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成'',
                order_price TEXT COMMENT ''订单价格信息（JSON）'',
                customer_info TEXT COMMENT ''客户信息（JSON）'',
                delivery_address TEXT COMMENT ''配送地址（JSON）'',
                notes TEXT COMMENT ''备注'',
                pos_order_id VARCHAR(100) COMMENT ''POS系统订单ID'',
                payment_method VARCHAR(50) COMMENT ''支付方式'',
                payment_status VARCHAR(50) COMMENT ''支付状态'',
                stripe_payment_intent_id VARCHAR(100) COMMENT ''Stripe支付意图ID'',
                refund_amount DECIMAL(10,2) DEFAULT 0 COMMENT ''退款金额'',
                refund_reason TEXT COMMENT ''退款原因'',
                version BIGINT NOT NULL DEFAULT 1 COMMENT ''版本号（用于乐观锁）'',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                delivery_id VARCHAR(100) COMMENT ''DoorDash 配送ID'',
                INDEX idx_merchant_id (merchant_id),
                INDEX idx_shard_id (shard_id),
                INDEX idx_user_id (user_id),
                INDEX idx_status (status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''订单表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_orders_tables();
DROP PROCEDURE IF EXISTS create_orders_tables;

-- 创建 outbox_00..outbox_31 表（统一表名，通过数据库隔离）
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_outbox_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT ''主键ID'',
                type VARCHAR(100) NOT NULL COMMENT ''任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）'',
                biz_key VARCHAR(255) NOT NULL COMMENT ''业务键（如：orderId，用于唯一约束和幂等）'',
                sharding_key VARCHAR(100) COMMENT ''通用分片键（业务方可以存 merchantId, storeId, userId 等）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算，用于分库分表路由）'',
                topic VARCHAR(100) COMMENT ''RocketMQ Topic（MQ 类型任务使用）'',
                tag VARCHAR(50) COMMENT ''RocketMQ Tag（MQ 类型任务使用）'',
                message_key VARCHAR(255) COMMENT ''消息Key（用于消息追踪，MQ 类型任务使用）'',
                payload TEXT NOT NULL COMMENT ''任务负载（JSON格式）'',
                status VARCHAR(20) NOT NULL DEFAULT ''NEW'' COMMENT ''状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信'',
                retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'',
                next_retry_time DATETIME COMMENT ''下次重试时间'',
                lock_owner VARCHAR(100) COMMENT ''锁持有者（实例ID，用于多实例抢锁）'',
                lock_time DATETIME COMMENT ''锁定时间'',
                lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）'',
                last_error TEXT COMMENT ''最后错误信息'',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                completed_at DATETIME COMMENT ''完成时间（SUCCESS 时记录）'',
                message_body TEXT COMMENT ''消息体（兼容旧字段，新版本使用 payload）'',
                error_message TEXT COMMENT ''错误信息（兼容旧字段）'',
                sent_at DATETIME COMMENT ''发送时间（兼容旧字段）'',
                UNIQUE KEY uk_type_biz (type, biz_key),
                INDEX idx_status (status),
                INDEX idx_created_at (created_at),
                INDEX idx_next_retry_time (next_retry_time),
                INDEX idx_shard_id (shard_id),
                INDEX idx_shard_id_status (shard_id, status),
                INDEX idx_lock_owner (lock_owner),
                INDEX idx_status_next_retry (status, next_retry_time),
                INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
                INDEX idx_cleanup (shard_id, status, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''可靠任务表（Outbox Pattern）_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_outbox_tables();
DROP PROCEDURE IF EXISTS create_outbox_tables;

-- ============================================
-- jiaoyi_order_1 数据库（重复上述操作）
-- ============================================
USE jiaoyi_order_1;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_orders_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT NOT NULL PRIMARY KEY COMMENT ''订单ID（雪花算法）'',
                merchant_id VARCHAR(100) NOT NULL COMMENT ''餐馆ID（用于分片）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算）'',
                user_id BIGINT NOT NULL COMMENT ''用户ID'',
                order_type VARCHAR(50) NOT NULL COMMENT ''订单类型（DINE_IN/TAKEOUT/DELIVERY）'',
                status INT NOT NULL DEFAULT 1 COMMENT ''订单状态：1-已下单，100-已支付，-1-已取消等'',
                local_status INT NOT NULL DEFAULT 1 COMMENT ''本地订单状态：1-已下单，100-成功，200-支付失败等'',
                kitchen_status INT DEFAULT 1 COMMENT ''厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成'',
                order_price TEXT COMMENT ''订单价格信息（JSON）'',
                customer_info TEXT COMMENT ''客户信息（JSON）'',
                delivery_address TEXT COMMENT ''配送地址（JSON）'',
                notes TEXT COMMENT ''备注'',
                pos_order_id VARCHAR(100) COMMENT ''POS系统订单ID'',
                payment_method VARCHAR(50) COMMENT ''支付方式'',
                payment_status VARCHAR(50) COMMENT ''支付状态'',
                stripe_payment_intent_id VARCHAR(100) COMMENT ''Stripe支付意图ID'',
                refund_amount DECIMAL(10,2) DEFAULT 0 COMMENT ''退款金额'',
                refund_reason TEXT COMMENT ''退款原因'',
                version BIGINT NOT NULL DEFAULT 1 COMMENT ''版本号（用于乐观锁）'',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                delivery_id VARCHAR(100) COMMENT ''DoorDash 配送ID'',
                INDEX idx_merchant_id (merchant_id),
                INDEX idx_shard_id (shard_id),
                INDEX idx_user_id (user_id),
                INDEX idx_status (status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''订单表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_orders_tables();
DROP PROCEDURE IF EXISTS create_orders_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_outbox_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT ''主键ID'',
                type VARCHAR(100) NOT NULL COMMENT ''任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）'',
                biz_key VARCHAR(255) NOT NULL COMMENT ''业务键（如：orderId，用于唯一约束和幂等）'',
                sharding_key VARCHAR(100) COMMENT ''通用分片键（业务方可以存 merchantId, storeId, userId 等）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算，用于分库分表路由）'',
                topic VARCHAR(100) COMMENT ''RocketMQ Topic（MQ 类型任务使用）'',
                tag VARCHAR(50) COMMENT ''RocketMQ Tag（MQ 类型任务使用）'',
                message_key VARCHAR(255) COMMENT ''消息Key（用于消息追踪，MQ 类型任务使用）'',
                payload TEXT NOT NULL COMMENT ''任务负载（JSON格式）'',
                status VARCHAR(20) NOT NULL DEFAULT ''NEW'' COMMENT ''状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信'',
                retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'',
                next_retry_time DATETIME COMMENT ''下次重试时间'',
                lock_owner VARCHAR(100) COMMENT ''锁持有者（实例ID，用于多实例抢锁）'',
                lock_time DATETIME COMMENT ''锁定时间'',
                lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）'',
                last_error TEXT COMMENT ''最后错误信息'',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                completed_at DATETIME COMMENT ''完成时间（SUCCESS 时记录）'',
                message_body TEXT COMMENT ''消息体（兼容旧字段，新版本使用 payload）'',
                error_message TEXT COMMENT ''错误信息（兼容旧字段）'',
                sent_at DATETIME COMMENT ''发送时间（兼容旧字段）'',
                UNIQUE KEY uk_type_biz (type, biz_key),
                INDEX idx_status (status),
                INDEX idx_created_at (created_at),
                INDEX idx_next_retry_time (next_retry_time),
                INDEX idx_shard_id (shard_id),
                INDEX idx_shard_id_status (shard_id, status),
                INDEX idx_lock_owner (lock_owner),
                INDEX idx_status_next_retry (status, next_retry_time),
                INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
                INDEX idx_cleanup (shard_id, status, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''可靠任务表（Outbox Pattern）_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_outbox_tables();
DROP PROCEDURE IF EXISTS create_outbox_tables;

-- ============================================
-- 步骤 4：在 jiaoyi_order_2 数据库中创建表
-- ============================================
USE jiaoyi_order_2;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_orders_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT NOT NULL PRIMARY KEY COMMENT ''订单ID（雪花算法）'',
                merchant_id VARCHAR(100) NOT NULL COMMENT ''餐馆ID（用于分片）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算）'',
                user_id BIGINT NOT NULL COMMENT ''用户ID'',
                order_type VARCHAR(50) NOT NULL COMMENT ''订单类型（DINE_IN/TAKEOUT/DELIVERY）'',
                status INT NOT NULL DEFAULT 1 COMMENT ''订单状态：1-已下单，100-已支付，-1-已取消等'',
                local_status INT NOT NULL DEFAULT 1 COMMENT ''本地订单状态：1-已下单，100-成功，200-支付失败等'',
                kitchen_status INT DEFAULT 1 COMMENT ''厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成'',
                order_price TEXT COMMENT ''订单价格信息（JSON）'',
                customer_info TEXT COMMENT ''客户信息（JSON）'',
                delivery_address TEXT COMMENT ''配送地址（JSON）'',
                notes TEXT COMMENT ''备注'',
                pos_order_id VARCHAR(100) COMMENT ''POS系统订单ID'',
                payment_method VARCHAR(50) COMMENT ''支付方式'',
                payment_status VARCHAR(50) COMMENT ''支付状态'',
                stripe_payment_intent_id VARCHAR(100) COMMENT ''Stripe支付意图ID'',
                refund_amount DECIMAL(10,2) DEFAULT 0 COMMENT ''退款金额'',
                refund_reason TEXT COMMENT ''退款原因'',
                version BIGINT NOT NULL DEFAULT 1 COMMENT ''版本号（用于乐观锁）'',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                delivery_id VARCHAR(100) COMMENT ''DoorDash 配送ID'',
                INDEX idx_merchant_id (merchant_id),
                INDEX idx_shard_id (shard_id),
                INDEX idx_user_id (user_id),
                INDEX idx_status (status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''订单表_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_orders_tables();
DROP PROCEDURE IF EXISTS create_orders_tables;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_outbox_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT ''主键ID'',
                type VARCHAR(100) NOT NULL COMMENT ''任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）'',
                biz_key VARCHAR(255) NOT NULL COMMENT ''业务键（如：orderId，用于唯一约束和幂等）'',
                sharding_key VARCHAR(100) COMMENT ''通用分片键（业务方可以存 merchantId, storeId, userId 等）'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于 brandId 计算，用于分库分表路由）'',
                topic VARCHAR(100) COMMENT ''RocketMQ Topic（MQ 类型任务使用）'',
                tag VARCHAR(50) COMMENT ''RocketMQ Tag（MQ 类型任务使用）'',
                message_key VARCHAR(255) COMMENT ''消息Key（用于消息追踪，MQ 类型任务使用）'',
                payload TEXT NOT NULL COMMENT ''任务负载（JSON格式）'',
                status VARCHAR(20) NOT NULL DEFAULT ''NEW'' COMMENT ''状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信'',
                retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'',
                next_retry_time DATETIME COMMENT ''下次重试时间'',
                lock_owner VARCHAR(100) COMMENT ''锁持有者（实例ID，用于多实例抢锁）'',
                lock_time DATETIME COMMENT ''锁定时间'',
                lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）'',
                last_error TEXT COMMENT ''最后错误信息'',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                completed_at DATETIME COMMENT ''完成时间（SUCCESS 时记录）'',
                message_body TEXT COMMENT ''消息体（兼容旧字段，新版本使用 payload）'',
                error_message TEXT COMMENT ''错误信息（兼容旧字段）'',
                sent_at DATETIME COMMENT ''发送时间（兼容旧字段）'',
                UNIQUE KEY uk_type_biz (type, biz_key),
                INDEX idx_status (status),
                INDEX idx_created_at (created_at),
                INDEX idx_next_retry_time (next_retry_time),
                INDEX idx_shard_id (shard_id),
                INDEX idx_shard_id_status (shard_id, status),
                INDEX idx_lock_owner (lock_owner),
                INDEX idx_status_next_retry (status, next_retry_time),
                INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id),
                INDEX idx_cleanup (shard_id, status, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''可靠任务表（Outbox Pattern）_', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_outbox_tables();
DROP PROCEDURE IF EXISTS create_outbox_tables;

