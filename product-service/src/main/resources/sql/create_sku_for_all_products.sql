-- 为所有现有商品自动创建 SKU 测试数据
-- 此脚本会为每个商品创建一个默认 SKU（标准规格）

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- 为 store_products_0 中的商品创建 SKU（插入到对应的 product_sku 分片表）
-- 注意：根据 store_id 计算分片表
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0  -- 路由到 product_sku_0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1  -- 路由到 product_sku_1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2  -- 路由到 product_sku_2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

-- 为 store_products_1 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

-- 为 store_products_2 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

-- 为 store_products_0 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

-- 为 store_products_1 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

-- 为 store_products_2 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

-- 为 store_products_0 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_0
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_0.id 
        AND psk.store_id = store_products_0.store_id
  );

-- 为 store_products_1 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_1
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_1.id 
        AND psk.store_id = store_products_1.store_id
  );

-- 为 store_products_2 中的商品创建 SKU
INSERT INTO product_sku_0 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 0
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_0 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_1 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_1 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );

INSERT INTO product_sku_2 (product_id, store_id, sku_code, sku_name, sku_price, sku_attributes, status, is_delete, version)
SELECT 
    id AS product_id,
    store_id,
    CONCAT('SKU-', id, '-DEFAULT') AS sku_code,
    CONCAT(product_name, ' - 标准规格') AS sku_name,
    unit_price AS sku_price,
    '{"size":"标准","specification":"默认规格"}' AS sku_attributes,
    'ACTIVE' AS status,
    0 AS is_delete,
    1 AS version
FROM store_products_2
WHERE is_delete = 0
  AND (store_id % 9) % 3 = 2
  AND NOT EXISTS (
      SELECT 1 FROM product_sku_2 psk 
      WHERE psk.product_id = store_products_2.id 
        AND psk.store_id = store_products_2.store_id
  );










