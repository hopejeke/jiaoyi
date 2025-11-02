SET NAMES utf8mb4;
USE jiaoyi;

-- 为 inventory 表增加 store_id 字段
-- 注意：如果表中已有数据，需要先处理数据关联关系

-- 1. 先删除旧的唯一约束（如果存在）
-- 检查是否存在唯一约束，如果存在则删除
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = 'jiaoyi' 
    AND table_name = 'inventory' 
    AND index_name = 'product_id'
);
SET @sql = IF(@index_exists > 0, 'ALTER TABLE inventory DROP INDEX product_id', 'SELECT "Index product_id does not exist" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 添加 store_id 字段（允许 NULL，后续需要更新为 NOT NULL）
ALTER TABLE inventory ADD COLUMN store_id BIGINT COMMENT '店铺ID' AFTER id;

-- 3. 如果 inventory 表中有数据，需要根据 product_id 从 store_products 表获取 store_id
-- 更新现有数据的 store_id
UPDATE inventory i
INNER JOIN store_products sp ON i.product_id = sp.id
SET i.store_id = sp.store_id
WHERE i.store_id IS NULL;

-- 4. 将 store_id 设置为 NOT NULL
ALTER TABLE inventory MODIFY COLUMN store_id BIGINT NOT NULL COMMENT '店铺ID';

-- 5. 添加外键约束和唯一约束
ALTER TABLE inventory 
    ADD CONSTRAINT fk_inventory_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_inventory_store_product FOREIGN KEY (product_id) REFERENCES store_products(id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_store_product (store_id, product_id);

-- 6. 添加索引
ALTER TABLE inventory ADD INDEX idx_store_id (store_id);
ALTER TABLE inventory ADD INDEX idx_store_product (store_id, product_id);

SELECT 'inventory 表的 store_id 字段添加完成！' AS message;

