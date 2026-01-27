-- ============================================
-- 修复 store_products 表的 version 字段类型
-- ============================================
-- 说明：
-- 1. 将 version 字段从 DATETIME 改为 BIGINT
-- 2. 如果 version 是 DATETIME，先转换为时间戳，再改为 BIGINT
-- 3. 需要在每个分片库中执行
-- ============================================

-- ============================================
-- jiaoyi_product_0 数据库
-- ============================================
USE jiaoyi_product_0;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_products_version_type()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    DECLARE column_type VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        -- 检查 version 字段类型
        SELECT DATA_TYPE INTO column_type
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'version';
        
        -- 如果 version 字段是 DATETIME 类型，需要修复
        IF column_type = 'datetime' OR column_type = 'DATETIME' THEN
            -- 先备份数据（将 DATETIME 转换为时间戳）
            SET @sql = CONCAT('UPDATE ', table_name, 
                ' SET version = UNIX_TIMESTAMP(version) WHERE version IS NOT NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 修改字段类型为 BIGINT
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SELECT CONCAT('Fixed version column type for ', table_name) AS message;
        ELSE
            -- 如果已经是 BIGINT，确保默认值为 0
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_products_version_type();
DROP PROCEDURE IF EXISTS fix_store_products_version_type;

-- 验证修复结果
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
  AND TABLE_NAME = 'store_products_00'
  AND COLUMN_NAME = 'version';

-- ============================================
-- jiaoyi_product_1 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_1;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_products_version_type()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    DECLARE column_type VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        SELECT DATA_TYPE INTO column_type
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'version';
        
        IF column_type = 'datetime' OR column_type = 'DATETIME' THEN
            SET @sql = CONCAT('UPDATE ', table_name, 
                ' SET version = UNIX_TIMESTAMP(version) WHERE version IS NOT NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        ELSE
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_products_version_type();
DROP PROCEDURE IF EXISTS fix_store_products_version_type;

-- ============================================
-- jiaoyi_product_2 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_2;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS fix_store_products_version_type()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    DECLARE column_type VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        SELECT DATA_TYPE INTO column_type
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'version';
        
        IF column_type = 'datetime' OR column_type = 'DATETIME' THEN
            SET @sql = CONCAT('UPDATE ', table_name, 
                ' SET version = UNIX_TIMESTAMP(version) WHERE version IS NOT NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        ELSE
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL fix_store_products_version_type();
DROP PROCEDURE IF EXISTS fix_store_products_version_type;


