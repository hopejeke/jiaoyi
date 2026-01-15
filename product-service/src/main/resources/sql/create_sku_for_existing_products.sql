-- 为现有商品创建 SKU 测试数据
-- 此脚本会自动为所有商品创建默认 SKU（如果没有 SKU 的话）

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- 为 store_products_0 中的商品创建 SKU
-- 注意：需要根据实际的 store_id 和 product_id 来创建
-- 这里使用示例数据，请根据实际情况修改

-- 示例：假设 store_id=1 的商品
-- 为每个商品创建一个默认 SKU（标准规格）
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  )
LIMIT 10; -- 限制数量，避免一次性插入太多

-- 为 store_products_1 中的商品创建 SKU
INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  )
LIMIT 10;

-- 为 store_products_2 中的商品创建 SKU
INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  )
LIMIT 10;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  )
LIMIT 10;

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  )
LIMIT 10;

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  )
LIMIT 10;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  )
LIMIT 10;

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  )
LIMIT 10;

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","spec":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  )
LIMIT 10;








