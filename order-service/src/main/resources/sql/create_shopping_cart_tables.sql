-- ============================================
-- 创建购物车表（支持分库分表）
-- ============================================
-- 说明：
-- 1. 购物车表按 storeId 分片，与 orders 表保持一致
-- 2. 每个库创建 32 张表：shopping_carts_00..shopping_carts_31
-- 3. 购物车项表：shopping_cart_items_00..shopping_cart_items_31
-- 4. 支持用户购物车（userId）和桌码购物车（tableId）
-- ============================================

-- ============================================
-- jiaoyi_order_0 数据库
-- ============================================
USE jiaoyi_order_0;

-- 创建购物车表（32张表）
CREATE TABLE IF NOT EXISTS shopping_carts_00 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '购物车ID',
    user_id BIGINT COMMENT '用户ID（如果用户已登录）',
    table_id INT COMMENT '桌码ID（堂食场景）',
    merchant_id VARCHAR(64) NOT NULL COMMENT '餐馆ID',
    store_id BIGINT NOT NULL COMMENT '门店ID（用于分片）',
    shard_id INT NOT NULL COMMENT '分片ID（0-1023）',
    total_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '购物车总金额',
    total_quantity INT DEFAULT 0 COMMENT '购物车商品总数',
    expire_time DATETIME NOT NULL COMMENT '购物车过期时间',
    version BIGINT DEFAULT 1 COMMENT '版本号（乐观锁）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_table_id (table_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_store_id (store_id),
    INDEX idx_expire_time (expire_time),
    UNIQUE KEY uk_user_store (user_id, store_id, table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表_00';

-- 创建购物车项表（32张表）
CREATE TABLE IF NOT EXISTS shopping_cart_items_00 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '购物车项ID',
    cart_id BIGINT NOT NULL COMMENT '购物车ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_id BIGINT COMMENT 'SKU ID',
    sku_name VARCHAR(255) COMMENT 'SKU名称',
    sku_attributes TEXT COMMENT 'SKU属性（JSON）',
    product_name VARCHAR(255) NOT NULL COMMENT '商品名称',
    product_image VARCHAR(512) COMMENT '商品图片',
    unit_price DECIMAL(10, 2) NOT NULL COMMENT '商品单价',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    subtotal DECIMAL(10, 2) NOT NULL COMMENT '小计金额',
    options TEXT COMMENT '选项（JSON数组）',
    combo_detail TEXT COMMENT '套餐详情（JSON）',
    version BIGINT DEFAULT 1 COMMENT '版本号（乐观锁）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_cart_id (cart_id),
    INDEX idx_product_id (product_id),
    INDEX idx_sku_id (sku_id),
    UNIQUE KEY uk_cart_product_sku (cart_id, product_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车项表_00';

-- 批量创建其他31张表（shopping_carts_01 到 shopping_carts_31）
CREATE TABLE IF NOT EXISTS shopping_carts_01 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_01 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_02 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_02 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_03 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_03 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_04 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_04 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_05 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_05 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_06 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_06 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_07 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_07 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_08 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_08 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_09 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_09 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_10 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_10 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_11 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_11 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_12 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_12 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_13 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_13 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_14 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_14 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_15 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_15 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_16 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_16 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_17 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_17 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_18 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_18 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_19 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_19 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_20 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_20 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_21 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_21 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_22 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_22 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_23 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_23 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_24 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_24 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_25 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_25 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_26 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_26 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_27 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_27 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_28 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_28 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_29 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_29 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_30 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_30 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_31 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_31 LIKE shopping_cart_items_00;

-- ============================================
-- jiaoyi_order_1 数据库
-- ============================================
USE jiaoyi_order_1;

-- 复制表结构（使用 LIKE 语法）
CREATE TABLE IF NOT EXISTS shopping_carts_00 LIKE jiaoyi_order_0.shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_00 LIKE jiaoyi_order_0.shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_01 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_01 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_02 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_02 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_03 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_03 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_04 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_04 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_05 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_05 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_06 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_06 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_07 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_07 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_08 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_08 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_09 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_09 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_10 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_10 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_11 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_11 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_12 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_12 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_13 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_13 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_14 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_14 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_15 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_15 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_16 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_16 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_17 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_17 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_18 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_18 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_19 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_19 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_20 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_20 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_21 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_21 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_22 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_22 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_23 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_23 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_24 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_24 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_25 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_25 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_26 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_26 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_27 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_27 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_28 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_28 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_29 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_29 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_30 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_30 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_31 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_31 LIKE shopping_cart_items_00;

-- ============================================
-- jiaoyi_order_2 数据库
-- ============================================
USE jiaoyi_order_2;

CREATE TABLE IF NOT EXISTS shopping_carts_00 LIKE jiaoyi_order_0.shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_00 LIKE jiaoyi_order_0.shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_01 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_01 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_02 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_02 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_03 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_03 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_04 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_04 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_05 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_05 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_06 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_06 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_07 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_07 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_08 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_08 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_09 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_09 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_10 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_10 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_11 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_11 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_12 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_12 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_13 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_13 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_14 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_14 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_15 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_15 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_16 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_16 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_17 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_17 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_18 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_18 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_19 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_19 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_20 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_20 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_21 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_21 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_22 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_22 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_23 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_23 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_24 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_24 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_25 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_25 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_26 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_26 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_27 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_27 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_28 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_28 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_29 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_29 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_30 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_30 LIKE shopping_cart_items_00;
CREATE TABLE IF NOT EXISTS shopping_carts_31 LIKE shopping_carts_00;
CREATE TABLE IF NOT EXISTS shopping_cart_items_31 LIKE shopping_cart_items_00;

