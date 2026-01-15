-- ============================================
-- 为 outbox 表添加 store_id 列（简单版本）
-- ============================================
-- 说明：
-- 1. 此脚本用于为已存在的 outbox 表添加 store_id 列
-- 2. 需要在每个分片库（jiaoyi_0, jiaoyi_1, jiaoyi_2）中分别执行
-- 3. 如果列已存在，会报错 "Duplicate column name 'store_id'"，可以忽略
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 添加 store_id 列（如果已存在会报错，可以忽略）
ALTER TABLE outbox ADD COLUMN store_id BIGINT COMMENT '店铺ID（分片键，与商品表一致，用于分库路由）';

-- 添加索引（如果已存在会报错，可以忽略）
ALTER TABLE outbox ADD INDEX idx_store_id (store_id);
ALTER TABLE outbox ADD INDEX idx_store_id_status (store_id, status);

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

ALTER TABLE outbox ADD COLUMN store_id BIGINT COMMENT '店铺ID（分片键，与商品表一致，用于分库路由）';
ALTER TABLE outbox ADD INDEX idx_store_id (store_id);
ALTER TABLE outbox ADD INDEX idx_store_id_status (store_id, status);

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

ALTER TABLE outbox ADD COLUMN store_id BIGINT COMMENT '店铺ID（分片键，与商品表一致，用于分库路由）';
ALTER TABLE outbox ADD INDEX idx_store_id (store_id);
ALTER TABLE outbox ADD INDEX idx_store_id_status (store_id, status);

-- ============================================
-- 验证（可选）
-- ============================================
-- 执行以下 SQL 验证 store_id 列是否已添加成功：
-- 
-- USE jiaoyi_0;
-- DESCRIBE outbox;
-- 
-- 或者查看所有列：
-- SHOW COLUMNS FROM outbox;
-- 
-- 查看索引：
-- SHOW INDEX FROM outbox;

