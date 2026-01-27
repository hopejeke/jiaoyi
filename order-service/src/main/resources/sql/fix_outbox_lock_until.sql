-- ============================================
-- 修复 outbox 表缺少 lock_until 字段的问题
-- ============================================
-- 此脚本为所有 outbox_XX 表添加 lock_until 字段（如果不存在）
-- 同时确保所有 doordash_retry_task_XX 表都存在

USE jiaoyi_order_0;

-- 为 outbox_00..outbox_31 添加 lock_until 字段
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_lock_until_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    DECLARE done INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        -- 检查 lock_until 字段是否存在
        SET @col_exists = (
            SELECT COUNT(*) 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
            AND TABLE_NAME = table_name 
            AND COLUMN_NAME = 'lock_until'
        );
        
        IF @col_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD COLUMN lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('✓ 已为 ', table_name, ' 添加 lock_until 字段') AS result;
        ELSE
            SELECT CONCAT('✓ ', table_name, ' 已存在 lock_until 字段，跳过') AS result;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_lock_until_to_outbox();
DROP PROCEDURE IF EXISTS add_lock_until_to_outbox;

-- 确保所有 doordash_retry_task_XX 表都存在
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS create_missing_doordash_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS ', table_name, ' (',
            'id BIGINT NOT NULL PRIMARY KEY COMMENT ''任务ID（雪花算法生成）'', ',
            'order_id BIGINT NOT NULL COMMENT ''订单ID（关联orders.id）'', ',
            'merchant_id VARCHAR(50) NOT NULL COMMENT ''商户ID'', ',
            'shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于brandId计算）'', ',
            'payment_id BIGINT COMMENT ''支付ID（关联payments.id）'', ',
            'status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''任务状态：PENDING-待重试，RETRYING-重试中，SUCCESS-成功，FAILED-失败，MANUAL-需要人工介入'', ',
            'retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'', ',
            'max_retry_count INT NOT NULL DEFAULT 3 COMMENT ''最大重试次数'', ',
            'error_message TEXT COMMENT ''错误信息（最后一次失败的错误信息）'', ',
            'error_stack TEXT COMMENT ''错误堆栈（最后一次失败的错误堆栈）'', ',
            'next_retry_time DATETIME COMMENT ''下次重试时间（用于延迟重试）'', ',
            'last_retry_time DATETIME COMMENT ''最后重试时间'', ',
            'success_time DATETIME COMMENT ''成功时间（创建成功时记录）'', ',
            'manual_intervention_time DATETIME COMMENT ''人工介入时间（标记为需要人工介入时记录）'', ',
            'manual_intervention_note TEXT COMMENT ''人工介入备注'', ',
            'create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ',
            'update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'', ',
            'INDEX idx_order_id (order_id), ',
            'INDEX idx_shard_id (shard_id), ',
            'INDEX idx_merchant_id (merchant_id), ',
            'INDEX idx_status (status), ',
            'INDEX idx_next_retry_time (next_retry_time), ',
            'INDEX idx_create_time (create_time)',
            ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''DoorDash重试任务表_库0_分片', LPAD(i, 2, '0'), '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL create_missing_doordash_tables();
DROP PROCEDURE IF EXISTS create_missing_doordash_tables;

-- ============================================
-- 对 jiaoyi_order_1 执行相同操作
-- ============================================
USE jiaoyi_order_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_lock_until_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        SET @col_exists = (
            SELECT COUNT(*) 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_order_1' 
            AND TABLE_NAME = table_name 
            AND COLUMN_NAME = 'lock_until'
        );
        
        IF @col_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD COLUMN lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_lock_until_to_outbox();
DROP PROCEDURE IF EXISTS add_lock_until_to_outbox;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS create_missing_doordash_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS ', table_name, ' (',
            'id BIGINT NOT NULL PRIMARY KEY COMMENT ''任务ID（雪花算法生成）'', ',
            'order_id BIGINT NOT NULL COMMENT ''订单ID（关联orders.id）'', ',
            'merchant_id VARCHAR(50) NOT NULL COMMENT ''商户ID'', ',
            'shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于brandId计算）'', ',
            'payment_id BIGINT COMMENT ''支付ID（关联payments.id）'', ',
            'status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''任务状态：PENDING-待重试，RETRYING-重试中，SUCCESS-成功，FAILED-失败，MANUAL-需要人工介入'', ',
            'retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'', ',
            'max_retry_count INT NOT NULL DEFAULT 3 COMMENT ''最大重试次数'', ',
            'error_message TEXT COMMENT ''错误信息（最后一次失败的错误信息）'', ',
            'error_stack TEXT COMMENT ''错误堆栈（最后一次失败的错误堆栈）'', ',
            'next_retry_time DATETIME COMMENT ''下次重试时间（用于延迟重试）'', ',
            'last_retry_time DATETIME COMMENT ''最后重试时间'', ',
            'success_time DATETIME COMMENT ''成功时间（创建成功时记录）'', ',
            'manual_intervention_time DATETIME COMMENT ''人工介入时间（标记为需要人工介入时记录）'', ',
            'manual_intervention_note TEXT COMMENT ''人工介入备注'', ',
            'create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ',
            'update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'', ',
            'INDEX idx_order_id (order_id), ',
            'INDEX idx_shard_id (shard_id), ',
            'INDEX idx_merchant_id (merchant_id), ',
            'INDEX idx_status (status), ',
            'INDEX idx_next_retry_time (next_retry_time), ',
            'INDEX idx_create_time (create_time)',
            ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''DoorDash重试任务表_库1_分片', LPAD(i, 2, '0'), '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL create_missing_doordash_tables();
DROP PROCEDURE IF EXISTS create_missing_doordash_tables;

-- ============================================
-- 对 jiaoyi_order_2 执行相同操作
-- ============================================
USE jiaoyi_order_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_lock_until_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        SET @col_exists = (
            SELECT COUNT(*) 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_order_2' 
            AND TABLE_NAME = table_name 
            AND COLUMN_NAME = 'lock_until'
        );
        
        IF @col_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD COLUMN lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_lock_until_to_outbox();
DROP PROCEDURE IF EXISTS add_lock_until_to_outbox;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS create_missing_doordash_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS ', table_name, ' (',
            'id BIGINT NOT NULL PRIMARY KEY COMMENT ''任务ID（雪花算法生成）'', ',
            'order_id BIGINT NOT NULL COMMENT ''订单ID（关联orders.id）'', ',
            'merchant_id VARCHAR(50) NOT NULL COMMENT ''商户ID'', ',
            'shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于brandId计算）'', ',
            'payment_id BIGINT COMMENT ''支付ID（关联payments.id）'', ',
            'status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''任务状态：PENDING-待重试，RETRYING-重试中，SUCCESS-成功，FAILED-失败，MANUAL-需要人工介入'', ',
            'retry_count INT NOT NULL DEFAULT 0 COMMENT ''重试次数'', ',
            'max_retry_count INT NOT NULL DEFAULT 3 COMMENT ''最大重试次数'', ',
            'error_message TEXT COMMENT ''错误信息（最后一次失败的错误信息）'', ',
            'error_stack TEXT COMMENT ''错误堆栈（最后一次失败的错误堆栈）'', ',
            'next_retry_time DATETIME COMMENT ''下次重试时间（用于延迟重试）'', ',
            'last_retry_time DATETIME COMMENT ''最后重试时间'', ',
            'success_time DATETIME COMMENT ''成功时间（创建成功时记录）'', ',
            'manual_intervention_time DATETIME COMMENT ''人工介入时间（标记为需要人工介入时记录）'', ',
            'manual_intervention_note TEXT COMMENT ''人工介入备注'', ',
            'create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ',
            'update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'', ',
            'INDEX idx_order_id (order_id), ',
            'INDEX idx_shard_id (shard_id), ',
            'INDEX idx_merchant_id (merchant_id), ',
            'INDEX idx_status (status), ',
            'INDEX idx_next_retry_time (next_retry_time), ',
            'INDEX idx_create_time (create_time)',
            ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''DoorDash重试任务表_库2_分片', LPAD(i, 2, '0'), '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL create_missing_doordash_tables();
DROP PROCEDURE IF EXISTS create_missing_doordash_tables;

SELECT '✓ 所有修复完成！' AS result;



