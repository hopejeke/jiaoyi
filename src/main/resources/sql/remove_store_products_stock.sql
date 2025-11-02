SET NAMES utf8mb4;
USE jiaoyi;

-- 移除 store_products 表中的 stock_quantity 字段（库存由独立的 inventory 表管理）
ALTER TABLE store_products DROP COLUMN stock_quantity;

SELECT 'store_products 表的 stock_quantity 字段已移除！' AS message;

