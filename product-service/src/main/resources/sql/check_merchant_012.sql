-- ============================================
-- 检查 merchant_012 在所有可能的位置
-- ============================================

-- 1. 检查 jiaoyi_0.merchants_0
SELECT 'jiaoyi_0.merchants_0' AS location, COUNT(*) AS count 
FROM jiaoyi_0.merchants_0 
WHERE merchant_id = 'merchant_012';

SELECT * FROM jiaoyi_0.merchants_0 WHERE merchant_id = 'merchant_012';

-- 2. 检查 jiaoyi_0.merchants_1
SELECT 'jiaoyi_0.merchants_1' AS location, COUNT(*) AS count 
FROM jiaoyi_0.merchants_1 
WHERE merchant_id = 'merchant_012';

SELECT * FROM jiaoyi_0.merchants_1 WHERE merchant_id = 'merchant_012';

-- 3. 检查 jiaoyi_0.merchants_2
SELECT 'jiaoyi_0.merchants_2' AS location, COUNT(*) AS count 
FROM jiaoyi_0.merchants_2 
WHERE merchant_id = 'merchant_012';

SELECT * FROM jiaoyi_0.merchants_2 WHERE merchant_id = 'merchant_012';

-- 4. 检查 jiaoyi_2.merchants_1 (旧位置)
SELECT 'jiaoyi_2.merchants_1 (旧位置)' AS location, COUNT(*) AS count 
FROM jiaoyi_2.merchants_1 
WHERE merchant_id = 'merchant_012';

SELECT * FROM jiaoyi_2.merchants_1 WHERE merchant_id = 'merchant_012';

-- 5. 检查所有 merchants 表
SELECT '所有位置汇总' AS summary;
SELECT 'jiaoyi_0.merchants_0' AS location, COUNT(*) AS count FROM jiaoyi_0.merchants_0 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_1', COUNT(*) FROM jiaoyi_0.merchants_1 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_0.merchants_2', COUNT(*) FROM jiaoyi_0.merchants_2 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_0', COUNT(*) FROM jiaoyi_1.merchants_0 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_1', COUNT(*) FROM jiaoyi_1.merchants_1 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_1.merchants_2', COUNT(*) FROM jiaoyi_1.merchants_2 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_0', COUNT(*) FROM jiaoyi_2.merchants_0 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_1', COUNT(*) FROM jiaoyi_2.merchants_1 WHERE merchant_id = 'merchant_012'
UNION ALL
SELECT 'jiaoyi_2.merchants_2', COUNT(*) FROM jiaoyi_2.merchants_2 WHERE merchant_id = 'merchant_012';












