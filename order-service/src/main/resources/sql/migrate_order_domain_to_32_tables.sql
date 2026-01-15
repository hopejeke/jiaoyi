-- ============================================
-- 订单域：迁移到32张表/库（固定虚拟桶1024 + 路由表映射）
-- ============================================
-- 说明：
-- 1. 所有订单事实表改为32张表/库（orders_00..orders_31）
-- 2. 所有表必须包含 shard_id 字段（INT NOT NULL）
-- 3. shard_id 计算：hash(brandId) & 1023
-- 4. 分库路由：通过 shard_bucket_route 表查询
-- 5. 分表路由：shard_id % 32
-- 6. 执行前请确保已创建 shard_bucket_route 路由表
-- ============================================

-- ============================================
-- 步骤1：为现有表添加 shard_id 字段（如果不存在）
-- ============================================
-- 注意：此步骤需要为所有现有表（orders_0..orders_2等）添加 shard_id 字段
-- 实际执行时，需要根据现有表结构进行调整

-- orders 表（示例：为 jiaoyi_order_0 的 orders_0..orders_2 添加 shard_id）
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，需要使用存储过程或手动检查
USE jiaoyi_order_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_shard_id_column_if_not_exists()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    
    -- 检查并添加 orders_0 的 shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_0' 
      AND COLUMN_NAME = 'shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE orders_0 ADD COLUMN shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
    END IF;
    
    -- 检查并添加索引
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_0' 
      AND INDEX_NAME = 'idx_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE orders_0 ADD INDEX idx_shard_id (shard_id);
    END IF;
    
    -- 检查并添加 orders_1 的 shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_1' 
      AND COLUMN_NAME = 'shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE orders_1 ADD COLUMN shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
    END IF;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_1' 
      AND INDEX_NAME = 'idx_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE orders_1 ADD INDEX idx_shard_id (shard_id);
    END IF;
    
    -- 检查并添加 orders_2 的 shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_2' 
      AND COLUMN_NAME = 'shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE orders_2 ADD COLUMN shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
    END IF;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_order_0' 
      AND TABLE_NAME = 'orders_2' 
      AND INDEX_NAME = 'idx_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE orders_2 ADD INDEX idx_shard_id (shard_id);
    END IF;
END$$

DELIMITER ;

CALL add_shard_id_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_shard_id_column_if_not_exists;

-- 注意：需要为 jiaoyi_order_1 和 jiaoyi_order_2 也执行相同操作
-- 注意：需要为 order_items, order_coupons, payments, refunds, refund_items, deliveries, doordash_retry_task 也执行相同操作

-- ============================================
-- 步骤2：创建32张表/库（orders_00..orders_31）
-- ============================================
-- 注意：此步骤假设已有3张表（orders_0..orders_2），需要扩展为32张表
-- 实际执行时，需要先迁移数据，然后创建新表结构

USE jiaoyi_order_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_orders_tables_32()
BEGIN
    DECLARE i INT DEFAULT 3;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        SET @comment_text = CONCAT('订单表_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT NOT NULL PRIMARY KEY COMMENT ''订单ID（雪花算法）'',
                merchant_id VARCHAR(50) NOT NULL COMMENT ''餐馆ID'',
                shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于brandId计算）'',
                user_id BIGINT NOT NULL COMMENT ''用户ID'',
                order_type VARCHAR(20) NOT NULL COMMENT ''订单类型：PICKUP/DELIVERY/SELF_DINE_IN'',
                status INT NOT NULL DEFAULT 1 COMMENT ''订单状态：1-已下单，2-已支付，3-制作中，4-已完成，5-已取消，7-支付失败，8-配送中，9-待接单'',
                local_status INT NOT NULL DEFAULT 1 COMMENT ''本地订单状态：1-已下单，100-成功，200-支付失败'',
                kitchen_status INT NOT NULL DEFAULT 1 COMMENT ''厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成'',
                order_price TEXT COMMENT ''订单价格信息（JSON）'',
                customer_info TEXT COMMENT ''客户信息（JSON）'',
                delivery_address TEXT COMMENT ''配送地址（JSON）'',
                notes TEXT COMMENT ''备注'',
                pos_order_id VARCHAR(100) COMMENT ''POS系统订单ID'',
                payment_method VARCHAR(50) COMMENT ''支付方式'',
                payment_status VARCHAR(50) COMMENT ''支付状态'',
                stripe_payment_intent_id VARCHAR(200) COMMENT ''Stripe支付意图ID'',
                refund_amount DECIMAL(10,2) COMMENT ''退款金额'',
                refund_reason VARCHAR(500) COMMENT ''退款原因'',
                delivery_id VARCHAR(100) COMMENT ''DoorDash配送ID'',
                version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（乐观锁）'',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                INDEX idx_merchant_id (merchant_id),
                INDEX idx_shard_id (shard_id),
                INDEX idx_user_id (user_id),
                INDEX idx_status (status),
                INDEX idx_create_time (create_time),
                INDEX idx_delivery_id (delivery_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''', @comment_text, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_orders_tables_32();
DROP PROCEDURE IF EXISTS create_orders_tables_32;

-- 注意：需要为 jiaoyi_order_1 和 jiaoyi_order_2 也执行相同的存储过程
-- 注意：需要为 order_items, order_coupons, payments, refunds, refund_items, deliveries, doordash_retry_task 也执行类似操作

-- ============================================
-- 步骤3：重命名现有表（orders_0 -> orders_00, orders_1 -> orders_01, orders_2 -> orders_02）
-- ============================================
-- 注意：MySQL不支持直接重命名表，需要先创建新表，迁移数据，然后删除旧表
-- 此步骤需要谨慎执行，建议在低峰期进行

-- 示例（仅展示逻辑，实际执行需要数据迁移）：
-- RENAME TABLE orders_0 TO orders_00;
-- RENAME TABLE orders_1 TO orders_01;
-- RENAME TABLE orders_2 TO orders_02;

-- ============================================
-- 步骤4：迁移商户配置表到基础库（单表）
-- ============================================
USE jiaoyi;

-- 创建 merchant_stripe_config 单表
CREATE TABLE IF NOT EXISTS merchant_stripe_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID',
    stripe_account_id VARCHAR(100) COMMENT 'Stripe Connected Account ID（acct_xxx）',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 Stripe Connect',
    currency VARCHAR(10) DEFAULT 'USD' COMMENT '货币代码（USD, CAD 等）',
    application_fee_percentage DECIMAL(5,2) DEFAULT 2.50 COMMENT '平台手续费率（百分比）',
    application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '平台固定手续费（元）',
    amex_application_fee_percentage DECIMAL(5,2) DEFAULT 3.50 COMMENT '美国运通手续费率（百分比）',
    amex_application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '美国运通固定手续费（元）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_stripe_account_id (stripe_account_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户Stripe配置表（单表）';

-- 创建 merchant_fee_config 单表
CREATE TABLE IF NOT EXISTS merchant_fee_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID',
    delivery_fee_type VARCHAR(20) DEFAULT 'FLAT_RATE' COMMENT '配送费类型：FLAT_RATE-固定费率，VARIABLE_RATE-按距离可变费率，ZONE_RATE-按邮编区域费率',
    delivery_fee_fixed DECIMAL(10,2) DEFAULT 5.00 COMMENT '配送费固定金额（元，FLAT_RATE 时使用）',
    delivery_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '配送费百分比（已废弃，保留兼容）',
    delivery_fee_min DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最低金额（元）',
    delivery_fee_max DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最高金额（元）',
    delivery_fee_free_threshold DECIMAL(10,2) DEFAULT 0.00 COMMENT '免配送费门槛（订单金额达到此金额免配送费，元）',
    delivery_variable_rate TEXT COMMENT '按距离的可变费率（JSON格式，VARIABLE_RATE 时使用）',
    delivery_zone_rate TEXT COMMENT '按邮编区域的费率（JSON格式，ZONE_RATE 时使用）',
    delivery_maximum_distance DECIMAL(10,2) DEFAULT 0.00 COMMENT '最大配送距离（英里，mile）',
    merchant_latitude DECIMAL(10,7) COMMENT '商户纬度（用于计算配送距离）',
    merchant_longitude DECIMAL(10,7) COMMENT '商户经度（用于计算配送距离）',
    delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）',
    tax_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '税率（百分比，如 8.0 表示 8%）',
    tax_exempt TINYINT(1) DEFAULT 0 COMMENT '是否免税：1-免税，0-不免税',
    online_service_fee_type VARCHAR(20) DEFAULT 'NONE' COMMENT '在线服务费类型：FIXED-固定费用，PERCENTAGE-百分比，NONE-无',
    online_service_fee_fixed DECIMAL(10,2) DEFAULT 0.00 COMMENT '在线服务费固定金额（元）',
    online_service_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '在线服务费百分比',
    online_service_fee_strategy TEXT COMMENT '在线服务费策略（JSON格式，存储阶梯费率配置）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_delivery_fee_type (delivery_fee_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户费用配置表（单表）';

-- 创建 merchant_capability_config 单表
CREATE TABLE IF NOT EXISTS merchant_capability_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID',
    enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用',
    qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）',
    time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）',
    closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）',
    next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）',
    re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）',
    operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动',
    operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动',
    operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动',
    operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_enable (enable),
    INDEX idx_next_open_at (next_open_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表（单表）';

-- ============================================
-- 步骤5：数据迁移（商户配置表）
-- ============================================
-- 注意：需要将分片表中的数据迁移到基础库单表
-- 示例（需要根据实际情况调整）：
-- INSERT INTO jiaoyi.merchant_stripe_config 
-- SELECT * FROM jiaoyi_order_0.merchant_stripe_config_0
-- UNION ALL
-- SELECT * FROM jiaoyi_order_0.merchant_stripe_config_1
-- UNION ALL
-- SELECT * FROM jiaoyi_order_0.merchant_stripe_config_2
-- ... (需要为所有数据库和分片表执行)

-- ============================================
-- 步骤6：验证和清理
-- ============================================
-- 1. 验证所有表都有 shard_id 字段
-- 2. 验证所有表都有32张表/库
-- 3. 验证索引已创建（idx_shard_id, idx_claim, idx_cleanup等）
-- 4. 清理旧的分片表（如果数据已迁移）

