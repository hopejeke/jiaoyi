-- ============================================
-- 为 inventory_transactions 表添加唯一索引
-- ============================================
-- 说明：
-- 1. 唯一索引：(order_id, sku_id)
-- 2. 用于幂等性校验：防止同一订单对同一SKU重复锁/扣减
-- 3. 执行此脚本前，请确保已执行 add_sku_id_column.sql
-- ============================================

-- ============================================
-- 库 jiaoyi_product_0
-- ============================================
USE jiaoyi_product_0;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_unique_index_order_sku()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        -- 检查 order_id 字段是否存在
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';
        
        -- 检查 sku_id 字段是否存在
        SELECT COUNT(*) INTO sku_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        -- 只有两个字段都存在时，才创建唯一索引
        IF order_id_exists > 0 AND sku_id_exists > 0 THEN
            -- 检查唯一索引是否已存在
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
              AND TABLE_NAME = table_name
              AND INDEX_NAME = 'uk_order_sku';
            
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', table_name, 
                    ' ADD UNIQUE KEY uk_order_sku (order_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加唯一索引 uk_order_sku 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ ', table_name, ' 唯一索引 uk_order_sku 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('✗ ', table_name, ' order_id或sku_id字段不存在（order_id=', order_id_exists, ', sku_id=', sku_id_exists, '），跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_unique_index_order_sku();
DROP PROCEDURE IF EXISTS add_unique_index_order_sku;

-- ============================================
-- 库 jiaoyi_product_1
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_unique_index_order_sku()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';
        
        SELECT COUNT(*) INTO sku_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF order_id_exists > 0 AND sku_id_exists > 0 THEN
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
              AND TABLE_NAME = table_name
              AND INDEX_NAME = 'uk_order_sku';
            
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', table_name, 
                    ' ADD UNIQUE KEY uk_order_sku (order_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加唯一索引 uk_order_sku 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ ', table_name, ' 唯一索引 uk_order_sku 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('✗ ', table_name, ' order_id或sku_id字段不存在（order_id=', order_id_exists, ', sku_id=', sku_id_exists, '），跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_unique_index_order_sku();
DROP PROCEDURE IF EXISTS add_unique_index_order_sku;

-- ============================================
-- 库 jiaoyi_product_2
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_unique_index_order_sku()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';
        
        SELECT COUNT(*) INTO sku_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF order_id_exists > 0 AND sku_id_exists > 0 THEN
            SELECT COUNT(*) INTO index_exists
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
              AND TABLE_NAME = table_name
              AND INDEX_NAME = 'uk_order_sku';
            
            IF index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', table_name, 
                    ' ADD UNIQUE KEY uk_order_sku (order_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加唯一索引 uk_order_sku 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ ', table_name, ' 唯一索引 uk_order_sku 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('✗ ', table_name, ' order_id或sku_id字段不存在（order_id=', order_id_exists, ', sku_id=', sku_id_exists, '），跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_unique_index_order_sku();
DROP PROCEDURE IF EXISTS add_unique_index_order_sku;

-- ============================================
-- 验证结果
-- ============================================
USE jiaoyi_product_0;
SHOW INDEX FROM inventory_transactions_00 WHERE Key_name = 'uk_order_sku';

USE jiaoyi_product_1;
SHOW INDEX FROM inventory_transactions_00 WHERE Key_name = 'uk_order_sku';

USE jiaoyi_product_2;
SHOW INDEX FROM inventory_transactions_00 WHERE Key_name = 'uk_order_sku';


