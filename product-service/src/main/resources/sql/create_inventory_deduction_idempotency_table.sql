-- 库存扣减幂等性日志表
-- 用于记录库存扣减请求的幂等性信息，防止重复处理

CREATE TABLE IF NOT EXISTS inventory_deduction_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE COMMENT '幂等键（唯一标识，格式：orderId + "-DEDUCT"）',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_ids TEXT NOT NULL COMMENT '商品ID列表（JSON格式）',
    sku_ids TEXT NOT NULL COMMENT 'SKU ID列表（JSON格式）',
    quantities TEXT NOT NULL COMMENT '数量列表（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
    error_message TEXT COMMENT '错误信息（如果失败）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存扣减幂等性日志表';


