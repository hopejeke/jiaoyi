-- Webhook 事件日志表（用于事件幂等）
-- 存储所有支付回调事件，确保同一事件只处理一次
CREATE TABLE IF NOT EXISTS webhook_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL COMMENT '第三方事件ID（Stripe event.id 或支付宝交易号）',
    event_type VARCHAR(100) NOT NULL COMMENT '事件类型（payment_intent.succeeded, TRADE_SUCCESS 等）',
    payment_intent_id VARCHAR(255) COMMENT 'Stripe Payment Intent ID',
    third_party_trade_no VARCHAR(255) COMMENT '第三方交易号（支付宝 trade_no 或 Stripe charge.id）',
    order_id BIGINT COMMENT '订单ID',
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT '状态：RECEIVED-已接收，PROCESSED-已处理，FAILED-处理失败',
    error_message TEXT COMMENT '错误信息（处理失败时记录）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    processed_at DATETIME COMMENT '处理完成时间',
    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_payment_intent_id (payment_intent_id),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook事件日志表（事件幂等）';



