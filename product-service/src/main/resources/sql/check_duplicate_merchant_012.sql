-- ============================================
-- 检查 merchant_012 在所有分片中的记录
-- ============================================

-- 检查所有分片数据库中的所有 merchants 表
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

-- 查看所有 merchant_012 的详细信息
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
ORDER BY create_time DESC;






