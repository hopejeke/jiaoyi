-- MQ 消费日志表（用于消费幂等）
-- 存储所有 MQ 消息的消费记录，确保同一消息只处理一次
CREATE TABLE IF NOT EXISTS consumer_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL COMMENT '消费者组（RocketMQ Consumer Group）',
    topic VARCHAR(100) NOT NULL COMMENT 'Topic',
    tag VARCHAR(50) COMMENT 'Tag',
    message_key VARCHAR(255) NOT NULL COMMENT '消息Key（eventId 或 idempotencyKey）',
    message_id VARCHAR(100) COMMENT 'RocketMQ MessageId（用于追踪）',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
    error_message TEXT COMMENT '错误信息（处理失败时记录）',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    processed_at DATETIME COMMENT '处理完成时间',
    UNIQUE KEY uk_consumer_message (consumer_group, message_key),
    INDEX idx_topic (topic),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费日志表（消费幂等）';



