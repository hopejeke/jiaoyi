-- 修改products表，使create_time和update_time由数据库自动生成

-- 修改create_time：设置默认值为CURRENT_TIMESTAMP
ALTER TABLE products 
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 修改update_time：设置默认值和自动更新
ALTER TABLE products 
MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

