-- 为 product_sku 表创建测试数据
-- 需要先查询 store_products 表中的商品，然后为每个商品创建对应的 SKU

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- 查询现有的商品（示例：假设有商品）
-- 注意：需要根据实际的 store_products 表数据来创建 SKU
-- 这里提供示例 SQL，实际执行前请先查询 store_products 表

-- 示例：为 store_id=1, product_id=1 的商品创建 SKU
-- 请根据实际的商品数据修改以下 SQL

-- 假设商品表中有以下商品：
-- store_id=1, product_id=1, product_name='宫保鸡丁'
-- store_id=1, product_id=2, product_name='麻婆豆腐'

-- 为商品 ID=1 创建 SKU（小份、中份、大份）
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version) VALUES
(1, 1, 'SKU001-S', '小份', 25.00, '{"size":"小份","weight":"200g"}', 'ACTIVE', 0, 1),
(1, 1, 'SKU001-M', '中份', 35.00, '{"size":"中份","weight":"300g"}', 'ACTIVE', 0, 1),
(1, 1, 'SKU001-L', '大份', 45.00, '{"size":"大份","weight":"400g"}', 'ACTIVE', 0, 1);

-- 为商品 ID=2 创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version) VALUES
(2, 1, 'SKU002-S', '小份', 20.00, '{"size":"小份","weight":"200g"}', 'ACTIVE', 0, 1),
(2, 1, 'SKU002-M', '中份', 28.00, '{"size":"中份","weight":"300g"}', 'ACTIVE', 0, 1),
(2, 1, 'SKU002-L', '大份', 38.00, '{"size":"大份","weight":"400g"}', 'ACTIVE', 0, 1);

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

-- 为 jiaoyi_1 中的商品创建 SKU（示例）
-- 请根据实际的商品数据修改

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

-- 为 jiaoyi_2 中的商品创建 SKU（示例）
-- 请根据实际的商品数据修改








