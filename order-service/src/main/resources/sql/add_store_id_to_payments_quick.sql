-- ============================================
-- 快速修复：为 payments 表添加 store_id 字段
-- ============================================
-- 说明：此脚本仅用于快速修复 payments 表，解决支付功能报错
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick$$
CREATE PROCEDURE add_store_id_to_payments_quick()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments_quick();
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick$$
CREATE PROCEDURE add_store_id_to_payments_quick()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments_quick();
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick$$
CREATE PROCEDURE add_store_id_to_payments_quick()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments_quick();
DROP PROCEDURE IF EXISTS add_store_id_to_payments_quick;

-- ============================================
-- 完成
-- ============================================
-- 注意：执行完此脚本后，payments 表将包含 store_id 字段
-- 历史数据的 store_id 默认为 0，需要根据业务逻辑进行数据迁移



