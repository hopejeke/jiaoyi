-- 安全修复 product_sku 表缺少 is_delete 字段的问题
-- 先检查字段是否存在，如果不存在再添加

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- product_sku_0
-- 先检查字段是否存在，如果不存在则添加
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引（如果不存在）
SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_0' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_1' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'product_sku_2' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

-- product_sku_0
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_0' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_1' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'product_sku_2' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

-- product_sku_0
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_0' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_0' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_1
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_1' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_1' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- product_sku_2
SET @db_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_2' AND COLUMN_NAME = 'is_delete');
SET @sql = IF(@db_exists = 0, 
    'ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）''',
    'SELECT ''is_delete字段已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'product_sku_2' AND INDEX_NAME = 'idx_is_delete');
SET @sql = IF(@index_exists = 0, 
    'ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete)',
    'SELECT ''idx_is_delete索引已存在'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;










