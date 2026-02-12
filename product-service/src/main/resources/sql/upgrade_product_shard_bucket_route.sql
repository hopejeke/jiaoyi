-- ============================================
-- 升级 product_shard_bucket_route 路由表
-- 添加表路由和迁移支持字段
-- ============================================
-- 说明：
-- 1. 添加 tbl_id 字段（表路由）
-- 2. 添加 version 字段（版本号，用于缓存热更新）
-- 3. 添加 target_ds_id 和 target_tbl_id（迁移目标）
-- 4. 初始化表路由数据（当前使用 product_shard_id % 4）
-- ============================================

USE jiaoyi;

-- 检查并添加 tbl_id 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi' 
      AND TABLE_NAME = 'product_shard_bucket_route' 
      AND COLUMN_NAME = 'tbl_id'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE product_shard_bucket_route ADD COLUMN tbl_id INT NOT NULL DEFAULT 0 COMMENT ''表后缀（0-3）'' AFTER ds_name',
    'SELECT ''tbl_id column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 version 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi' 
      AND TABLE_NAME = 'product_shard_bucket_route' 
      AND COLUMN_NAME = 'version'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE product_shard_bucket_route ADD COLUMN version BIGINT NOT NULL DEFAULT 1 COMMENT ''映射版本号，用于缓存更新'' AFTER status',
    'SELECT ''version column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 target_ds_id 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi' 
      AND TABLE_NAME = 'product_shard_bucket_route' 
      AND COLUMN_NAME = 'target_ds_id'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE product_shard_bucket_route ADD COLUMN target_ds_id VARCHAR(32) NULL COMMENT ''迁移目标数据源（仅 MIGRATING 状态有值）'' AFTER version',
    'SELECT ''target_ds_id column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 target_tbl_id 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'jiaoyi' 
      AND TABLE_NAME = 'product_shard_bucket_route' 
      AND COLUMN_NAME = 'target_tbl_id'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE product_shard_bucket_route ADD COLUMN target_tbl_id INT NULL COMMENT ''迁移目标表（仅 MIGRATING 状态有值）'' AFTER target_ds_id',
    'SELECT ''target_tbl_id column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 初始化表路由数据（如果 tbl_id 为 0 或 NULL，使用 bucket_id % 4）
UPDATE product_shard_bucket_route 
SET tbl_id = bucket_id % 4
WHERE tbl_id = 0 OR tbl_id IS NULL;

-- 初始化版本号（如果 version 为 1 或 NULL，设置为当前时间戳）
UPDATE product_shard_bucket_route 
SET version = UNIX_TIMESTAMP(NOW())
WHERE version = 1 OR version IS NULL;

-- 添加索引（使用存储过程检查索引是否存在）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_index_if_not_exists()
BEGIN
    -- 检查并添加 idx_tbl_id
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'product_shard_bucket_route' 
          AND INDEX_NAME = 'idx_tbl_id'
    ) THEN
        CREATE INDEX idx_tbl_id ON product_shard_bucket_route(tbl_id);
    END IF;
    
    -- 检查并添加 idx_version
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'product_shard_bucket_route' 
          AND INDEX_NAME = 'idx_version'
    ) THEN
        CREATE INDEX idx_version ON product_shard_bucket_route(version);
    END IF;
    
    -- 检查并添加 idx_ds_tbl
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'product_shard_bucket_route' 
          AND INDEX_NAME = 'idx_ds_tbl'
    ) THEN
        CREATE INDEX idx_ds_tbl ON product_shard_bucket_route(ds_name, tbl_id);
    END IF;
END$$
DELIMITER ;

CALL add_index_if_not_exists();
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- 验证数据
SELECT 
    ds_name, 
    COUNT(*) as bucket_count, 
    MIN(bucket_id) as min_bucket, 
    MAX(bucket_id) as max_bucket,
    COUNT(DISTINCT tbl_id) as table_count
FROM product_shard_bucket_route
GROUP BY ds_name
ORDER BY ds_name;

-- 验证表路由分布
SELECT 
    ds_name,
    tbl_id,
    COUNT(*) as bucket_count
FROM product_shard_bucket_route
GROUP BY ds_name, tbl_id
ORDER BY ds_name, tbl_id
LIMIT 50;

