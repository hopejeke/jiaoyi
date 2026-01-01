-- ============================================
-- 创建退款单表和退款明细表（分片表）
-- 分片键：merchant_id（与 orders 表保持一致）
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 创建退款单表分片
CREATE TABLE IF NOT EXISTS refunds_0 (
    refund_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '退款ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    payment_id BIGINT COMMENT '关联的支付记录ID',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    request_no VARCHAR(100) NOT NULL COMMENT '退款请求号（幂等键）',
    refund_amount DECIMAL(10,2) NOT NULL COMMENT '退款总金额',
    reason VARCHAR(500) COMMENT '退款原因',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '退款状态：CREATED, PROCESSING, SUCCEEDED, FAILED, CANCELED',
    third_party_refund_id VARCHAR(100) COMMENT '第三方退款ID（Stripe refund_id 或支付宝退款单号）',
    error_message TEXT COMMENT '失败原因',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    processed_at DATETIME COMMENT '处理完成时间',
    UNIQUE KEY uk_request_no (merchant_id, request_no),
    INDEX idx_order_id (order_id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单表_库0_分片0';

CREATE TABLE IF NOT EXISTS refunds_1 LIKE refunds_0;
ALTER TABLE refunds_1 COMMENT='退款单表_库0_分片1';

CREATE TABLE IF NOT EXISTS refunds_2 LIKE refunds_0;
ALTER TABLE refunds_2 COMMENT='退款单表_库0_分片2';

-- 创建退款明细表分片
CREATE TABLE IF NOT EXISTS refund_items_0 (
    refund_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL COMMENT '退款单ID',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    order_item_id BIGINT COMMENT '订单项ID（如果按商品退款）',
    subject VARCHAR(50) NOT NULL COMMENT '退款科目：ITEM, TAX, DELIVERY_FEE, TIPS, CHARGE, DISCOUNT',
    refund_qty INT COMMENT '退款数量（仅商品退款时有效）',
    refund_amount DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    tax_refund DECIMAL(10,2) DEFAULT 0 COMMENT '税费退款（仅商品退款时有效）',
    discount_refund DECIMAL(10,2) DEFAULT 0 COMMENT '折扣退款（仅商品退款时有效）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refund_id (refund_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款明细表_库0_分片0';

CREATE TABLE IF NOT EXISTS refund_items_1 LIKE refund_items_0;
ALTER TABLE refund_items_1 COMMENT='退款明细表_库0_分片1';

CREATE TABLE IF NOT EXISTS refund_items_2 LIKE refund_items_0;
ALTER TABLE refund_items_2 COMMENT='退款明细表_库0_分片2';

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

CREATE TABLE IF NOT EXISTS refunds_0 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_0 COMMENT='退款单表_库1_分片0';
CREATE TABLE IF NOT EXISTS refunds_1 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_1 COMMENT='退款单表_库1_分片1';
CREATE TABLE IF NOT EXISTS refunds_2 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_2 COMMENT='退款单表_库1_分片2';

CREATE TABLE IF NOT EXISTS refund_items_0 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_0 COMMENT='退款明细表_库1_分片0';
CREATE TABLE IF NOT EXISTS refund_items_1 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_1 COMMENT='退款明细表_库1_分片1';
CREATE TABLE IF NOT EXISTS refund_items_2 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_2 COMMENT='退款明细表_库1_分片2';

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

CREATE TABLE IF NOT EXISTS refunds_0 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_0 COMMENT='退款单表_库2_分片0';
CREATE TABLE IF NOT EXISTS refunds_1 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_1 COMMENT='退款单表_库2_分片1';
CREATE TABLE IF NOT EXISTS refunds_2 LIKE jiaoyi_0.refunds_0;
ALTER TABLE refunds_2 COMMENT='退款单表_库2_分片2';

CREATE TABLE IF NOT EXISTS refund_items_0 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_0 COMMENT='退款明细表_库2_分片0';
CREATE TABLE IF NOT EXISTS refund_items_1 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_1 COMMENT='退款明细表_库2_分片1';
CREATE TABLE IF NOT EXISTS refund_items_2 LIKE jiaoyi_0.refund_items_0;
ALTER TABLE refund_items_2 COMMENT='退款明细表_库2_分片2';

