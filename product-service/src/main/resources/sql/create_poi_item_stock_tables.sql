-- 商品中心库存管理相关表
-- 注意：这些表存储在 primary 数据源（主库），不分库分表

-- 1. 商品库存主表
CREATE TABLE IF NOT EXISTS poi_item_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id VARCHAR(32) NOT NULL COMMENT '品牌ID',
    store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
    object_type INT NOT NULL COMMENT '对象类型：1-SPU, 2-SKU',
    object_id BIGINT NOT NULL COMMENT '对象ID',
    stock_status INT NOT NULL COMMENT '库存状态：1-可售, 2-售罄',
    stock_type INT DEFAULT 0 COMMENT '库存类型：1-不限量, 2-限量',
    plan_quantity DECIMAL(10,1) DEFAULT 0 COMMENT '计划库存份数（初始设定值）',
    real_quantity DECIMAL(10,1) DEFAULT 0 COMMENT '实时库存（当前可用）',
    auto_restore_type INT DEFAULT 0 COMMENT '自动恢复类型：1-自动恢复, 2-不自动恢复',
    auto_restore_at TIMESTAMP NULL COMMENT '恢复时间',
    shared_pool_quantity DECIMAL(10,1) DEFAULT 0 COMMENT '共享池库存（不分配给任何渠道的弹性库存）',
    last_manual_set_time TIMESTAMP NULL COMMENT '最后一次手动设置（ABSOLUTE_SET）时间，用于冲突合并',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_brand_store_object (brand_id, store_id, object_id),
    INDEX idx_brand_store (brand_id, store_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品库存主表';

-- 2. 商品渠道库存表
CREATE TABLE IF NOT EXISTS poi_item_channel_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id VARCHAR(32) NOT NULL COMMENT '品牌ID',
    store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
    stock_id BIGINT NOT NULL COMMENT '关联 poi_item_stock.id',
    stock_status INT NOT NULL COMMENT '库存状态：1-可售, 2-售罄',
    stock_type INT DEFAULT 0 COMMENT '库存类型：1-不限量, 2-共享限量',
    channel_code VARCHAR(32) NOT NULL COMMENT '渠道代码：POS, KIOSK, ONLINE_ORDER',
    channel_quota DECIMAL(10,1) DEFAULT 0 COMMENT '渠道分配额度',
    channel_sold DECIMAL(10,1) DEFAULT 0 COMMENT '渠道已售数量',
    channel_weight DECIMAL(3,2) DEFAULT 0.33 COMMENT '渠道分配权重（0-1，所有渠道权重之和=1）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_stock_channel (stock_id, channel_code),
    INDEX idx_brand_store (brand_id, store_id),
    INDEX idx_stock_id (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品渠道库存表';

-- 3. 库存变更记录表
CREATE TABLE IF NOT EXISTS poi_item_stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id VARCHAR(32) NOT NULL COMMENT '品牌ID',
    store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
    stock_id BIGINT NOT NULL COMMENT '关联 poi_item_stock.id',
    content TEXT NOT NULL COMMENT '变更内容（JSON格式）',
    change_type VARCHAR(20) NOT NULL DEFAULT 'ABSOLUTE_SET' COMMENT '变更类型：ABSOLUTE_SET/RELATIVE_DELTA/STATUS_CHANGE',
    delta DECIMAL(10,1) DEFAULT 0 COMMENT '变更量（RELATIVE_DELTA时有效，负数表示扣减）',
    source VARCHAR(20) NOT NULL DEFAULT 'CLOUD' COMMENT '来源：POS/CLOUD/POS_OFFLINE',
    order_id VARCHAR(64) NULL COMMENT '关联订单ID（用于幂等）',
    deduct_source VARCHAR(32) NULL COMMENT '扣减来源：FROM_CHANNEL/FROM_SHARED_POOL',
    channel_code VARCHAR(32) NULL COMMENT '渠道代码（归还时用）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_stock_id (stock_id),
    INDEX idx_brand_store (brand_id, store_id),
    INDEX idx_created_at (created_at),
    INDEX idx_order_id (order_id),
    INDEX idx_stock_change_time (stock_id, change_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变更记录表';

-- 4. 超卖记录表
CREATE TABLE IF NOT EXISTS oversell_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id VARCHAR(32) NOT NULL COMMENT '品牌ID',
    store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
    stock_id BIGINT NOT NULL COMMENT '关联 poi_item_stock.id',
    object_id BIGINT NOT NULL COMMENT '商品对象ID',
    oversell_quantity DECIMAL(10,1) NOT NULL COMMENT '超卖数量',
    source VARCHAR(20) NOT NULL COMMENT '触发来源：POS_OFFLINE/SYNC_CONFLICT',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CONFIRMED/REFUND/RESOLVED',
    resolved_by VARCHAR(64) NULL COMMENT '处理人',
    resolved_at TIMESTAMP NULL COMMENT '处理时间',
    remark TEXT NULL COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_brand_store (brand_id, store_id),
    INDEX idx_stock_id (stock_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='超卖记录表';
