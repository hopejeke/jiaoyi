-- 创建数据库
CREATE DATABASE IF NOT EXISTS jiaoyi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE jiaoyi;

-- 删除表（如果存在）- 按依赖关系逆序删除
DROP TABLE IF EXISTS outbox;
DROP TABLE IF EXISTS coupon_usage;
DROP TABLE IF EXISTS order_coupons;
DROP TABLE IF EXISTS inventory_transactions;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS store_products;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS coupons;

-- 创建优惠券表（被依赖表，需要先创建）
CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_code VARCHAR(50) NOT NULL UNIQUE COMMENT '优惠券代码',
    coupon_name VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    type VARCHAR(20) NOT NULL COMMENT '优惠券类型：FIXED-固定金额，PERCENTAGE-百分比',
    value DECIMAL(10,2) NOT NULL COMMENT '优惠券面值',
    min_order_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '最低订单金额',
    max_discount_amount DECIMAL(10,2) COMMENT '最大优惠金额',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '优惠券状态：ACTIVE-有效，INACTIVE-无效，EXPIRED-已过期',
    total_quantity INT NOT NULL DEFAULT 0 COMMENT '总发行数量',
    used_quantity INT NOT NULL DEFAULT 0 COMMENT '已使用数量',
    limit_per_user INT NOT NULL DEFAULT 1 COMMENT '每用户限用次数',
    applicable_type VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '适用范围：ALL-全部商品，CATEGORY-指定分类，PRODUCT-指定商品',
    applicable_products TEXT COMMENT '适用商品ID列表，JSON格式',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_coupon_code (coupon_code),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time),
    INDEX idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- 创建订单表
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    status VARCHAR(20) NOT NULL COMMENT '订单状态',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    total_discount_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '总优惠金额',
    actual_amount DECIMAL(10,2) NOT NULL COMMENT '实际支付金额',
    receiver_name VARCHAR(100) NOT NULL COMMENT '收货人姓名',
    receiver_phone VARCHAR(20) NOT NULL COMMENT '收货人电话',
    receiver_address VARCHAR(500) NOT NULL COMMENT '收货地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 创建订单优惠券关联表
CREATE TABLE order_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券ID',
    coupon_code VARCHAR(50) NOT NULL COMMENT '优惠券代码',
    applied_amount DECIMAL(10,2) NOT NULL COMMENT '该优惠券实际抵扣金额',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_coupon_code (coupon_code),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单优惠券关联表';

-- 创建订单项表
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_image VARCHAR(500) COMMENT '商品图片',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    quantity INT NOT NULL COMMENT '购买数量',
    subtotal DECIMAL(10,2) NOT NULL COMMENT '小计金额',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- 创建库存表
CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    product_id BIGINT NOT NULL COMMENT '商品ID（关联store_products.id）',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    current_stock INT NOT NULL COMMENT '当前库存数量',
    locked_stock INT NOT NULL DEFAULT 0 COMMENT '锁定库存数量',
    min_stock INT NOT NULL DEFAULT 0 COMMENT '最低库存预警线',
    max_stock INT COMMENT '最大库存容量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_store_product (store_id, product_id),
    INDEX idx_store_id (store_id),
    INDEX idx_product_id (product_id),
    INDEX idx_store_product (store_id, product_id),
    FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES store_products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 创建库存变动记录表
CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    order_id BIGINT COMMENT '订单ID',
    transaction_type VARCHAR(20) NOT NULL COMMENT '变动类型',
    quantity INT NOT NULL COMMENT '变动数量',
    before_stock INT NOT NULL COMMENT '变动前库存',
    after_stock INT NOT NULL COMMENT '变动后库存',
    before_locked INT NOT NULL DEFAULT 0 COMMENT '变动前锁定库存',
    after_locked INT NOT NULL DEFAULT 0 COMMENT '变动后锁定库存',
    remark TEXT COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_order_id (order_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动记录表';

-- 创建优惠券使用记录表
CREATE TABLE coupon_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL COMMENT '优惠券ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    coupon_code VARCHAR(50) NOT NULL COMMENT '优惠券代码',
    discount_amount DECIMAL(10,2) NOT NULL COMMENT '优惠金额',
    used_time DATETIME NOT NULL COMMENT '使用时间',
    order_amount DECIMAL(10,2) NOT NULL COMMENT '订单金额（使用优惠券前的金额）',
    actual_amount DECIMAL(10,2) NOT NULL COMMENT '实际支付金额（使用优惠券后的金额）',
    status VARCHAR(20) NOT NULL DEFAULT 'USED' COMMENT '使用状态：USED-已使用，REFUNDED-已退款',
    remark TEXT COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_coupon_code (coupon_code),
    INDEX idx_used_time (used_time),
    FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券使用记录表';

-- 创建商品表

-- 创建店铺表
CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name VARCHAR(200) NOT NULL COMMENT '店铺名称',
    store_code VARCHAR(50) NOT NULL UNIQUE COMMENT '店铺编码',
    description TEXT COMMENT '店铺描述',
    owner_name VARCHAR(100) COMMENT '店主姓名',
    owner_phone VARCHAR(20) COMMENT '店主电话',
    address VARCHAR(500) COMMENT '店铺地址',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态：ACTIVE-营业中，INACTIVE-已关闭',
    product_list_version BIGINT NOT NULL DEFAULT 0 COMMENT '商品列表版本号（用于缓存一致性控制）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_code (store_code),
    INDEX idx_store_name (store_name),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- 创建店铺商品表（包含所有商品字段 + store_id）
CREATE TABLE store_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    product_image VARCHAR(500) COMMENT '商品图片',
    category VARCHAR(100) COMMENT '商品分类',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '商品状态：ACTIVE-上架，INACTIVE-下架',
    is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于缓存一致性控制）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_id (store_id),
    INDEX idx_product_name (product_name),
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_is_delete (is_delete),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺商品表';

-- 创建消息发件箱表（Outbox Pattern）
CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(100) NOT NULL COMMENT 'RocketMQ Topic',
    tag VARCHAR(50) NOT NULL COMMENT 'RocketMQ Tag',
    message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪）',
    message_body TEXT NOT NULL COMMENT '消息体（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待发送，SENT-已发送，FAILED-发送失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息（发送失败时记录）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    sent_at DATETIME COMMENT '发送时间',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_topic_tag (topic, tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息发件箱表（Outbox Pattern）';
