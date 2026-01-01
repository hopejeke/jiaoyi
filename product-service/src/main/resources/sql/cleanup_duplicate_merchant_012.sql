-- ============================================
-- 清理 merchant_012 的重复数据
-- 保留最新的记录（id 最大的），删除其他重复记录
-- ============================================

-- 1. 先查看所有 merchant_012 的记录
SELECT '查看所有 merchant_012 记录' AS step;

SELECT 'jiaoyi_0.merchants_0' AS location, id, merchant_id, name, create_time
FROM jiaoyi_0.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_1', id, merchant_id, name, create_time
FROM jiaoyi_0.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_2', id, merchant_id, name, create_time
FROM jiaoyi_0.merchants_2 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_0', id, merchant_id, name, create_time
FROM jiaoyi_1.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_1', id, merchant_id, name, create_time
FROM jiaoyi_1.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_2', id, merchant_id, name, create_time
FROM jiaoyi_1.merchants_2 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_0', id, merchant_id, name, create_time
FROM jiaoyi_2.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_1', id, merchant_id, name, create_time
FROM jiaoyi_2.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_2', id, merchant_id, name, create_time
FROM jiaoyi_2.merchants_2 
WHERE merchant_id = 'merchant_012'
ORDER BY id DESC;

-- 2. 删除除了最新记录（id=1207279389033627649）之外的所有 merchant_012 记录
-- 注意：根据分片算法，merchant_012 应该路由到 jiaoyi_0.merchants_0
-- 所以只保留 jiaoyi_0.merchants_0 中的最新记录，删除其他所有位置的记录

-- 删除 jiaoyi_0.merchants_0 中的旧记录（保留 id=1207279389033627649）
USE jiaoyi_0;
DELETE FROM merchants_0 WHERE merchant_id = 'merchant_012' AND id != 1207279389033627649;

-- 删除 jiaoyi_0.merchants_1 中的所有 merchant_012 记录
DELETE FROM merchants_1 WHERE merchant_id = 'merchant_012';

-- 删除 jiaoyi_0.merchants_2 中的所有 merchant_012 记录
DELETE FROM merchants_2 WHERE merchant_id = 'merchant_012';

-- 删除 jiaoyi_1 中的所有 merchant_012 记录
USE jiaoyi_1;
DELETE FROM merchants_0 WHERE merchant_id = 'merchant_012';
DELETE FROM merchants_1 WHERE merchant_id = 'merchant_012';
DELETE FROM merchants_2 WHERE merchant_id = 'merchant_012';

-- 删除 jiaoyi_2 中的所有 merchant_012 记录
USE jiaoyi_2;
DELETE FROM merchants_0 WHERE merchant_id = 'merchant_012';
DELETE FROM merchants_1 WHERE merchant_id = 'merchant_012';
DELETE FROM merchants_2 WHERE merchant_id = 'merchant_012';

-- 3. 验证清理结果
SELECT '清理后验证' AS step;
SELECT 'jiaoyi_0.merchants_0' AS location, COUNT(*) AS count, GROUP_CONCAT(id) AS ids
FROM jiaoyi_0.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_1', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_0.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_2', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_0.merchants_2 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_0', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_1.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_1', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_1.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_2', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_1.merchants_2 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_0', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_2.merchants_0 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_1', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_2.merchants_1 
WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_2', COUNT(*), GROUP_CONCAT(id)
FROM jiaoyi_2.merchants_2 
WHERE merchant_id = 'merchant_012';












