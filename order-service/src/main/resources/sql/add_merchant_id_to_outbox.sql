-- ============================================
-- 为 outbox 表添加 merchant_id 列
-- ============================================
-- 说明：
-- 1. 此脚本用于为已存在的 outbox 表添加 merchant_id 列
-- 2. 需要在每个分片库（jiaoyi_0, jiaoyi_1, jiaoyi_2）中分别执行
-- 3. 如果列已存在，会报错但可以忽略（使用 IF NOT EXISTS 语法）
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 检查并添加 merchant_id 列
SET @db_exists = (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'jiaoyi_0');
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'outbox' AND COLUMN_NAME = 'merchant_id');

-- 如果数据库和表存在，且列不存在，则添加列
SET @sql = IF(@db_exists > 0 AND @col_exists = 0,
    'ALTER TABLE outbox ADD COLUMN merchant_id VARCHAR(50) COMMENT ''商户ID（分片键，与订单表一致，用于分库路由）''',
    'SELECT ''merchant_id 列已存在或表不存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加索引
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id');
SET @sql = IF(@db_exists > 0 AND @idx_exists = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id (merchant_id)',
    'SELECT ''idx_merchant_id 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_0' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id_status');
SET @sql = IF(@db_exists > 0 AND @idx_exists2 = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id_status (merchant_id, status)',
    'SELECT ''idx_merchant_id_status 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

SET @db_exists = (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'jiaoyi_1');
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'outbox' AND COLUMN_NAME = 'merchant_id');

SET @sql = IF(@db_exists > 0 AND @col_exists = 0,
    'ALTER TABLE outbox ADD COLUMN merchant_id VARCHAR(50) COMMENT ''商户ID（分片键，与订单表一致，用于分库路由）''',
    'SELECT ''merchant_id 列已存在或表不存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id');
SET @sql = IF(@db_exists > 0 AND @idx_exists = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id (merchant_id)',
    'SELECT ''idx_merchant_id 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_1' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id_status');
SET @sql = IF(@db_exists > 0 AND @idx_exists2 = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id_status (merchant_id, status)',
    'SELECT ''idx_merchant_id_status 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

SET @db_exists = (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'jiaoyi_2');
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'outbox' AND COLUMN_NAME = 'merchant_id');

SET @sql = IF(@db_exists > 0 AND @col_exists = 0,
    'ALTER TABLE outbox ADD COLUMN merchant_id VARCHAR(50) COMMENT ''商户ID（分片键，与订单表一致，用于分库路由）''',
    'SELECT ''merchant_id 列已存在或表不存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id');
SET @sql = IF(@db_exists > 0 AND @idx_exists = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id (merchant_id)',
    'SELECT ''idx_merchant_id 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'jiaoyi_2' AND TABLE_NAME = 'outbox' AND INDEX_NAME = 'idx_merchant_id_status');
SET @sql = IF(@db_exists > 0 AND @idx_exists2 = 0,
    'ALTER TABLE outbox ADD INDEX idx_merchant_id_status (merchant_id, status)',
    'SELECT ''idx_merchant_id_status 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 验证脚本（可选）
-- ============================================
-- 执行以下 SQL 验证 merchant_id 列是否已添加成功：
-- 
-- USE jiaoyi_0;
-- DESCRIBE outbox;
-- 
-- USE jiaoyi_1;
-- DESCRIBE outbox;
-- 
-- USE jiaoyi_2;
-- DESCRIBE outbox;
-- 
-- 或者查看索引：
-- SHOW INDEX FROM outbox;



