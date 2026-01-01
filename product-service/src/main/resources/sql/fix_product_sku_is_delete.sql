-- 修复 product_sku 表缺少 is_delete 字段的问题
-- 需要在每个分库的每个分片表上执行

-- 数据库 jiaoyi_0
USE jiaoyi_0;

-- 检查并添加 is_delete 字段到 product_sku_0
ALTER TABLE product_sku_0 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

-- 检查并添加 is_delete 字段到 product_sku_1
ALTER TABLE product_sku_1 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

-- 检查并添加 is_delete 字段到 product_sku_2
ALTER TABLE product_sku_2 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

-- 添加索引（如果不存在）
ALTER TABLE product_sku_0 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_1 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_2 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);

-- 数据库 jiaoyi_1
USE jiaoyi_1;

ALTER TABLE product_sku_0 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_1 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_2 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_0 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_1 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_2 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);

-- 数据库 jiaoyi_2
USE jiaoyi_2;

ALTER TABLE product_sku_0 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_1 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_2 
ADD COLUMN IF NOT EXISTS is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）' AFTER status;

ALTER TABLE product_sku_0 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_1 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);
ALTER TABLE product_sku_2 ADD INDEX IF NOT EXISTS idx_is_delete (is_delete);






