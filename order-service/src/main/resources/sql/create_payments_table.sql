-- ============================================
-- 创建支付记录表（payments）
-- 参照 OO 项目的 Payment 模型设计
-- 注意：payments 表需要分片，分片键为 merchant_id
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 创建 payments 表分片
CREATE TABLE payments_0 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '支付ID',
    order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）',
    transaction_id BIGINT COMMENT '交易ID（用于关联交易记录）',
    merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）',
    status INT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付，100-成功，200-失败',
    type INT NOT NULL DEFAULT 100 COMMENT '支付类型：100-扣款，200-退款',
    category INT NOT NULL COMMENT '支付方式类别：1-信用卡，7-现金，8-微信支付，9-支付宝',
    payment_service VARCHAR(50) NOT NULL COMMENT '支付服务：ALIPAY, WECHAT_PAY, CASH, STRIPE',
    payment_no VARCHAR(100) NOT NULL UNIQUE COMMENT '支付流水号',
    third_party_trade_no VARCHAR(100) COMMENT '第三方支付平台交易号',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    tip_amount DECIMAL(10,2) DEFAULT 0 COMMENT '小费金额',
    order_price JSON COMMENT '订单价格信息（JSON）',
    card_info JSON COMMENT '卡片信息（JSON，用于信用卡支付）',
    extra JSON COMMENT '额外信息（JSON，存储第三方支付平台的完整响应）',
    stripe_payment_intent_id VARCHAR(100) COMMENT 'Stripe Payment Intent ID（用于异步支付）',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_payment_no (payment_no),
    INDEX idx_order_id (order_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_payment_service (payment_service),
    INDEX idx_third_party_trade_no (third_party_trade_no),
    INDEX idx_stripe_payment_intent_id (stripe_payment_intent_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表_库0_分片0';

CREATE TABLE payments_1 LIKE payments_0;
ALTER TABLE payments_1 COMMENT='支付记录表_库0_分片1';

CREATE TABLE payments_2 LIKE payments_0;
ALTER TABLE payments_2 COMMENT='支付记录表_库0_分片2';

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

CREATE TABLE payments_0 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_0 COMMENT='支付记录表_库1_分片0';
CREATE TABLE payments_1 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_1 COMMENT='支付记录表_库1_分片1';
CREATE TABLE payments_2 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_2 COMMENT='支付记录表_库1_分片2';

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

CREATE TABLE payments_0 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_0 COMMENT='支付记录表_库2_分片0';
CREATE TABLE payments_1 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_1 COMMENT='支付记录表_库2_分片1';
CREATE TABLE payments_2 LIKE jiaoyi_0.payments_0;
ALTER TABLE payments_2 COMMENT='支付记录表_库2_分片2';














