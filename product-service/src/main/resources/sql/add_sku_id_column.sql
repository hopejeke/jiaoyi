-- ============================================
-- 为 inventory_transactions 表添加 sku_id 字段
-- ============================================
-- 说明：
-- 1. 库存是按 SKU 级别管理的（inventory 表有 sku_id）
-- 2. 但库存变动记录表缺少 sku_id 字段，无法精确追踪 SKU 级别的变动
-- 3. 添加 sku_id 字段后，可以精确查询某个 SKU 的库存变动记录
-- ============================================

-- ============================================
-- 库 jiaoyi_product_0
-- ============================================
USE jiaoyi_product_0;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_column()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        -- 检查字段是否已存在
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            -- 添加 sku_id 字段
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 添加索引
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 添加复合索引
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段成功') AS message;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_column();
DROP PROCEDURE IF EXISTS add_sku_id_column;

-- ============================================
-- 库 jiaoyi_product_1
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_column()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段成功') AS message;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_column();
DROP PROCEDURE IF EXISTS add_sku_id_column;

-- ============================================
-- 库 jiaoyi_product_2
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_column()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段成功') AS message;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_column();
DROP PROCEDURE IF EXISTS add_sku_id_column;

-- ============================================
-- 验证结果
-- ============================================
USE jiaoyi_product_0;
DESC inventory_transactions_00;

USE jiaoyi_product_1;
DESC inventory_transactions_00;

USE jiaoyi_product_2;
DESC inventory_transactions_00;


