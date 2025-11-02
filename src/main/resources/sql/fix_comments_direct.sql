SET NAMES utf8mb4;
USE jiaoyi;

-- 修复stores表的字段注释 - 使用ALTER TABLE CHANGE方式
ALTER TABLE stores CHANGE COLUMN id id BIGINT AUTO_INCREMENT COMMENT '店铺ID';
ALTER TABLE stores CHANGE COLUMN store_name store_name VARCHAR(200) NOT NULL COMMENT '店铺名称';
ALTER TABLE stores CHANGE COLUMN store_code store_code VARCHAR(50) NOT NULL COMMENT '店铺编码';
ALTER TABLE stores CHANGE COLUMN description description TEXT COMMENT '店铺描述';
ALTER TABLE stores CHANGE COLUMN owner_name owner_name VARCHAR(100) COMMENT '店主姓名';
ALTER TABLE stores CHANGE COLUMN owner_phone owner_phone VARCHAR(20) COMMENT '店主电话';
ALTER TABLE stores CHANGE COLUMN address address VARCHAR(500) COMMENT '店铺地址';
ALTER TABLE stores CHANGE COLUMN status status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态：ACTIVE-营业中，INACTIVE-已关闭';
ALTER TABLE stores CHANGE COLUMN create_time create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE stores CHANGE COLUMN update_time update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE stores COMMENT = '店铺表';

-- 修复store_products表的字段注释
ALTER TABLE store_products CHANGE COLUMN id id BIGINT AUTO_INCREMENT COMMENT '关联ID';
ALTER TABLE store_products CHANGE COLUMN store_id store_id BIGINT NOT NULL COMMENT '店铺ID';
ALTER TABLE store_products CHANGE COLUMN product_id product_id BIGINT NOT NULL COMMENT '商品ID';
ALTER TABLE store_products CHANGE COLUMN stock_quantity stock_quantity INT NOT NULL DEFAULT 0 COMMENT '该店铺库存数量';
ALTER TABLE store_products CHANGE COLUMN price price DECIMAL(10,2) COMMENT '该店铺商品价格（如果为空则使用商品表价格）';
ALTER TABLE store_products CHANGE COLUMN status status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态：ACTIVE-上架，INACTIVE-下架';
ALTER TABLE store_products CHANGE COLUMN create_time create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE store_products CHANGE COLUMN update_time update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE store_products COMMENT = '店铺商品关联表';

SELECT '完成！' AS message;

