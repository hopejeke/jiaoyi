-- 简单修复：删除 product_sku 表中的旧 price 字段（如果存在）
-- 注意：执行前请确认没有重要数据在 price 字段中

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- 删除旧的 price 字段（如果存在）
ALTER TABLE product_sku_0 DROP COLUMN price;
ALTER TABLE product_sku_1 DROP COLUMN price;
ALTER TABLE product_sku_2 DROP COLUMN price;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

ALTER TABLE product_sku_0 DROP COLUMN price;
ALTER TABLE product_sku_1 DROP COLUMN price;
ALTER TABLE product_sku_2 DROP COLUMN price;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

ALTER TABLE product_sku_0 DROP COLUMN price;
ALTER TABLE product_sku_1 DROP COLUMN price;
ALTER TABLE product_sku_2 DROP COLUMN price;







