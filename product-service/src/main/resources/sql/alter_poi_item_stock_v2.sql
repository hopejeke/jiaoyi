-- ============================================================
-- 库存表 V2 升级：支持渠道隔离、共享池、冲突合并、离线回放、超卖检测
-- ============================================================

-- 1. 库存主表：增加共享池、最后手动设置时间
ALTER TABLE poi_item_stock
    ADD COLUMN IF NOT EXISTS shared_pool_quantity DECIMAL(10,1) DEFAULT 0 COMMENT '共享池库存（不分配给任何渠道的弹性库存）',
    ADD COLUMN IF NOT EXISTS last_manual_set_time TIMESTAMP NULL COMMENT '最后一次手动设置（ABSOLUTE_SET）时间，用于冲突合并';

-- 2. 渠道库存表：增加渠道额度、已售数量、权重
ALTER TABLE poi_item_channel_stock
    ADD COLUMN IF NOT EXISTS channel_quota DECIMAL(10,1) DEFAULT 0 COMMENT '渠道分配额度',
    ADD COLUMN IF NOT EXISTS channel_sold DECIMAL(10,1) DEFAULT 0 COMMENT '渠道已售数量',
    ADD COLUMN IF NOT EXISTS channel_weight DECIMAL(3,2) DEFAULT 0.33 COMMENT '渠道分配权重（0-1，所有渠道权重之和=1）';

-- 3. 库存变更记录表：增加变更类型、变更量、来源、订单ID
ALTER TABLE poi_item_stock_log
    ADD COLUMN IF NOT EXISTS change_type VARCHAR(20) NOT NULL DEFAULT 'ABSOLUTE_SET' COMMENT '变更类型：ABSOLUTE_SET-绝对设置, RELATIVE_DELTA-相对变更(订单扣减), STATUS_CHANGE-状态变更',
    ADD COLUMN IF NOT EXISTS delta DECIMAL(10,1) DEFAULT 0 COMMENT '变更量（RELATIVE_DELTA时有效，负数表示扣减）',
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'CLOUD' COMMENT '来源：POS-POS在线, CLOUD-商品中心, POS_OFFLINE-POS离线回放',
    ADD COLUMN IF NOT EXISTS order_id VARCHAR(64) NULL COMMENT '关联订单ID（用于幂等，RELATIVE_DELTA时必填）';

-- 4. 库存变更记录表：添加订单幂等索引
ALTER TABLE poi_item_stock_log
    ADD INDEX IF NOT EXISTS idx_order_id (order_id);

-- 5. 超卖记录表
CREATE TABLE IF NOT EXISTS oversell_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id VARCHAR(32) NOT NULL COMMENT '品牌ID',
    store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
    stock_id BIGINT NOT NULL COMMENT '关联 poi_item_stock.id',
    object_id BIGINT NOT NULL COMMENT '商品对象ID',
    oversell_quantity DECIMAL(10,1) NOT NULL COMMENT '超卖数量',
    source VARCHAR(20) NOT NULL COMMENT '触发来源：POS_OFFLINE-POS离线回放, SYNC_CONFLICT-同步冲突',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理, CONFIRMED-已确认可做, REFUND-需退款, RESOLVED-已解决',
    resolved_by VARCHAR(64) NULL COMMENT '处理人',
    resolved_at TIMESTAMP NULL COMMENT '处理时间',
    remark TEXT NULL COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_brand_store (brand_id, store_id),
    INDEX idx_stock_id (stock_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='超卖记录表';
