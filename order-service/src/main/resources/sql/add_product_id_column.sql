-- ============================================
-- 为 order_items 表添加 product_id 和 product_image 字段
-- ============================================
-- 注意：如果列已存在，会报错 "Duplicate column name"，可以忽略
-- 执行前请确保已备份数据库

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 添加 product_id 列（如果已存在会报错，可忽略）
ALTER TABLE order_items_0 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_1 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_2 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;

-- 添加 product_image 列（如果已存在会报错，可忽略）
ALTER TABLE order_items_0 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_1 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_2 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;

-- 添加 product_id 索引（如果已存在会报错，可忽略）
CREATE INDEX idx_product_id ON order_items_0(product_id);
CREATE INDEX idx_product_id ON order_items_1(product_id);
CREATE INDEX idx_product_id ON order_items_2(product_id);

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

ALTER TABLE order_items_0 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_1 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_2 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;

ALTER TABLE order_items_0 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_1 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_2 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;

CREATE INDEX idx_product_id ON order_items_0(product_id);
CREATE INDEX idx_product_id ON order_items_1(product_id);
CREATE INDEX idx_product_id ON order_items_2(product_id);

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

ALTER TABLE order_items_0 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_1 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;
ALTER TABLE order_items_2 ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）' AFTER merchant_id;

ALTER TABLE order_items_0 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_1 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;
ALTER TABLE order_items_2 ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片' AFTER item_name;

CREATE INDEX idx_product_id ON order_items_0(product_id);
CREATE INDEX idx_product_id ON order_items_1(product_id);
CREATE INDEX idx_product_id ON order_items_2(product_id);

SELECT '✓ product_id 和 product_image 字段已添加到所有 order_items 表中' AS result;

