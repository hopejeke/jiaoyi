-- ============================================
-- 删除 payment_no 字段和唯一索引
-- 需要在所有分片表上执行
-- 注意：MySQL 不支持 IF EXISTS，如果字段或索引不存在会报错，可以忽略
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 删除唯一索引（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP INDEX uk_payment_no;
ALTER TABLE payments_1 DROP INDEX uk_payment_no;
ALTER TABLE payments_2 DROP INDEX uk_payment_no;

-- 删除 payment_no 字段（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP COLUMN payment_no;
ALTER TABLE payments_1 DROP COLUMN payment_no;
ALTER TABLE payments_2 DROP COLUMN payment_no;

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

-- 删除唯一索引（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP INDEX uk_payment_no;
ALTER TABLE payments_1 DROP INDEX uk_payment_no;
ALTER TABLE payments_2 DROP INDEX uk_payment_no;

-- 删除 payment_no 字段（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP COLUMN payment_no;
ALTER TABLE payments_1 DROP COLUMN payment_no;
ALTER TABLE payments_2 DROP COLUMN payment_no;

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

-- 删除唯一索引（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP INDEX uk_payment_no;
ALTER TABLE payments_1 DROP INDEX uk_payment_no;
ALTER TABLE payments_2 DROP INDEX uk_payment_no;

-- 删除 payment_no 字段（如果不存在会报错，可以忽略）
ALTER TABLE payments_0 DROP COLUMN payment_no;
ALTER TABLE payments_1 DROP COLUMN payment_no;
ALTER TABLE payments_2 DROP COLUMN payment_no;

