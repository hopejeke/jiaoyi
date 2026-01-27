-- ============================================
-- 修复 store_products 表的 status 字段值
-- ============================================
-- 说明：
-- 1. 将数字值 0 转换为 'ACTIVE'
-- 2. 将数字值 1 转换为 'INACTIVE'
-- 3. 需要在每个分片库中执行
-- ============================================

-- ============================================
-- jiaoyi_product_0 数据库
-- ============================================
USE jiaoyi_product_0;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_product_status()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        -- 将数字值 0 转换为 'ACTIVE'
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''ACTIVE'' WHERE status = ''0'' OR status = 0');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        -- 将数字值 1 转换为 'INACTIVE'
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''INACTIVE'' WHERE status = ''1'' OR status = 1');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_product_status();
DROP PROCEDURE IF EXISTS fix_store_product_status;

-- 验证修复结果
SELECT status, COUNT(*) as count 
FROM store_products_00 
GROUP BY status;

-- ============================================
-- jiaoyi_product_1 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_product_status()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''ACTIVE'' WHERE status = ''0'' OR status = 0');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''INACTIVE'' WHERE status = ''1'' OR status = 1');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_product_status();
DROP PROCEDURE IF EXISTS fix_store_product_status;

-- ============================================
-- jiaoyi_product_2 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_product_status()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''ACTIVE'' WHERE status = ''0'' OR status = 0');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('UPDATE ', table_name, 
            ' SET status = ''INACTIVE'' WHERE status = ''1'' OR status = 1');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_product_status();
DROP PROCEDURE IF EXISTS fix_store_product_status;


