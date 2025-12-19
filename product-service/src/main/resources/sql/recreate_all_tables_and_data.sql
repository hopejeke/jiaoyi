-- ============================================
-- 完整重建脚本：删除所有表 -> 重建表结构 -> 插入测试数据
-- ============================================

-- ============================================
-- 第一步：删除所有表
-- ============================================

-- 删除分片数据库中的表（禁用外键检查以加快速度）
USE jiaoyi_0;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_items_0, order_items_1, order_items_2;
DROP TABLE IF EXISTS orders_0, orders_1, orders_2;
DROP TABLE IF EXISTS merchants_0, merchants_1, merchants_2;
DROP TABLE IF EXISTS store_services_0, store_services_1, store_services_2;
DROP TABLE IF EXISTS menu_items_0, menu_items_1, menu_items_2;
DROP TABLE IF EXISTS store_products_0, store_products_1, store_products_2;
DROP TABLE IF EXISTS product_sku_0, product_sku_1, product_sku_2;
DROP TABLE IF EXISTS inventory_0, inventory_1, inventory_2;
DROP TABLE IF EXISTS inventory_transaction_0, inventory_transaction_1, inventory_transaction_2;
SET FOREIGN_KEY_CHECKS = 1;

USE jiaoyi_1;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_items_0, order_items_1, order_items_2;
DROP TABLE IF EXISTS orders_0, orders_1, orders_2;
DROP TABLE IF EXISTS merchants_0, merchants_1, merchants_2;
DROP TABLE IF EXISTS store_services_0, store_services_1, store_services_2;
DROP TABLE IF EXISTS menu_items_0, menu_items_1, menu_items_2;
DROP TABLE IF EXISTS store_products_0, store_products_1, store_products_2;
DROP TABLE IF EXISTS product_sku_0, product_sku_1, product_sku_2;
DROP TABLE IF EXISTS inventory_0, inventory_1, inventory_2;
DROP TABLE IF EXISTS inventory_transaction_0, inventory_transaction_1, inventory_transaction_2;
SET FOREIGN_KEY_CHECKS = 1;

USE jiaoyi_2;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_items_0, order_items_1, order_items_2;
DROP TABLE IF EXISTS orders_0, orders_1, orders_2;
DROP TABLE IF EXISTS merchants_0, merchants_1, merchants_2;
DROP TABLE IF EXISTS store_services_0, store_services_1, store_services_2;
DROP TABLE IF EXISTS menu_items_0, menu_items_1, menu_items_2;
DROP TABLE IF EXISTS store_products_0, store_products_1, store_products_2;
DROP TABLE IF EXISTS product_sku_0, product_sku_1, product_sku_2;
DROP TABLE IF EXISTS inventory_0, inventory_1, inventory_2;
DROP TABLE IF EXISTS inventory_transaction_0, inventory_transaction_1, inventory_transaction_2;
SET FOREIGN_KEY_CHECKS = 1;

-- 删除主库中的表
USE jiaoyi;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS outbox;
DROP TABLE IF EXISTS outbox_node;
DROP TABLE IF EXISTS snowflake_worker;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 第二步：创建主库表（jiaoyi）
-- ============================================
USE jiaoyi;

-- 创建 stores 表
CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name VARCHAR(200) NOT NULL COMMENT '店铺名称',
    store_code VARCHAR(50) NOT NULL UNIQUE COMMENT '店铺编码',
    description TEXT COMMENT '店铺描述',
    owner_name VARCHAR(100) COMMENT '店主姓名',
    owner_phone VARCHAR(20) COMMENT '店主电话',
    address VARCHAR(500) COMMENT '店铺地址',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态',
    product_list_version BIGINT NOT NULL DEFAULT 0 COMMENT '商品列表版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表（不分片）';

-- 创建 users 表
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    country_code VARCHAR(10) COMMENT '国家代码',
    name VARCHAR(100) COMMENT '姓名',
    password VARCHAR(255) COMMENT '密码（加密）',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    status INT NOT NULL DEFAULT 200 COMMENT '用户状态：100-新用户，200-活跃，666-禁用',
    delivery_address JSON COMMENT '配送地址（JSON格式）',
    stripe_customer_id VARCHAR(100) COMMENT 'Stripe客户ID',
    openid VARCHAR(100) COMMENT '微信OpenID',
    unionid VARCHAR(100) COMMENT '微信UnionID',
    head_img_url VARCHAR(500) COMMENT '微信头像URL',
    regist_channel VARCHAR(50) COMMENT '注册渠道',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表（不分片）';

-- 创建 outbox_node 表
CREATE TABLE outbox_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(50) NOT NULL COMMENT '节点IP地址',
    port INT NOT NULL COMMENT '节点端口',
    node_id VARCHAR(100) NOT NULL UNIQUE COMMENT '节点ID（格式：ip:port）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    expired_time DATETIME NOT NULL COMMENT '心跳过期时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_enabled_expired (enabled, expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox节点表';

-- 创建 outbox 表
CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）',
    topic VARCHAR(100) NOT NULL COMMENT '消息主题',
    tag VARCHAR(50) COMMENT '消息标签',
    message_key VARCHAR(100) COMMENT '消息键',
    message_body TEXT NOT NULL COMMENT '消息体（JSON）',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENT/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息',
    sent_at DATETIME COMMENT '发送时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_id_status (shard_id, status),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox表';

-- 创建 snowflake_worker 表
CREATE TABLE snowflake_worker (
    worker_id INT PRIMARY KEY COMMENT '工作机器ID（0-1023）',
    instance VARCHAR(128) NOT NULL UNIQUE COMMENT '实例标识（hostname或IP:PORT）',
    last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后心跳时间',
    INDEX idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='雪花算法Worker-ID分配表';

-- ============================================
-- 第三步：创建分片表（jiaoyi_0, jiaoyi_1, jiaoyi_2）
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 创建 merchants 表分片
CREATE TABLE merchants_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（POS系统ID）',
    merchant_group_id VARCHAR(50) NOT NULL COMMENT '餐馆组ID',
    encrypt_merchant_id VARCHAR(100) NOT NULL COMMENT '加密的餐馆ID',
    name VARCHAR(200) NOT NULL COMMENT '餐馆名称',
    time_zone VARCHAR(50) NOT NULL COMMENT '时区',
    logo VARCHAR(500) COMMENT '餐馆Logo',
    short_url VARCHAR(200) COMMENT '短链接',
    is_pickup TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否支持自取',
    is_delivery TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否支持配送',
    pickup_payment_acceptance JSON COMMENT '自取支付方式（JSON数组）',
    delivery_payment_acceptance JSON COMMENT '配送支付方式（JSON数组）',
    pickup_prepare_time JSON COMMENT '自取准备时间（JSON：{"min":30,"max":60}）',
    delivery_prepare_time JSON COMMENT '配送准备时间（JSON：{"min":45,"max":90}）',
    pickup_open_time JSON COMMENT '自取营业时间（JSON数组）',
    delivery_open_time JSON COMMENT '配送营业时间（JSON数组）',
    default_delivery_fee VARCHAR(50) COMMENT '默认配送费类型：FLAT_RATE/VARIABLE_RATE/ZONE_RATE',
    delivery_flat_fee DECIMAL(10,2) COMMENT '配送固定费用',
    delivery_variable_rate JSON COMMENT '配送可变费率（JSON数组）',
    delivery_zone_rate JSON COMMENT '配送区域费率（JSON对象）',
    delivery_minimum_amount DECIMAL(10,2) COMMENT '配送最低金额',
    delivery_maximum_distance DECIMAL(10,2) COMMENT '配送最大距离',
    activate TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已激活',
    dl_activate TINYINT(1) NOT NULL DEFAULT 0 COMMENT '配送是否已激活',
    pickup_have_setted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '自取是否已设置',
    delivery_have_setted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '配送是否已设置',
    enable_note TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用备注',
    enable_auto_send TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用自动发送',
    enable_auto_receipt TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用自动接收',
    enable_sdi_auto_receipt TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用堂食自动接收',
    enable_sdi_auto_send TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用堂食自动发送',
    enable_popular_item TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用热门商品',
    display TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否显示',
    personalization JSON COMMENT '个性化配置（JSON对象）',
    capability_of_order JSON COMMENT '订单能力（JSON对象）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_id (merchant_id),
    INDEX idx_merchant_group_id (merchant_group_id),
    INDEX idx_encrypt_merchant_id (encrypt_merchant_id),
    INDEX idx_display (display)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='餐馆表_库0_分片0';

CREATE TABLE merchants_1 LIKE merchants_0;
ALTER TABLE merchants_1 COMMENT='餐馆表_库0_分片1';

CREATE TABLE merchants_2 LIKE merchants_0;
ALTER TABLE merchants_2 COMMENT='餐馆表_库0_分片2';

-- 创建 store_services 表分片
CREATE TABLE store_services_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（用于分片）',
    service_type VARCHAR(50) NOT NULL COMMENT '服务类型：PICKUP/DELIVERY/SELF_DINE_IN',
    payment_acceptance JSON COMMENT '支付方式（JSON数组）',
    prepare_time JSON COMMENT '准备时间（JSON：{"min":30,"max":60}）',
    open_time JSON COMMENT '营业时间（JSON数组）',
    open_time_range JSON COMMENT '营业时间范围（JSON对象）',
    special_hours JSON COMMENT '特殊营业时间（JSON数组）',
    temp_close TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否临时关闭',
    have_set TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已设置',
    activate TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已激活',
    activate_date BIGINT COMMENT '激活时间（时间戳）',
    enable_use TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
    usage_time_period JSON COMMENT '使用时间段（JSON数组）',
    togo_entrance_qr_base64 TEXT COMMENT 'Togo入口二维码（Base64）',
    togo_entrance_qr_mini_program_base64 TEXT COMMENT 'Togo入口小程序二维码（Base64）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_service (merchant_id, service_type),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_service_type (service_type),
    INDEX idx_activate (activate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='餐馆服务表_库0_分片0';

CREATE TABLE store_services_1 LIKE store_services_0;
ALTER TABLE store_services_1 COMMENT='餐馆服务表_库0_分片1';

CREATE TABLE store_services_2 LIKE store_services_0;
ALTER TABLE store_services_2 COMMENT='餐馆服务表_库0_分片2';

-- 创建 menu_items 表分片
CREATE TABLE menu_items_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（用于分片）',
    item_id BIGINT NOT NULL COMMENT '菜品ID（POS系统ID）',
    img_info JSON COMMENT '图片信息（JSON：{"urls":[],"name":"","hisUrl":[]}）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_item (merchant_id, item_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单项信息表_库0_分片0';

CREATE TABLE menu_items_1 LIKE menu_items_0;
ALTER TABLE menu_items_1 COMMENT='菜单项信息表_库0_分片1';

CREATE TABLE menu_items_2 LIKE menu_items_0;
ALTER TABLE menu_items_2 COMMENT='菜单项信息表_库0_分片2';

-- 创建 orders 表分片
CREATE TABLE orders_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（用于分片）',
    user_id BIGINT COMMENT '用户ID',
    order_no VARCHAR(100) NOT NULL UNIQUE COMMENT '订单号',
    order_type VARCHAR(50) NOT NULL COMMENT '订单类型：PICKUP/DELIVERY/SELF_DINE_IN',
    status INT NOT NULL DEFAULT 1 COMMENT '订单状态：1-已下单，100-已支付，-1-已取消等',
    local_status INT NOT NULL DEFAULT 1 COMMENT '本地订单状态：1-已下单，100-成功，200-支付失败等',
    kitchen_status INT COMMENT '厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成',
    order_price JSON COMMENT '订单价格信息（JSON）',
    customer_info JSON COMMENT '客户信息（JSON）',
    delivery_address JSON COMMENT '配送地址（JSON）',
    notes TEXT COMMENT '订单备注',
    pos_order_id VARCHAR(100) COMMENT 'POS系统订单ID',
    payment_method VARCHAR(50) COMMENT '支付方式',
    payment_status VARCHAR(50) COMMENT '支付状态',
    stripe_payment_intent_id VARCHAR(100) COMMENT 'Stripe支付意图ID',
    refund_amount DECIMAL(10,2) DEFAULT 0 COMMENT '退款金额',
    refund_reason TEXT COMMENT '退款原因',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_local_status (local_status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表_库0_分片0';

CREATE TABLE orders_1 LIKE orders_0;
ALTER TABLE orders_1 COMMENT='订单表_库0_分片1';

CREATE TABLE orders_2 LIKE orders_0;
ALTER TABLE orders_2 COMMENT='订单表_库0_分片2';

-- 创建 order_items 表分片
CREATE TABLE order_items_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）',
    merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（用于分片）',
    product_id BIGINT COMMENT '商品ID（用于库存锁定）',
    sale_item_id BIGINT NOT NULL COMMENT '销售项ID（POS系统ID）',
    order_item_id BIGINT NOT NULL COMMENT '订单项ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
    item_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_image VARCHAR(500) COMMENT '商品图片',
    item_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    item_price_total DECIMAL(10,2) NOT NULL COMMENT '小计',
    display_price DECIMAL(10,2) COMMENT '显示价格',
    detail_price_id BIGINT COMMENT '详细价格ID',
    detail_price_info JSON COMMENT '详细价格信息（JSON）',
    size_id BIGINT COMMENT '尺寸ID',
    options JSON COMMENT '选项（JSON数组）',
    combo_detail JSON COMMENT '套餐详情（JSON）',
    discount_name VARCHAR(100) COMMENT '折扣名称',
    charge_name VARCHAR(100) COMMENT '费用名称',
    course_number VARCHAR(50) COMMENT '课程编号',
    item_type VARCHAR(50) COMMENT '商品类型',
    item_number VARCHAR(50) COMMENT '商品编号',
    kitchen_index INT COMMENT '厨房索引',
    category_id BIGINT COMMENT '分类ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_sale_item_id (sale_item_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表_库0_分片0';

CREATE TABLE order_items_1 LIKE order_items_0;
ALTER TABLE order_items_1 COMMENT='订单项表_库0_分片1';

CREATE TABLE order_items_2 LIKE order_items_0;
ALTER TABLE order_items_2 COMMENT='订单项表_库0_分片2';

-- 创建 store_products 表分片
CREATE TABLE store_products_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID（用于分片）',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    product_image VARCHAR(500) COMMENT '商品图片URL',
    category VARCHAR(100) COMMENT '商品分类',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '商品状态',
    is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除（逻辑删除）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁和缓存一致性）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_id (store_id),
    INDEX idx_is_delete (is_delete),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表_库0_分片0';

CREATE TABLE store_products_1 LIKE store_products_0;
ALTER TABLE store_products_1 COMMENT='商品表_库0_分片1';

CREATE TABLE store_products_2 LIKE store_products_0;
ALTER TABLE store_products_2 COMMENT='商品表_库0_分片2';

-- 创建 product_sku 表分片
CREATE TABLE product_sku_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID（用于分片）',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) NOT NULL COMMENT 'SKU编码',
    sku_name VARCHAR(200) NOT NULL COMMENT 'SKU名称',
    price DECIMAL(10,2) NOT NULL COMMENT '价格',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_sku (product_id, sku_code),
    INDEX idx_store_id (store_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表_库0_分片0';

CREATE TABLE product_sku_1 LIKE product_sku_0;
ALTER TABLE product_sku_1 COMMENT='商品SKU表_库0_分片1';

CREATE TABLE product_sku_2 LIKE product_sku_0;
ALTER TABLE product_sku_2 COMMENT='商品SKU表_库0_分片2';

-- 创建 inventory 表分片
CREATE TABLE inventory_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID（用于分片）',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_id BIGINT COMMENT 'SKU ID',
    sku_name VARCHAR(200) COMMENT 'SKU名称',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    current_stock INT NOT NULL DEFAULT 0 COMMENT '当前库存',
    locked_stock INT NOT NULL DEFAULT 0 COMMENT '锁定库存',
    min_stock INT NOT NULL DEFAULT 0 COMMENT '最小库存',
    max_stock INT NOT NULL DEFAULT 0 COMMENT '最大库存',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_store_product_sku (store_id, product_id, sku_id),
    INDEX idx_store_id (store_id),
    INDEX idx_product_id (product_id),
    INDEX idx_sku_id (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表_库0_分片0';

CREATE TABLE inventory_1 LIKE inventory_0;
ALTER TABLE inventory_1 COMMENT='库存表_库0_分片1';

CREATE TABLE inventory_2 LIKE inventory_0;
ALTER TABLE inventory_2 COMMENT='库存表_库0_分片2';

-- ============================================
-- jiaoyi_1 数据库（复制 jiaoyi_0 的表结构）
-- ============================================
USE jiaoyi_1;

CREATE TABLE merchants_0 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_0 COMMENT='餐馆表_库1_分片0';
CREATE TABLE merchants_1 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_1 COMMENT='餐馆表_库1_分片1';
CREATE TABLE merchants_2 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_2 COMMENT='餐馆表_库1_分片2';

CREATE TABLE store_services_0 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_0 COMMENT='餐馆服务表_库1_分片0';
CREATE TABLE store_services_1 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_1 COMMENT='餐馆服务表_库1_分片1';
CREATE TABLE store_services_2 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_2 COMMENT='餐馆服务表_库1_分片2';

CREATE TABLE menu_items_0 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_0 COMMENT='菜单项信息表_库1_分片0';
CREATE TABLE menu_items_1 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_1 COMMENT='菜单项信息表_库1_分片1';
CREATE TABLE menu_items_2 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_2 COMMENT='菜单项信息表_库1_分片2';

CREATE TABLE orders_0 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_0 COMMENT='订单表_库1_分片0';
CREATE TABLE orders_1 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_1 COMMENT='订单表_库1_分片1';
CREATE TABLE orders_2 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_2 COMMENT='订单表_库1_分片2';

CREATE TABLE order_items_0 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_0 COMMENT='订单项表_库1_分片0';
CREATE TABLE order_items_1 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_1 COMMENT='订单项表_库1_分片1';
CREATE TABLE order_items_2 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_2 COMMENT='订单项表_库1_分片2';

CREATE TABLE store_products_0 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_0 COMMENT='商品表_库1_分片0';
CREATE TABLE store_products_1 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_1 COMMENT='商品表_库1_分片1';
CREATE TABLE store_products_2 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_2 COMMENT='商品表_库1_分片2';

CREATE TABLE product_sku_0 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_0 COMMENT='商品SKU表_库1_分片0';
CREATE TABLE product_sku_1 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_1 COMMENT='商品SKU表_库1_分片1';
CREATE TABLE product_sku_2 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_2 COMMENT='商品SKU表_库1_分片2';

CREATE TABLE inventory_0 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_0 COMMENT='库存表_库1_分片0';
CREATE TABLE inventory_1 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_1 COMMENT='库存表_库1_分片1';
CREATE TABLE inventory_2 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_2 COMMENT='库存表_库1_分片2';

-- ============================================
-- jiaoyi_2 数据库（复制 jiaoyi_0 的表结构）
-- ============================================
USE jiaoyi_2;

CREATE TABLE merchants_0 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_0 COMMENT='餐馆表_库2_分片0';
CREATE TABLE merchants_1 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_1 COMMENT='餐馆表_库2_分片1';
CREATE TABLE merchants_2 LIKE jiaoyi_0.merchants_0;
ALTER TABLE merchants_2 COMMENT='餐馆表_库2_分片2';

CREATE TABLE store_services_0 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_0 COMMENT='餐馆服务表_库2_分片0';
CREATE TABLE store_services_1 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_1 COMMENT='餐馆服务表_库2_分片1';
CREATE TABLE store_services_2 LIKE jiaoyi_0.store_services_0;
ALTER TABLE store_services_2 COMMENT='餐馆服务表_库2_分片2';

CREATE TABLE menu_items_0 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_0 COMMENT='菜单项信息表_库2_分片0';
CREATE TABLE menu_items_1 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_1 COMMENT='菜单项信息表_库2_分片1';
CREATE TABLE menu_items_2 LIKE jiaoyi_0.menu_items_0;
ALTER TABLE menu_items_2 COMMENT='菜单项信息表_库2_分片2';

CREATE TABLE orders_0 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_0 COMMENT='订单表_库2_分片0';
CREATE TABLE orders_1 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_1 COMMENT='订单表_库2_分片1';
CREATE TABLE orders_2 LIKE jiaoyi_0.orders_0;
ALTER TABLE orders_2 COMMENT='订单表_库2_分片2';

CREATE TABLE order_items_0 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_0 COMMENT='订单项表_库2_分片0';
CREATE TABLE order_items_1 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_1 COMMENT='订单项表_库2_分片1';
CREATE TABLE order_items_2 LIKE jiaoyi_0.order_items_0;
ALTER TABLE order_items_2 COMMENT='订单项表_库2_分片2';

CREATE TABLE store_products_0 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_0 COMMENT='商品表_库2_分片0';
CREATE TABLE store_products_1 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_1 COMMENT='商品表_库2_分片1';
CREATE TABLE store_products_2 LIKE jiaoyi_0.store_products_0;
ALTER TABLE store_products_2 COMMENT='商品表_库2_分片2';

CREATE TABLE product_sku_0 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_0 COMMENT='商品SKU表_库2_分片0';
CREATE TABLE product_sku_1 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_1 COMMENT='商品SKU表_库2_分片1';
CREATE TABLE product_sku_2 LIKE jiaoyi_0.product_sku_0;
ALTER TABLE product_sku_2 COMMENT='商品SKU表_库2_分片2';

CREATE TABLE inventory_0 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_0 COMMENT='库存表_库2_分片0';
CREATE TABLE inventory_1 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_1 COMMENT='库存表_库2_分片1';
CREATE TABLE inventory_2 LIKE jiaoyi_0.inventory_0;
ALTER TABLE inventory_2 COMMENT='库存表_库2_分片2';

-- ============================================
-- 第四步：插入测试数据
-- ============================================

-- 插入主库测试数据
USE jiaoyi;

-- 插入 stores 数据
INSERT INTO stores (id, store_name, store_code, description, owner_name, owner_phone, address, status, product_list_version, create_time, update_time) VALUES
(0, '测试店铺0', 'STORE_000', '这是测试店铺0', '张三', '13800000000', '北京市朝阳区测试街道0号', 'ACTIVE', 0, NOW(), NOW()),
(1, '测试店铺1', 'STORE_001', '这是测试店铺1', '李四', '13800000001', '北京市朝阳区测试街道1号', 'ACTIVE', 0, NOW(), NOW()),
(2, '测试店铺2', 'STORE_002', '这是测试店铺2', '王五', '13800000002', '北京市朝阳区测试街道2号', 'ACTIVE', 0, NOW(), NOW()),
(3, '测试店铺3', 'STORE_003', '这是测试店铺3', '赵六', '13800000003', '上海市浦东新区测试街道3号', 'ACTIVE', 0, NOW(), NOW()),
(4, '测试店铺4', 'STORE_004', '这是测试店铺4', '钱七', '13800000004', '上海市浦东新区测试街道4号', 'ACTIVE', 0, NOW(), NOW()),
(5, '测试店铺5', 'STORE_005', '这是测试店铺5', '孙八', '13800000005', '上海市浦东新区测试街道5号', 'ACTIVE', 0, NOW(), NOW()),
(6, '测试店铺6', 'STORE_006', '这是测试店铺6', '周九', '13800000006', '广州市天河区测试街道6号', 'ACTIVE', 0, NOW(), NOW()),
(7, '测试店铺7', 'STORE_007', '这是测试店铺7', '吴十', '13800000007', '广州市天河区测试街道7号', 'ACTIVE', 0, NOW(), NOW()),
(8, '测试店铺8', 'STORE_008', '这是测试店铺8', '郑十一', '13800000008', '广州市天河区测试街道8号', 'ACTIVE', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE store_name=VALUES(store_name);

-- 插入 users 数据
INSERT INTO users (id, email, name, phone, status, create_time, update_time) VALUES
(1, 'user1@example.com', '测试用户1', '13800138001', 200, NOW(), NOW()),
(2, 'user2@example.com', '测试用户2', '13800138002', 200, NOW(), NOW()),
(3, 'user3@example.com', '测试用户3', '13800138003', 200, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入分片数据库测试数据（merchants）
-- 注意：merchant_id 的哈希值决定了分片位置，这里手动插入到对应的分片表

USE jiaoyi_0;
-- merchant_001, merchant_004, merchant_007 会路由到 jiaoyi_0
INSERT INTO merchants_0 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_001', 'group_001', 'encrypt_001', '川味小厨', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_004', 'group_001', 'encrypt_004', '湘味餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_007', 'group_002', 'encrypt_007', '粤式茶餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_1 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_010', 'group_002', 'encrypt_010', '东北饺子馆', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_2 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_013', 'group_003', 'encrypt_013', '西式快餐', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

USE jiaoyi_1;
-- merchant_002, merchant_005, merchant_008 会路由到 jiaoyi_1
INSERT INTO merchants_0 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_002', 'group_001', 'encrypt_002', '鲁菜馆', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_005', 'group_001', 'encrypt_005', '苏菜餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_008', 'group_002', 'encrypt_008', '日式料理', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_1 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_011', 'group_002', 'encrypt_011', '韩式烤肉', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_2 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_014', 'group_003', 'encrypt_014', '泰式餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

USE jiaoyi_2;
-- merchant_003, merchant_006, merchant_009 会路由到 jiaoyi_2
INSERT INTO merchants_0 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_003', 'group_001', 'encrypt_003', '徽菜馆', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_006', 'group_001', 'encrypt_006', '浙菜餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW()),
('merchant_009', 'group_002', 'encrypt_009', '意式餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_1 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_012', 'group_002', 'encrypt_012', '法式餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO merchants_2 (merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, is_pickup, is_delivery, display, version, create_time, update_time) VALUES
('merchant_015', 'group_003', 'encrypt_015', '印度餐厅', 'Asia/Shanghai', 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 插入 store_products 测试数据（按 store_id 分片）
-- ============================================

USE jiaoyi_0;
-- store_id 0, 1, 2 路由到 jiaoyi_0
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(0, '商品0-1', '店铺0的商品1', 99.00, 'https://example.com/image1.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(0, '商品0-2', '店铺0的商品2', 199.00, 'https://example.com/image2.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(0, '商品0-3', '店铺0的商品3', 299.00, 'https://example.com/image3.jpg', '服装', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(1, '商品1-1', '店铺1的商品1', 89.00, 'https://example.com/image4.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(1, '商品1-2', '店铺1的商品2', 189.00, 'https://example.com/image5.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(1, '商品1-3', '店铺1的商品3', 289.00, 'https://example.com/image6.jpg', '日用品', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(2, '商品2-1', '店铺2的商品1', 79.00, 'https://example.com/image7.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(2, '商品2-2', '店铺2的商品2', 179.00, 'https://example.com/image8.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(2, '商品2-3', '店铺2的商品3', 279.00, 'https://example.com/image9.jpg', '文具', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

USE jiaoyi_1;
-- store_id 3, 4, 5 路由到 jiaoyi_1
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(3, '商品3-1', '店铺3的商品1', 109.00, 'https://example.com/image10.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(3, '商品3-2', '店铺3的商品2', 209.00, 'https://example.com/image11.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(3, '商品3-3', '店铺3的商品3', 309.00, 'https://example.com/image12.jpg', '服装', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(4, '商品4-1', '店铺4的商品1', 119.00, 'https://example.com/image13.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(4, '商品4-2', '店铺4的商品2', 219.00, 'https://example.com/image14.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(4, '商品4-3', '店铺4的商品3', 319.00, 'https://example.com/image15.jpg', '日用品', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(5, '商品5-1', '店铺5的商品1', 129.00, 'https://example.com/image16.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(5, '商品5-2', '店铺5的商品2', 229.00, 'https://example.com/image17.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(5, '商品5-3', '店铺5的商品3', 329.00, 'https://example.com/image18.jpg', '文具', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

USE jiaoyi_2;
-- store_id 6, 7, 8 路由到 jiaoyi_2
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(6, '商品6-1', '店铺6的商品1', 139.00, 'https://example.com/image19.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(6, '商品6-2', '店铺6的商品2', 239.00, 'https://example.com/image20.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(6, '商品6-3', '店铺6的商品3', 339.00, 'https://example.com/image21.jpg', '服装', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(7, '商品7-1', '店铺7的商品1', 149.00, 'https://example.com/image22.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(7, '商品7-2', '店铺7的商品2', 249.00, 'https://example.com/image23.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(7, '商品7-3', '店铺7的商品3', 349.00, 'https://example.com/image24.jpg', '日用品', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(8, '商品8-1', '店铺8的商品1', 159.00, 'https://example.com/image25.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(8, '商品8-2', '店铺8的商品2', 259.00, 'https://example.com/image26.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(8, '商品8-3', '店铺8的商品3', 359.00, 'https://example.com/image27.jpg', '文具', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- ============================================
-- 插入 inventory 测试数据（按 store_id 分片，与 store_products 对应）
-- ============================================
-- 注意：由于 product_id 是雪花算法生成的，这里使用占位符，实际使用时需要通过应用层查询获取

USE jiaoyi_0;
-- 注意：product_id 需要从实际插入的 store_products 中获取，这里先不插入，由应用层自动创建

USE jiaoyi_1;
-- 注意：product_id 需要从实际插入的 store_products 中获取，这里先不插入，由应用层自动创建

USE jiaoyi_2;
-- 注意：product_id 需要从实际插入的 store_products 中获取，这里先不插入，由应用层自动创建

-- ============================================
-- 插入 store_services 测试数据（按 merchant_id 分片）
-- ============================================

USE jiaoyi_0;
INSERT INTO store_services_0 (merchant_id, service_type, activate, enable_use, version, create_time, update_time) VALUES
('merchant_001', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_001', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_004', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_004', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_007', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_007', 'DELIVERY', 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE activate=VALUES(activate);

USE jiaoyi_1;
INSERT INTO store_services_0 (merchant_id, service_type, activate, enable_use, version, create_time, update_time) VALUES
('merchant_002', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_002', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_005', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_005', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_008', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_008', 'DELIVERY', 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE activate=VALUES(activate);

USE jiaoyi_2;
INSERT INTO store_services_0 (merchant_id, service_type, activate, enable_use, version, create_time, update_time) VALUES
('merchant_003', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_003', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_006', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_006', 'DELIVERY', 1, 1, 1, NOW(), NOW()),
('merchant_009', 'PICKUP', 1, 1, 1, NOW(), NOW()),
('merchant_009', 'DELIVERY', 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE activate=VALUES(activate);

-- ============================================
-- 插入 menu_items 测试数据（按 merchant_id 分片）
-- ============================================

USE jiaoyi_0;
INSERT INTO menu_items_0 (merchant_id, item_id, img_info, version, create_time, update_time) VALUES
('merchant_001', 1001, '{"urls":["https://example.com/menu1.jpg"],"name":"宫保鸡丁","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_001', 1002, '{"urls":["https://example.com/menu2.jpg"],"name":"麻婆豆腐","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_001', 1003, '{"urls":["https://example.com/menu3.jpg"],"name":"回锅肉","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_004', 2001, '{"urls":["https://example.com/menu4.jpg"],"name":"剁椒鱼头","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_004', 2002, '{"urls":["https://example.com/menu5.jpg"],"name":"湘味小炒肉","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_007', 3001, '{"urls":["https://example.com/menu6.jpg"],"name":"叉烧包","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_007', 3002, '{"urls":["https://example.com/menu7.jpg"],"name":"虾饺","hisUrl":[]}', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE img_info=VALUES(img_info);

USE jiaoyi_1;
INSERT INTO menu_items_0 (merchant_id, item_id, img_info, version, create_time, update_time) VALUES
('merchant_002', 4001, '{"urls":["https://example.com/menu8.jpg"],"name":"糖醋里脊","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_002', 4002, '{"urls":["https://example.com/menu9.jpg"],"name":"九转大肠","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_005', 5001, '{"urls":["https://example.com/menu10.jpg"],"name":"松鼠桂鱼","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_005', 5002, '{"urls":["https://example.com/menu11.jpg"],"name":"东坡肉","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_008', 6001, '{"urls":["https://example.com/menu12.jpg"],"name":"寿司拼盘","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_008', 6002, '{"urls":["https://example.com/menu13.jpg"],"name":"拉面","hisUrl":[]}', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE img_info=VALUES(img_info);

USE jiaoyi_2;
INSERT INTO menu_items_0 (merchant_id, item_id, img_info, version, create_time, update_time) VALUES
('merchant_003', 7001, '{"urls":["https://example.com/menu14.jpg"],"name":"徽州臭鳜鱼","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_003', 7002, '{"urls":["https://example.com/menu15.jpg"],"name":"毛豆腐","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_006', 8001, '{"urls":["https://example.com/menu16.jpg"],"name":"西湖醋鱼","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_006', 8002, '{"urls":["https://example.com/menu17.jpg"],"name":"龙井虾仁","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_009', 9001, '{"urls":["https://example.com/menu18.jpg"],"name":"意大利面","hisUrl":[]}', 1, NOW(), NOW()),
('merchant_009', 9002, '{"urls":["https://example.com/menu19.jpg"],"name":"披萨","hisUrl":[]}', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE img_info=VALUES(img_info);

-- ============================================
-- 脚本执行完成提示
-- ============================================
SELECT '✓ 所有表已删除、重建并插入测试数据完成！' AS result;

