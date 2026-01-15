-- 为所有库存表添加 stock_mode 字段
-- 注意：如果字段已存在，执行会报错，可以忽略

-- ============================================
-- 数据库 jiaoyi_0
-- ============================================
USE jiaoyi_0;

-- inventory_0
ALTER TABLE inventory_0 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_0 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_1
ALTER TABLE inventory_1 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_1 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_2
ALTER TABLE inventory_2 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_2 
ADD INDEX idx_stock_mode (stock_mode);

-- ============================================
-- 数据库 jiaoyi_1
-- ============================================
USE jiaoyi_1;

-- inventory_0
ALTER TABLE inventory_0 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_0 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_1
ALTER TABLE inventory_1 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_1 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_2
ALTER TABLE inventory_2 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_2 
ADD INDEX idx_stock_mode (stock_mode);

-- ============================================
-- 数据库 jiaoyi_2
-- ============================================
USE jiaoyi_2;

-- inventory_0
ALTER TABLE inventory_0 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_0 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_1
ALTER TABLE inventory_1 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_1 
ADD INDEX idx_stock_mode (stock_mode);

-- inventory_2
ALTER TABLE inventory_2 
ADD COLUMN stock_mode VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' 
COMMENT '库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）' 
AFTER sku_name;

ALTER TABLE inventory_2 
ADD INDEX idx_stock_mode (stock_mode);
