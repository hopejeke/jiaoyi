-- ============================================
-- 创建 product_shard_bucket_route 路由表（商品域）
-- ============================================
-- 说明：
-- 1. 此表用于存储 bucket_id（0-1023）到物理库（ds0/ds1/ds2）的映射关系
-- 2. 此表不分片，存在于基础数据库 jiaoyi 中
-- 3. 扩容时只需修改此表的映射关系，无需修改业务代码
-- 4. 商品域独立的路由表，与订单域的 shard_bucket_route 分离
-- ============================================

USE jiaoyi;

CREATE TABLE IF NOT EXISTS product_shard_bucket_route (
    bucket_id INT NOT NULL PRIMARY KEY COMMENT '虚拟分片ID（0-1023，对应 product_shard_id）',
    ds_name VARCHAR(32) NOT NULL COMMENT '物理库名称（ds0/ds1/ds2/...）',
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，MOVING-迁移中，DUAL_WRITE-双写',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ds_name (ds_name),
    INDEX idx_status (status),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品域分片路由表（bucket_id → 物理库映射）';

-- ============================================
-- 初始化路由数据：1024个bucket平均分到3个库
-- ============================================
-- ds0: bucket_id 0-341 (342个)
-- ds1: bucket_id 342-683 (342个)
-- ds2: bucket_id 684-1023 (340个)

-- 使用存储过程批量初始化（更高效）
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS init_product_shard_bucket_route()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE ds_index INT;
    
    WHILE i < 1024 DO
        -- 平均分配到3个库
        SET ds_index = i % 3;
        
        INSERT INTO product_shard_bucket_route (bucket_id, ds_name, status)
        VALUES (i, CONCAT('ds', ds_index), 'NORMAL')
        ON DUPLICATE KEY UPDATE 
            ds_name = VALUES(ds_name),
            status = VALUES(status),
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
SELECT ds_name, COUNT(*) as bucket_count, MIN(bucket_id) as min_bucket, MAX(bucket_id) as max_bucket
FROM product_shard_bucket_route
GROUP BY ds_name
ORDER BY ds_name;

