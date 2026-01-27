-- ============================================
-- 检查 store_products 表结构一致性
-- ============================================
-- 用于诊断列数不一致的问题
-- ============================================

USE jiaoyi_product_0;

-- 检查 store_products_00 表的字段
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, ORDINAL_POSITION
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
  AND TABLE_NAME = 'store_products_00'
ORDER BY ORDINAL_POSITION;

-- 检查是否有 bucket_id 字段
SELECT COUNT(*) as has_bucket_id
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
  AND TABLE_NAME = 'store_products_00'
  AND COLUMN_NAME = 'bucket_id';

-- 检查 version 字段类型
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
  AND TABLE_NAME = 'store_products_00'
  AND COLUMN_NAME IN ('version', 'status');

-- 统计每个表的字段数量（应该都是 13 或 14 个字段，取决于是否有 bucket_id）
SELECT 
    TABLE_NAME,
    COUNT(*) as column_count,
    GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ', ') as columns
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
  AND TABLE_NAME LIKE 'store_products_%'
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;


