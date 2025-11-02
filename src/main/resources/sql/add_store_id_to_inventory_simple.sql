SET NAMES utf8mb4;
USE jiaoyi;

-- 为 inventory 表增加 store_id 字段
-- 简化版本：直接添加字段，如果有旧数据需要手动处理

-- 1. 添加 store_id 字段（允许 NULL）
ALTER TABLE inventory ADD COLUMN store_id BIGINT COMMENT '店铺ID' AFTER id;

-- 2. 如果 inventory 表中有数据，需要根据 product_id 从 store_products 表获取 store_id
-- 更新现有数据的 store_id
UPDATE inventory i
INNER JOIN store_products sp ON i.product_id = sp.id
SET i.store_id = sp.store_id
WHERE i.store_id IS NULL;

-- 3. 将 store_id 设置为 NOT NULL
ALTER TABLE inventory MODIFY COLUMN store_id BIGINT NOT NULL COMMENT '店铺ID';

-- 4. 删除旧的唯一约束（如果存在）
ALTER TABLE inventory DROP INDEX product_id;

-- 5. 添加新的唯一约束和外键约束
ALTER TABLE inventory 
    ADD UNIQUE KEY uk_store_product (store_id, product_id);

-- 6. 添加外键约束（如果 store_products 表存在）
ALTER TABLE inventory 
    ADD CONSTRAINT fk_inventory_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_inventory_store_product FOREIGN KEY (product_id) REFERENCES store_products(id) ON DELETE CASCADE;

-- 7. 添加索引
ALTER TABLE inventory ADD INDEX idx_store_id (store_id);
ALTER TABLE inventory ADD INDEX idx_store_product (store_id, product_id);

SELECT 'inventory 表的 store_id 字段添加完成！' AS message;

