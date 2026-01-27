-- ============================================
-- 创建库存扣减幂等性日志表
-- 需要在每个分片库（jiaoyi_product_0, jiaoyi_product_1, jiaoyi_product_2）中执行
-- ============================================

-- 库 jiaoyi_product_0
USE jiaoyi_product_0;

CREATE TABLE IF NOT EXISTS inventory_deduction_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '幂等键（唯一标识，格式：orderId + "-DEDUCT"）',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于storeId计算，用于分库分表路由）',
    product_ids TEXT NOT NULL COMMENT '商品ID列表（JSON格式）',
    sku_ids TEXT NOT NULL COMMENT 'SKU ID列表（JSON格式）',
    quantities TEXT NOT NULL COMMENT '数量列表（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
    error_message TEXT COMMENT '错误信息（如果失败）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_idempotency_key_shard (idempotency_key, product_shard_id) COMMENT '幂等键+分片ID唯一索引',
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_product_shard_id (product_shard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存扣减幂等性日志表_库0';

-- 库 jiaoyi_product_1
USE jiaoyi_product_1;

CREATE TABLE IF NOT EXISTS inventory_deduction_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '幂等键（唯一标识，格式：orderId + "-DEDUCT"）',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于storeId计算，用于分库分表路由）',
    product_ids TEXT NOT NULL COMMENT '商品ID列表（JSON格式）',
    sku_ids TEXT NOT NULL COMMENT 'SKU ID列表（JSON格式）',
    quantities TEXT NOT NULL COMMENT '数量列表（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
    error_message TEXT COMMENT '错误信息（如果失败）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_idempotency_key_shard (idempotency_key, product_shard_id) COMMENT '幂等键+分片ID唯一索引',
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_product_shard_id (product_shard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存扣减幂等性日志表_库1';

-- 库 jiaoyi_product_2
USE jiaoyi_product_2;

CREATE TABLE IF NOT EXISTS inventory_deduction_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '幂等键（唯一标识，格式：orderId + "-DEDUCT"）',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于storeId计算，用于分库分表路由）',
    product_ids TEXT NOT NULL COMMENT '商品ID列表（JSON格式）',
    sku_ids TEXT NOT NULL COMMENT 'SKU ID列表（JSON格式）',
    quantities TEXT NOT NULL COMMENT '数量列表（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
    error_message TEXT COMMENT '错误信息（如果失败）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_idempotency_key_shard (idempotency_key, product_shard_id) COMMENT '幂等键+分片ID唯一索引',
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_product_shard_id (product_shard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存扣减幂等性日志表_库2';

