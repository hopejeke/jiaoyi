-- ============================================
-- 升级 shard_bucket_route 路由表
-- ============================================
-- 说明：
-- 1. 添加 tbl_id 字段（表路由）
-- 2. 添加 version 字段（版本号，用于缓存更新）
-- 3. 添加 target_ds_id 和 target_tbl_id 字段（迁移目标）
-- 4. 初始化表路由数据（bucket_id % 32）
-- ============================================

USE jiaoyi;

-- ============================================
-- 步骤1：添加新字段
-- ============================================

-- 添加 tbl_id 字段（表路由）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_tbl_id_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'shard_bucket_route' 
          AND COLUMN_NAME = 'tbl_id'
    ) THEN
        ALTER TABLE shard_bucket_route 
        ADD COLUMN tbl_id INT NOT NULL DEFAULT 0 COMMENT '表后缀（0-31，基于bucket_id计算）' AFTER ds_name;
        
        SELECT 'tbl_id 字段添加成功' AS message;
    ELSE
        SELECT 'tbl_id 字段已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_tbl_id_column();
DROP PROCEDURE IF EXISTS add_tbl_id_column;

-- 添加 version 字段（版本号）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_version_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'shard_bucket_route' 
          AND COLUMN_NAME = 'version'
    ) THEN
        ALTER TABLE shard_bucket_route 
        ADD COLUMN version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号（用于缓存更新）' AFTER status;
        
        SELECT 'version 字段添加成功' AS message;
    ELSE
        SELECT 'version 字段已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_version_column();
DROP PROCEDURE IF EXISTS add_version_column;

-- 添加 target_ds_id 字段（迁移目标数据源）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_target_ds_id_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'shard_bucket_route' 
          AND COLUMN_NAME = 'target_ds_id'
    ) THEN
        ALTER TABLE shard_bucket_route 
        ADD COLUMN target_ds_id VARCHAR(32) NULL COMMENT '迁移目标数据源（仅 MIGRATING 状态有值）' AFTER version;
        
        SELECT 'target_ds_id 字段添加成功' AS message;
    ELSE
        SELECT 'target_ds_id 字段已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_target_ds_id_column();
DROP PROCEDURE IF EXISTS add_target_ds_id_column;

-- 添加 target_tbl_id 字段（迁移目标表）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_target_tbl_id_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = 'jiaoyi' 
          AND TABLE_NAME = 'shard_bucket_route' 
          AND COLUMN_NAME = 'target_tbl_id'
    ) THEN
        ALTER TABLE shard_bucket_route 
        ADD COLUMN target_tbl_id INT NULL COMMENT '迁移目标表（仅 MIGRATING 状态有值）' AFTER target_ds_id;
        
        SELECT 'target_tbl_id 字段添加成功' AS message;
    ELSE
        SELECT 'target_tbl_id 字段已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_target_tbl_id_column();
DROP PROCEDURE IF EXISTS add_target_tbl_id_column;

-- ============================================
-- 步骤2：初始化表路由数据（tbl_id = bucket_id % 32）
-- ============================================

UPDATE shard_bucket_route 
SET tbl_id = bucket_id % 32
WHERE tbl_id = 0 OR tbl_id IS NULL;

-- ============================================
-- 步骤3：添加索引
-- ============================================

-- 添加 tbl_id 索引
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_tbl_id_index()
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi'
      AND TABLE_NAME = 'shard_bucket_route'
      AND INDEX_NAME = 'idx_tbl_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE shard_bucket_route ADD INDEX idx_tbl_id (tbl_id);
        SELECT 'idx_tbl_id 索引添加成功' AS message;
    ELSE
        SELECT 'idx_tbl_id 索引已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_tbl_id_index();
DROP PROCEDURE IF EXISTS add_tbl_id_index;

-- 添加 version 索引
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS add_version_index()
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi'
      AND TABLE_NAME = 'shard_bucket_route'
      AND INDEX_NAME = 'idx_version';
    
    IF index_exists = 0 THEN
        ALTER TABLE shard_bucket_route ADD INDEX idx_version (version);
        SELECT 'idx_version 索引添加成功' AS message;
    ELSE
        SELECT 'idx_version 索引已存在' AS message;
    END IF;
END$$
DELIMITER ;

CALL add_version_index();
DROP PROCEDURE IF EXISTS add_version_index;

-- ============================================
-- 步骤4：验证升级结果
-- ============================================

-- 检查表结构
DESC shard_bucket_route;

-- 验证表路由分布
SELECT ds_name, tbl_id, COUNT(*) as bucket_count
FROM shard_bucket_route
GROUP BY ds_name, tbl_id
ORDER BY ds_name, tbl_id
LIMIT 20;

-- 验证数据完整性（应该正好 1024 条）
SELECT COUNT(*) as total_buckets FROM shard_bucket_route;


