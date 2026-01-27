-- 修复 product_sku 表的 price 字段问题
-- 如果表中有旧的 price 字段，迁移数据到 sku_price 并删除旧字段

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- product_sku_0
-- 检查是否有 price 字段，如果有则迁移数据并删除
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'sku_price');

-- 如果有 price 字段但没有 sku_price，先添加 sku_price
SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 如果有 price 字段，迁移数据
SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_0 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除旧的 price 字段
SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_0 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_1 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_1 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_2 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_2 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

-- product_sku_0
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_0 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_0 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_1 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_1 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_2 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_2 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

-- product_sku_0
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_0 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_0 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_1 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_1 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'price');
SET @sku_price_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'sku_price');

SET @sql = IF(@sku_price_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) DEFAULT NULL COMMENT ''SKU价格'' AFTER sku_name', 
    'SELECT ''sku_price already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0 AND @sku_price_exists > 0, 
    'UPDATE product_sku_2 SET sku_price = price WHERE price IS NOT NULL AND sku_price IS NULL', 
    'SELECT ''No migration needed''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@col_exists > 0, 
    'ALTER TABLE product_sku_2 DROP COLUMN price', 
    'SELECT ''price column does not exist''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;









