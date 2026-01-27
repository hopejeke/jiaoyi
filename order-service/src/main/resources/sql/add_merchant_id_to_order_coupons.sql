-- ============================================
-- 为 order_coupons 表添加 merchant_id 和 store_id 字段
-- ============================================
-- 说明：
-- 1. order_coupons 表创建时缺少 merchant_id 字段
-- 2. 需要先添加 merchant_id，再添加 store_id（因为 store_id 的 AFTER merchant_id）
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons$$
CREATE PROCEDURE add_merchant_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        -- 检查 merchant_id 列是否存在
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'merchant_id';
        
        -- 如果 merchant_id 列不存在，则添加
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN merchant_id VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''商户ID'' AFTER order_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_merchant_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons$$
CREATE PROCEDURE add_merchant_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'merchant_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN merchant_id VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''商户ID'' AFTER order_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_merchant_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons$$
CREATE PROCEDURE add_merchant_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'merchant_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN merchant_id VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''商户ID'' AFTER order_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_merchant_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_merchant_id_to_order_coupons;

-- ============================================
-- 完成
-- ============================================
-- 注意：执行完此脚本后，order_coupons 表将包含 merchant_id 字段
-- 然后可以执行 add_store_id_to_all_tables.sql 来添加 store_id 字段



