-- ============================================
-- 删除 orders 表中的 order_no 字段
-- ============================================
-- 注意：如果索引或列不存在，会报错，可以忽略

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 删除 order_no 列和索引
-- 如果索引不存在，会报错，可以忽略
DROP INDEX uk_order_no ON orders_0;
ALTER TABLE orders_0 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_1;
ALTER TABLE orders_1 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_2;
ALTER TABLE orders_2 DROP COLUMN order_no;

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

DROP INDEX uk_order_no ON orders_0;
ALTER TABLE orders_0 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_1;
ALTER TABLE orders_1 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_2;
ALTER TABLE orders_2 DROP COLUMN order_no;

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

DROP INDEX uk_order_no ON orders_0;
ALTER TABLE orders_0 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_1;
ALTER TABLE orders_1 DROP COLUMN order_no;

DROP INDEX uk_order_no ON orders_2;
ALTER TABLE orders_2 DROP COLUMN order_no;

SELECT '✓ order_no 字段已从所有 orders 表中删除' AS result;

