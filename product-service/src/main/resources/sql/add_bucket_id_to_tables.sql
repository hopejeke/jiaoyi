-- ============================================
-- 为业务表添加 bucket_id 字段
-- ============================================
-- 说明：
-- 1. bucket_id = product_shard_id（0-1023）
-- 2. 用于迁移时按 bucket 筛选数据
-- 3. 需要在每个分片库中执行
-- ============================================

-- ============================================
-- jiaoyi_product_0 数据库
-- ============================================
USE jiaoyi_product_0;

-- store_products 表（32张表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_bucket_id_to_store_products()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        
        -- 检查字段是否存在
        IF NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
              AND TABLE_NAME = table_name 
              AND COLUMN_NAME = 'bucket_id'
        ) THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN bucket_id INT NOT NULL COMMENT ''虚拟桶ID（0-1023，等于product_shard_id）'' AFTER product_shard_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('CREATE INDEX idx_bucket_id ON ', table_name, '(bucket_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            -- 初始化 bucket_id = product_shard_id
            SET @sql = CONCAT('UPDATE ', table_name, ' SET bucket_id = product_shard_id WHERE bucket_id = 0 OR bucket_id IS NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_bucket_id_to_store_products();
DROP PROCEDURE IF EXISTS add_bucket_id_to_store_products;

-- product_sku 表（32张表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_bucket_id_to_product_sku()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('product_sku_', LPAD(i, 2, '0'));
        
        IF NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
              AND TABLE_NAME = table_name 
              AND COLUMN_NAME = 'bucket_id'
        ) THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN bucket_id INT NOT NULL COMMENT ''虚拟桶ID（0-1023，等于product_shard_id）'' AFTER product_shard_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('CREATE INDEX idx_bucket_id ON ', table_name, '(bucket_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('UPDATE ', table_name, ' SET bucket_id = product_shard_id WHERE bucket_id = 0 OR bucket_id IS NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_bucket_id_to_product_sku();
DROP PROCEDURE IF EXISTS add_bucket_id_to_product_sku;

-- inventory 表（32张表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_bucket_id_to_inventory()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_', LPAD(i, 2, '0'));
        
        IF NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
              AND TABLE_NAME = table_name 
              AND COLUMN_NAME = 'bucket_id'
        ) THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN bucket_id INT NOT NULL COMMENT ''虚拟桶ID（0-1023，等于product_shard_id）'' AFTER product_shard_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('CREATE INDEX idx_bucket_id ON ', table_name, '(bucket_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('UPDATE ', table_name, ' SET bucket_id = product_shard_id WHERE bucket_id = 0 OR bucket_id IS NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_bucket_id_to_inventory();
DROP PROCEDURE IF EXISTS add_bucket_id_to_inventory;

-- inventory_transactions 表（32张表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_bucket_id_to_inventory_transactions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('inventory_transactions_', LPAD(i, 2, '0'));
        
        IF NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
              AND TABLE_NAME = table_name 
              AND COLUMN_NAME = 'bucket_id'
        ) THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN bucket_id INT NOT NULL COMMENT ''虚拟桶ID（0-1023，等于product_shard_id）'' AFTER product_shard_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('CREATE INDEX idx_bucket_id ON ', table_name, '(bucket_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('UPDATE ', table_name, ' SET bucket_id = product_shard_id WHERE bucket_id = 0 OR bucket_id IS NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_bucket_id_to_inventory_transactions();
DROP PROCEDURE IF EXISTS add_bucket_id_to_inventory_transactions;

-- outbox 表（32张表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_bucket_id_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(100);
    
    WHILE i < 32 DO
        SET table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        IF NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
              AND TABLE_NAME = table_name 
              AND COLUMN_NAME = 'bucket_id'
        ) THEN
            SET @sql = CONCAT('ALTER TABLE ', table_name, 
                ' ADD COLUMN bucket_id INT NOT NULL COMMENT ''虚拟桶ID（0-1023，等于product_shard_id）'' AFTER product_shard_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('CREATE INDEX idx_bucket_id ON ', table_name, '(bucket_id)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            
            SET @sql = CONCAT('UPDATE ', table_name, ' SET bucket_id = product_shard_id WHERE bucket_id = 0 OR bucket_id IS NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL add_bucket_id_to_outbox();
DROP PROCEDURE IF EXISTS add_bucket_id_to_outbox;

-- ============================================
-- jiaoyi_product_1 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_1;

-- 复制上述存储过程，修改 TABLE_SCHEMA 为 'jiaoyi_product_1'
-- （为简化，这里只列出关键步骤，实际执行时需要修改）

-- ============================================
-- jiaoyi_product_2 数据库（重复上述操作）
-- ============================================
USE jiaoyi_product_2;

-- 复制上述存储过程，修改 TABLE_SCHEMA 为 'jiaoyi_product_2'
-- （为简化，这里只列出关键步骤，实际执行时需要修改）


