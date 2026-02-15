-- 渠道库存贡献追踪 + 水位线字段
-- 用于支持：动态再平衡、贡献度分析、自适应权重调整

ALTER TABLE poi_item_channel_stock
    ADD COLUMN borrowed_from_pool DECIMAL(10,1) DEFAULT 0 COMMENT '从共享池借调累计量（渠道额度不够时从共享池借的总量）' AFTER channel_weight,
    ADD COLUMN contributed_to_pool DECIMAL(10,1) DEFAULT 0 COMMENT '向共享池贡献累计量（渠道额度过剩被回收到共享池的总量）' AFTER borrowed_from_pool,
    ADD COLUMN utilization_rate DECIMAL(5,1) DEFAULT 0 COMMENT '渠道使用率水位线（0-100%，channel_sold/channel_quota*100）' AFTER contributed_to_pool;

-- 新增索引：按使用率查询（用于水位线检测和自动再平衡）
ALTER TABLE poi_item_channel_stock
    ADD INDEX idx_utilization (stock_id, utilization_rate);
