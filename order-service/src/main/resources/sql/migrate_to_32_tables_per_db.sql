-- ============================================
-- 订单域：迁移到32张表/库（固定虚拟桶1024 + 路由表映射）
-- ============================================
-- 说明：
-- 1. 所有订单事实表改为32张表/库（orders_00..orders_31）
-- 2. 所有表必须包含 shard_id 字段（INT NOT NULL）
-- 3. shard_id 计算：hash(brandId) & 1023
-- 4. 分库路由：通过 shard_bucket_route 表查询
-- 5. 分表路由：shard_id % 32
-- ============================================

-- ============================================
-- 步骤1：为所有订单事实表添加 shard_id 字段（如果不存在）
-- ============================================

-- orders 表
USE jiaoyi_order_0;
ALTER TABLE orders_0 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_1 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_2 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;

USE jiaoyi_order_1;
ALTER TABLE orders_0 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_1 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_2 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;

USE jiaoyi_order_2;
ALTER TABLE orders_0 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_1 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;
ALTER TABLE orders_2 ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id;

-- 添加索引
USE jiaoyi_order_0;
ALTER TABLE orders_0 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_1 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_2 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);

USE jiaoyi_order_1;
ALTER TABLE orders_0 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_1 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_2 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);

USE jiaoyi_order_2;
ALTER TABLE orders_0 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_1 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);
ALTER TABLE orders_2 ADD INDEX IF NOT EXISTS idx_shard_id (shard_id);

-- ============================================
-- 步骤2：创建32张表/库（orders_00..orders_31）
-- ============================================
-- 注意：此脚本假设已有3张表（orders_0..orders_2），需要扩展为32张表
-- 实际执行时，需要先迁移数据，然后创建新表结构

-- 示例：为 jiaoyi_order_0 创建 orders_03..orders_31
USE jiaoyi_order_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_orders_tables_32()
BEGIN
    DECLARE i INT DEFAULT 3;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''订单表_'', i, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 执行存储过程（为每个数据库执行）
CALL create_orders_tables_32();
DROP PROCEDURE IF EXISTS create_orders_tables_32;

-- 注意：需要为 jiaoyi_order_1 和 jiaoyi_order_2 也执行相同的操作
-- 实际执行时，需要循环处理所有数据库

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
-- 步骤4：为其他订单事实表执行相同操作
-- ============================================
-- order_items, order_coupons, payments, refunds, refund_items, deliveries
-- 都需要：
-- 1. 添加 shard_id 字段
-- 2. 扩展为32张表/库
-- 3. 添加 shard_id 索引

-- ============================================
-- 步骤5：更新 outbox 表为32张表/库
-- ============================================
-- outbox_00..outbox_31，添加 claim 索引和 uk_event_id

-- ============================================
-- 步骤6：迁移商户配置表到基础库（单表）
-- ============================================
-- merchant_stripe_config, merchant_fee_config, merchant_capability_config
-- 迁移到 jiaoyi 基础库，改为单表

-- ============================================
-- 步骤7：doordash_retry_task 改为 shard_id 分片，32张表/库
-- ============================================



