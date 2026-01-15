-- 完整修复 product_sku 表缺少 is_delete 字段的问题
-- 确保所有分库的所有分片表都有 is_delete 字段

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- product_sku_0
-- 如果字段已存在会报错，但可以忽略
ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete);

-- product_sku_1
ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete);

-- product_sku_2
ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete);

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete);

ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete);

ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete);

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_0 ADD INDEX idx_is_delete (is_delete);

ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_1 ADD INDEX idx_is_delete (is_delete);

ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）';
ALTER TABLE product_sku_2 ADD INDEX idx_is_delete (is_delete);








