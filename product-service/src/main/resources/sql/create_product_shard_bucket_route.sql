-- ============================================
-- 创建 product_shard_bucket_route 路由表（商品域）
-- ============================================
-- 说明：
-- 1. 此表用于存储 bucket_id（0-1023）到物理库（ds0/ds1）和物理表（tbl_id 0-3）的映射关系
-- 2. 此表不分片，存在于基础数据库 jiaoyi 中
-- 3. 扩容时只需修改此表的映射关系，无需修改业务代码
-- 4. 商品域独立的路由表，与订单域的 shard_bucket_route 分离
-- ============================================
-- 分片策略（优化版）：
-- 2库 × 4表 = 8个物理分片
-- ds0: bucket_id 0-511 (512个)，tbl_id = bucket_id % 4
-- ds1: bucket_id 512-1023 (512个)，tbl_id = bucket_id % 4
-- ============================================

USE jiaoyi;

CREATE TABLE IF NOT EXISTS product_shard_bucket_route (
    bucket_id INT NOT NULL PRIMARY KEY COMMENT '虚拟分片ID（0-1023，对应 product_shard_id）',
    ds_name VARCHAR(32) NOT NULL COMMENT '物理库名称（ds0/ds1）',
    tbl_id INT NOT NULL DEFAULT 0 COMMENT '表后缀（0-3）',
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，MOVING-迁移中，DUAL_WRITE-双写',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '映射版本号，用于缓存更新',
    target_ds_id VARCHAR(32) NULL COMMENT '迁移目标数据源（仅 MIGRATING 状态有值）',
    target_tbl_id INT NULL COMMENT '迁移目标表（仅 MIGRATING 状态有值）',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ds_name (ds_name),
    INDEX idx_tbl_id (tbl_id),
    INDEX idx_ds_tbl (ds_name, tbl_id),
    INDEX idx_status (status),
    INDEX idx_version (version),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品域分片路由表（bucket_id → 物理库+表映射）';

-- ============================================
-- 初始化路由数据：1024个bucket平均分到2个库 × 4张表
-- ============================================
-- ds0: bucket_id 0-511 (512个)
-- ds1: bucket_id 512-1023 (512个)
-- tbl_id = bucket_id % 4 (0-3)

-- 使用存储过程批量初始化（更高效）
DELIMITER $$

DROP PROCEDURE IF EXISTS init_product_shard_bucket_route$$

CREATE PROCEDURE init_product_shard_bucket_route()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE ds_index INT;
    DECLARE tbl_index INT;
    
    WHILE i < 1024 DO
        -- 平均分配到2个库：0-511 → ds0, 512-1023 → ds1
        SET ds_index = FLOOR(i / 512);
        -- 表路由：bucket_id % 4
        SET tbl_index = i % 4;
        
        INSERT INTO product_shard_bucket_route (bucket_id, ds_name, tbl_id, status, version)
        VALUES (i, CONCAT('ds', ds_index), tbl_index, 'NORMAL', UNIX_TIMESTAMP(NOW()))
        ON DUPLICATE KEY UPDATE 
            ds_name = VALUES(ds_name),
            tbl_id = VALUES(tbl_id),
            status = VALUES(status),
            version = UNIX_TIMESTAMP(NOW()),
            updated_at = CURRENT_TIMESTAMP;
        
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 执行初始化
CALL init_product_shard_bucket_route();

-- 删除存储过程
DROP PROCEDURE IF EXISTS init_product_shard_bucket_route;

-- 验证数据
SELECT ds_name, COUNT(*) as bucket_count, MIN(bucket_id) as min_bucket, MAX(bucket_id) as max_bucket,
       COUNT(DISTINCT tbl_id) as table_count
FROM product_shard_bucket_route
GROUP BY ds_name
ORDER BY ds_name;

-- 验证表路由分布
SELECT ds_name, tbl_id, COUNT(*) as bucket_count
FROM product_shard_bucket_route
GROUP BY ds_name, tbl_id
ORDER BY ds_name, tbl_id;



