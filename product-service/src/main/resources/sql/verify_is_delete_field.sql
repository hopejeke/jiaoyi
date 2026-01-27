-- 验证所有 product_sku 表是否都有 is_delete 字段
-- 执行此脚本查看详细结果

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

SELECT 'jiaoyi_0.product_sku_0' AS table_name, 
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_0' 
  AND TABLE_NAME = 'product_sku_0' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_0.product_sku_1' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_0' 
  AND TABLE_NAME = 'product_sku_1' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_0.product_sku_2' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_0' 
  AND TABLE_NAME = 'product_sku_2' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

-- ========== 数据库 jiaoyi_1 ==========
SELECT 'jiaoyi_1.product_sku_0' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_1' 
  AND TABLE_NAME = 'product_sku_0' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_1.product_sku_1' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_1' 
  AND TABLE_NAME = 'product_sku_1' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_1.product_sku_2' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_1' 
  AND TABLE_NAME = 'product_sku_2' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

-- ========== 数据库 jiaoyi_2 ==========
SELECT 'jiaoyi_2.product_sku_0' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_2' 
  AND TABLE_NAME = 'product_sku_0' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_2.product_sku_1' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_2' 
  AND TABLE_NAME = 'product_sku_1' 
  AND COLUMN_NAME = 'is_delete'

UNION ALL

SELECT 'jiaoyi_2.product_sku_2' AS table_name,
       CASE WHEN COUNT(*) > 0 THEN '✓ 存在' ELSE '✗ 不存在' END AS is_delete_status
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'jiaoyi_2' 
  AND TABLE_NAME = 'product_sku_2' 
  AND COLUMN_NAME = 'is_delete';










