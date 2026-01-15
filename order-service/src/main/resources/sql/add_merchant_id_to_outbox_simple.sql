-- ============================================
-- 为 outbox 表添加 sharding_key 列（通用分片键字段）
-- ============================================
-- 说明：
-- 1. 此脚本用于为已存在的 outbox 表添加 sharding_key 列（通用分片键字段）
-- 2. 需要在每个分片库（jiaoyi_0, jiaoyi_1, jiaoyi_2）中分别执行
-- 3. 如果列已存在，会报错 "Duplicate column name 'sharding_key'"，可以忽略
-- 4. 此字段替代了 merchant_id 和 store_id，业务方可以存任何分片键值
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 添加 sharding_key 列（通用分片键字段）
ALTER TABLE order_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE order_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE order_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

-- 如果存在 product_outbox 表，也添加
ALTER TABLE product_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE product_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE product_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

ALTER TABLE order_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE order_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE order_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

ALTER TABLE product_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE product_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE product_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

ALTER TABLE order_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE order_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE order_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

ALTER TABLE product_outbox ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）';
ALTER TABLE product_outbox ADD INDEX idx_sharding_key (sharding_key);
ALTER TABLE product_outbox ADD INDEX idx_sharding_key_status (sharding_key, status);

-- ============================================
-- 验证（可选）
-- ============================================
-- 执行以下 SQL 验证 merchant_id 列是否已添加成功：
-- 
-- USE jiaoyi_0;
-- DESCRIBE outbox;
-- 
-- 或者查看所有列：
-- SHOW COLUMNS FROM outbox;
-- 
-- 查看索引：
-- SHOW INDEX FROM outbox;

