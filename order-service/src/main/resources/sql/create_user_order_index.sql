-- ============================================
-- 用户订单索引表（解决按 userId 查询订单的广播查询问题）
-- ============================================
--
-- 问题：订单表按 store_id 分片，按 userId 查询会触发广播查询（96 张表）
-- 解决方案：创建索引表，按 user_id 分片，记录 userId → orderId + storeId 的映射
--
-- 分片策略：
--   - 数据库：jiaoyi_order_0, jiaoyi_order_1, jiaoyi_order_2（3 个库）
--   - 分片键：user_id
--   - 分片数：3 库 × 32 表 = 96 张表
--   - 分片算法：hash(user_id) % 3 = 库号，hash(user_id) % 32 = 表号
--
-- 查询流程：
--   1. 从索引表查询用户的订单ID列表（按 user_id 分片，精准路由）
--   2. 按 store_id 分组，批量查询订单详情（精准路由）
--
-- ============================================

-- 每个库需要创建 32 张分表（user_order_index_0 ~ user_order_index_31）
-- 在 jiaoyi_order_0, jiaoyi_order_1, jiaoyi_order_2 三个数据库中分别执行

-- user_order_index_0
CREATE TABLE IF NOT EXISTS `user_order_index_0` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（分片键）',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `store_id` BIGINT NOT NULL COMMENT '商户ID（用于精准查询订单详情）',
    `merchant_id` VARCHAR(50) NOT NULL COMMENT '商户ID（字符串形式）',
    `order_status` INT DEFAULT NULL COMMENT '订单状态（冗余字段，用于快速过滤）',
    `order_type` VARCHAR(20) DEFAULT NULL COMMENT '订单类型（DELIVERY/PICKUP/DINE_IN）',
    `total_amount` DECIMAL(10, 2) DEFAULT NULL COMMENT '订单总金额（冗余字段，用于展示）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_userid_orderid` (`user_id`, `order_id`),
    KEY `idx_userid_created` (`user_id`, `created_at` DESC),
    KEY `idx_userid_status` (`user_id`, `order_status`),
    KEY `idx_orderid` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订单索引表_0（按 user_id 分片）';

-- user_order_index_1 ~ user_order_index_31（使用 LIKE 复制表结构）
CREATE TABLE IF NOT EXISTS `user_order_index_1` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_1` COMMENT='用户订单索引表_1（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_2` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_2` COMMENT='用户订单索引表_2（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_3` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_3` COMMENT='用户订单索引表_3（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_4` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_4` COMMENT='用户订单索引表_4（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_5` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_5` COMMENT='用户订单索引表_5（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_6` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_6` COMMENT='用户订单索引表_6（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_7` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_7` COMMENT='用户订单索引表_7（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_8` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_8` COMMENT='用户订单索引表_8（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_9` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_9` COMMENT='用户订单索引表_9（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_10` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_10` COMMENT='用户订单索引表_10（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_11` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_11` COMMENT='用户订单索引表_11（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_12` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_12` COMMENT='用户订单索引表_12（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_13` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_13` COMMENT='用户订单索引表_13（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_14` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_14` COMMENT='用户订单索引表_14（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_15` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_15` COMMENT='用户订单索引表_15（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_16` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_16` COMMENT='用户订单索引表_16（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_17` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_17` COMMENT='用户订单索引表_17（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_18` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_18` COMMENT='用户订单索引表_18（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_19` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_19` COMMENT='用户订单索引表_19（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_20` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_20` COMMENT='用户订单索引表_20（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_21` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_21` COMMENT='用户订单索引表_21（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_22` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_22` COMMENT='用户订单索引表_22（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_23` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_23` COMMENT='用户订单索引表_23（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_24` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_24` COMMENT='用户订单索引表_24（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_25` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_25` COMMENT='用户订单索引表_25（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_26` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_26` COMMENT='用户订单索引表_26（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_27` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_27` COMMENT='用户订单索引表_27（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_28` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_28` COMMENT='用户订单索引表_28（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_29` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_29` COMMENT='用户订单索引表_29（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_30` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_30` COMMENT='用户订单索引表_30（按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `user_order_index_31` LIKE `user_order_index_0`;
ALTER TABLE `user_order_index_31` COMMENT='用户订单索引表_31（按 user_id 分片）';

-- ============================================
-- 使用示例（ShardingSphere 会自动路由到对应的分片）
-- ============================================

-- 1. 创建订单时，写入索引表
-- INSERT INTO user_order_index (user_id, order_id, store_id, merchant_id, order_status, order_type, total_amount)
-- VALUES (123, 456789, 100, 'M001', 1, 'DELIVERY', 50.00);
-- ShardingSphere 会根据 user_id=123 计算：
-- - 库号：hash(123) % 3 = X → jiaoyi_order_X
-- - 表号：hash(123) % 32 = Y → user_order_index_Y

-- 2. 查询用户的所有订单（按创建时间倒序）
-- SELECT order_id, store_id, merchant_id, order_status, total_amount, created_at
-- FROM user_order_index
-- WHERE user_id = 123
-- ORDER BY created_at DESC
-- LIMIT 20;
-- ShardingSphere 会精准路由到 1 张表：jiaoyi_order_X.user_order_index_Y

-- 3. 查询用户的指定状态订单
-- SELECT order_id, store_id
-- FROM user_order_index
-- WHERE user_id = 123 AND order_status = 1
-- ORDER BY created_at DESC;

-- ============================================
-- 性能对比
-- ============================================
--
-- 【修改前】按 userId 查询订单
-- SELECT * FROM orders WHERE user_id = 123;
-- → ShardingSphere 广播查询 96 张表（3 库 × 32 表）
-- → 查询时间：~500ms（高峰期）
--
-- 【修改后】先查索引表，再查订单
-- 1. SELECT order_id, store_id FROM user_order_index WHERE user_id = 123;
--    → 精准路由到 1 张表（jiaoyi_order_X.user_order_index_Y）
--    → 查询时间：~10ms
--
-- 2. SELECT * FROM orders WHERE store_id = 100 AND id IN (456789, 456790, ...);
--    → 精准路由到 1 张表（jiaoyi_order_X.orders_Y）
--    → 查询时间：~15ms
--
-- 总时间：~25ms（性能提升 95%）
--
-- ============================================
-- 执行步骤
-- ============================================
--
-- 在 3 个数据库中分别执行本脚本：
--
-- mysql -u root -p jiaoyi_order_0 < create_user_order_index.sql
-- mysql -u root -p jiaoyi_order_1 < create_user_order_index.sql
-- mysql -u root -p jiaoyi_order_2 < create_user_order_index.sql
--
-- 每个库会创建 32 张表：user_order_index_0 ~ user_order_index_31
-- 总计：3 × 32 = 96 张表
--
-- ============================================
