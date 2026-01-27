-- ============================================
-- 为 inventory_transactions 表添加 sku_id 字段和唯一索引
-- ============================================
-- 说明：
-- 1. 库存是按 SKU 级别管理的（inventory 表有 sku_id）
-- 2. 但库存变动记录表缺少 sku_id 字段，无法精确追踪 SKU 级别的变动
-- 3. 添加 sku_id 字段后，可以精确查询某个 SKU 的库存变动记录
-- 4. 添加唯一索引用于幂等性校验：防止同一订单对同一SKU重复锁/扣减
--    唯一索引：(order_id, sku_id)
-- ============================================

-- ============================================
-- 库 jiaoyi_product_0
-- ============================================
USE jiaoyi_product_0;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_to_inventory_transactions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        -- 检查并添加 sku_id 字段
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            -- 添加 sku_id 字段（不使用 AFTER，直接添加到最后）
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 再次检查字段是否添加成功
            SELECT COUNT(*) INTO column_exists
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
              AND TABLE_NAME = table_name
              AND COLUMN_NAME = 'sku_id';
            
            IF column_exists > 0 THEN
                -- 字段添加成功，创建索引
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段和索引成功') AS message;
            ELSE
                SELECT CONCAT('✗ ', table_name, ' 添加 sku_id 字段失败') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        -- 检查并添加唯一索引（用于幂等性校验）
        -- 先确认 order_id 和 sku_id 字段都存在，再创建唯一索引
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';
        
        SELECT COUNT(*) INTO sku_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF order_id_exists > 0 AND sku_id_exists > 0 THEN
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
            SELECT CONCAT('⚠ ', table_name, ' order_id或sku_id字段不存在（order_id=', order_id_exists, ', sku_id=', sku_id_exists, '），跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_to_inventory_transactions();
DROP PROCEDURE IF EXISTS add_sku_id_to_inventory_transactions;

-- ============================================
-- 库 jiaoyi_product_1
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_to_inventory_transactions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            -- 添加 sku_id 字段（不使用 AFTER，直接添加到最后）
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 再次检查字段是否添加成功
            SELECT COUNT(*) INTO column_exists
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
              AND TABLE_NAME = table_name
              AND COLUMN_NAME = 'sku_id';
            
            IF column_exists > 0 THEN
                -- 字段添加成功，创建索引
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段和索引成功') AS message;
            ELSE
                SELECT CONCAT('✗ ', table_name, ' 添加 sku_id 字段失败') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        -- 检查并添加唯一索引（用于幂等性校验）
        -- 先确认 sku_id 字段存在，再创建唯一索引
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists > 0 THEN
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
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段不存在，跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_to_inventory_transactions();
DROP PROCEDURE IF EXISTS add_sku_id_to_inventory_transactions;

-- ============================================
-- 库 jiaoyi_product_2
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_sku_id_to_inventory_transactions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';
        
        IF column_exists = 0 THEN
            -- 添加 sku_id 字段（不使用 AFTER，直接添加到最后）
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（关联product_sku.id），NULL表示商品级别库存变动''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 再次检查字段是否添加成功
            SELECT COUNT(*) INTO column_exists
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
              AND TABLE_NAME = table_name
              AND COLUMN_NAME = 'sku_id';
            
            IF column_exists > 0 THEN
                -- 字段添加成功，创建索引
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_sku_id (sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD INDEX idx_product_sku (product_id, sku_id)');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                
                SELECT CONCAT('✓ ', table_name, ' 添加 sku_id 字段和索引成功') AS message;
            ELSE
                SELECT CONCAT('✗ ', table_name, ' 添加 sku_id 字段失败') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ ', table_name, ' sku_id 字段已存在，跳过') AS message;
        END IF;
        
        -- 检查并添加唯一索引（用于幂等性校验）
        -- 先确认 order_id 和 sku_id 字段都存在，再创建唯一索引
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
            SELECT CONCAT('⚠ ', table_name, ' order_id或sku_id字段不存在（order_id=', order_id_exists, ', sku_id=', sku_id_exists, '），跳过唯一索引创建') AS message;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_sku_id_to_inventory_transactions();
DROP PROCEDURE IF EXISTS add_sku_id_to_inventory_transactions;

-- ============================================
-- 验证结果
-- ============================================
USE jiaoyi_product_0;
DESC inventory_transactions_00;

USE jiaoyi_product_1;
DESC inventory_transactions_00;

USE jiaoyi_product_2;
DESC inventory_transactions_00;
