-- ============================================
-- 为 payments 表添加唯一索引，防止并发创建多个支付记录
-- ============================================
-- 说明：
-- 1. 唯一索引：(order_id, type, status)
-- 2. 用于防止同一订单同时创建多个 PENDING 状态的支付记录
-- 3. 注意：此索引允许同一订单有多个 FAILED 记录（支付失败后可以重试）
-- 4. 但同一订单同一类型同一状态只能有一条记录（主要是防止 PENDING 重复）
-- 5. payments 表在每个数据库中有 3 个分片表：payments_0, payments_1, payments_2
-- ============================================

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_unique_index_payment_order_type_pending(db_name VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE type_exists INT DEFAULT 0;
    DECLARE status_exists INT DEFAULT 0;
    
    WHILE i < 3 DO
        SET table_name = CONCAT('payments_', i);

        -- 检查 order_id 字段是否存在
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';

        -- 检查 type 字段是否存在
        SELECT COUNT(*) INTO type_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'type';

        -- 检查 status 字段是否存在
        SELECT COUNT(*) INTO status_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'status';
        
        IF order_id_exists > 0 AND type_exists > 0 AND status_exists > 0 THEN
            -- 检查唯一索引是否已存在
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS 
            WHERE TABLE_SCHEMA = db_name 
              AND TABLE_NAME = table_name 
              AND INDEX_NAME = 'uk_order_type_status';
            
            -- 创建唯一索引（如果不存在）
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, 
                    ' ADD UNIQUE KEY uk_order_type_status (order_id, type, status) COMMENT ''防止同一订单同一类型同一状态重复创建支付记录''');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 创建唯一索引 uk_order_type_status 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': 唯一索引 uk_order_type_status 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': order_id、type 或 status 字段不存在，跳过索引创建') AS message;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- ============================================
-- 对每个分片数据库执行
-- ============================================

-- jiaoyi_0 数据库
USE jiaoyi_0;
CALL add_unique_index_payment_order_type_pending('jiaoyi_0');
DROP PROCEDURE IF EXISTS add_unique_index_payment_order_type_pending;

-- jiaoyi_1 数据库
USE jiaoyi_1;
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_unique_index_payment_order_type_pending(db_name VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE type_exists INT DEFAULT 0;
    DECLARE status_exists INT DEFAULT 0;
    
    WHILE i < 3 DO
        SET table_name = CONCAT('payments_', i);

        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';

        SELECT COUNT(*) INTO type_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'type';

        SELECT COUNT(*) INTO status_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'status';
        
        IF order_id_exists > 0 AND type_exists > 0 AND status_exists > 0 THEN
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS 
            WHERE TABLE_SCHEMA = db_name 
              AND TABLE_NAME = table_name 
              AND INDEX_NAME = 'uk_order_type_status';
            
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, 
                    ' ADD UNIQUE KEY uk_order_type_status (order_id, type, status) COMMENT ''防止同一订单同一类型同一状态重复创建支付记录''');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 创建唯一索引 uk_order_type_status 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': 唯一索引 uk_order_type_status 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': order_id、type 或 status 字段不存在，跳过索引创建') AS message;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_unique_index_payment_order_type_pending('jiaoyi_1');
DROP PROCEDURE IF EXISTS add_unique_index_payment_order_type_pending;

-- jiaoyi_2 数据库
USE jiaoyi_2;
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_unique_index_payment_order_type_pending(db_name VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE type_exists INT DEFAULT 0;
    DECLARE status_exists INT DEFAULT 0;
    
    WHILE i < 3 DO
        SET table_name = CONCAT('payments_', i);

        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';

        SELECT COUNT(*) INTO type_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'type';

        SELECT COUNT(*) INTO status_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'status';
        
        IF order_id_exists > 0 AND type_exists > 0 AND status_exists > 0 THEN
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS 
            WHERE TABLE_SCHEMA = db_name 
              AND TABLE_NAME = table_name 
              AND INDEX_NAME = 'uk_order_type_status';
            
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, 
                    ' ADD UNIQUE KEY uk_order_type_status (order_id, type, status) COMMENT ''防止同一订单同一类型同一状态重复创建支付记录''');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 创建唯一索引 uk_order_type_status 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': 唯一索引 uk_order_type_status 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': order_id、type 或 status 字段不存在，跳过索引创建') AS message;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_unique_index_payment_order_type_pending('jiaoyi_2');
DROP PROCEDURE IF EXISTS add_unique_index_payment_order_type_pending;

-- ============================================
-- 验证结果
-- ============================================
USE jiaoyi_0;
SHOW INDEX FROM payments_0 WHERE Key_name = 'uk_order_type_status';

USE jiaoyi_1;
SHOW INDEX FROM payments_0 WHERE Key_name = 'uk_order_type_status';

USE jiaoyi_2;
SHOW INDEX FROM payments_0 WHERE Key_name = 'uk_order_type_status';
