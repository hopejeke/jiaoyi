-- 查询现有商品并生成对应的 SKU 插入语句
-- 执行此脚本查看所有商品，然后手动创建对应的 SKU

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- 查询所有商品
SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_0
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_1
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_2
WHERE is_delete = 0
ORDER BY store_id, id;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_0
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_1
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_2
WHERE is_delete = 0
ORDER BY store_id, id;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_0
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_1
WHERE is_delete = 0
ORDER BY store_id, id;

SELECT 
    id AS product_id,
    store_id,
    product_name,
    unit_price,
    category,
    status
FROM store_products_2
WHERE is_delete = 0
ORDER BY store_id, id;










