-- 完整修复 product_sku 表的所有缺失字段
-- MySQL 不支持 IF NOT EXISTS，需要手动检查或忽略错误

-- ========== 数据库 jiaoyi_0 ==========
USE jiaoyi_0;

-- product_sku_0
ALTER TABLE product_sku_0 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_0 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_0 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_0 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_0 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

-- product_sku_1
ALTER TABLE product_sku_1 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_1 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_1 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_1 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_1 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

-- product_sku_2
ALTER TABLE product_sku_2 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_2 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_2 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_2 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_2 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

-- ========== 数据库 jiaoyi_1 ==========
USE jiaoyi_1;

ALTER TABLE product_sku_0 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_0 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_0 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_0 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_0 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

ALTER TABLE product_sku_1 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_1 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_1 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_1 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_1 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

ALTER TABLE product_sku_2 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_2 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_2 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_2 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_2 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

-- ========== 数据库 jiaoyi_2 ==========
USE jiaoyi_2;

ALTER TABLE product_sku_0 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_0 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_0 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_0 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_0 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_0 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_0 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

ALTER TABLE product_sku_1 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_1 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_1 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_1 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_1 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_1 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_1 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;

ALTER TABLE product_sku_2 ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式）' AFTER sku_code;
ALTER TABLE product_sku_2 ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称' AFTER sku_attributes;
ALTER TABLE product_sku_2 ADD COLUMN sku_price DECIMAL(10,2) COMMENT 'SKU价格' AFTER sku_name;
ALTER TABLE product_sku_2 ADD COLUMN sku_image VARCHAR(500) COMMENT 'SKU图片' AFTER sku_price;
ALTER TABLE product_sku_2 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态' AFTER sku_image;
ALTER TABLE product_sku_2 ADD COLUMN is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除' AFTER status;
ALTER TABLE product_sku_2 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER is_delete;









