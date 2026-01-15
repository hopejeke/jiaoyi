-- ============================================
-- 扩容脚本：从 3 个库扩容到 6 个库
-- ============================================
-- 说明：
-- 1. 1024 个虚拟桶数量不变
-- 2. 只需修改 shard_bucket_route 表的映射关系
-- 3. 无需修改业务代码和算法
-- ============================================

USE jiaoyi;

-- ============================================
-- 步骤 1：更新路由表（将 1024 个 bucket 重新分配到 6 个库）
-- ============================================
-- 分配策略：平均分配
-- ds0: bucket_id 0-170 (171个)
-- ds1: bucket_id 171-341 (171个)
-- ds2: bucket_id 342-512 (171个)
-- ds3: bucket_id 513-683 (171个)  ← 新增
-- ds4: bucket_id 684-853 (170个)  ← 新增
-- ds5: bucket_id 854-1023 (170个) ← 新增

UPDATE shard_bucket_route 
SET ds_name = CASE
    WHEN bucket_id BETWEEN 0 AND 170 THEN 'ds0'
    WHEN bucket_id BETWEEN 171 AND 341 THEN 'ds1'
    WHEN bucket_id BETWEEN 342 AND 512 THEN 'ds2'
    WHEN bucket_id BETWEEN 513 AND 683 THEN 'ds3'
    WHEN bucket_id BETWEEN 684 AND 853 THEN 'ds4'
    WHEN bucket_id BETWEEN 854 AND 1023 THEN 'ds5'
END,
status = 'NORMAL',
updated_at = NOW();

-- ============================================
-- 步骤 2：验证路由表
-- ============================================
SELECT ds_name, 
       COUNT(*) as bucket_count, 
       MIN(bucket_id) as min_bucket, 
       MAX(bucket_id) as max_bucket
FROM shard_bucket_route
GROUP BY ds_name
ORDER BY ds_name;

-- 预期结果：
-- ds0: 171 个 bucket (0-170)
-- ds1: 171 个 bucket (171-341)
-- ds2: 171 个 bucket (342-512)
-- ds3: 171 个 bucket (513-683)
-- ds4: 170 个 bucket (684-853)
-- ds5: 170 个 bucket (854-1023)

-- ============================================
-- 步骤 3：使用存储过程重新分配（可选，更灵活）
-- ============================================
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS redistribute_buckets_to_6_db()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE ds_index INT;
    DECLARE db_count INT DEFAULT 6;  -- 新的库数量
    
    WHILE i < 1024 DO
        -- 重新分配：bucket_id % 6
        SET ds_index = i % db_count;
        
        UPDATE shard_bucket_route 
        SET ds_name = CONCAT('ds', ds_index),
            status = 'NORMAL',
            updated_at = NOW()
        WHERE bucket_id = i;
        
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 执行重新分配（如果需要）
-- CALL redistribute_buckets_to_6_db();

-- ============================================
-- 步骤 4：查询需要迁移的数据范围（用于数据迁移）
-- ============================================
-- 例如：bucket_id 513-683 需要从原库迁移到 ds3

-- 查询需要迁移的 bucket（从 ds1 迁移到 ds3 的示例）
SELECT bucket_id, ds_name 
FROM shard_bucket_route 
WHERE bucket_id BETWEEN 513 AND 683
ORDER BY bucket_id;

-- ============================================
-- 注意事项：
-- ============================================
-- 1. 更新路由表后，需要调用刷新接口：
--    curl -X POST http://instance:8080/api/admin/route-cache/refresh
--
-- 2. 需要迁移的数据：
--    - 只迁移被重新分配的 bucket 的数据
--    - 例如：bucket_id 513-683 的数据需要迁移到 ds3
--
-- 3. 需要在 ShardingSphereConfig 中添加新数据源（ds3, ds4, ds5）
--
-- 4. 1024 这个值永远不变，算法永远不变

