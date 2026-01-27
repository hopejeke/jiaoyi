-- ============================================
-- 扩容迁移脚本：3 库 → 6 库
-- ============================================
-- 说明：
-- 1. 此脚本用于将 bucket_id 513-683 从 ds1 迁移到 ds3
-- 2. 迁移策略：按 bucket 范围迁移，确保数据完整性
-- 3. 迁移后验证：对比新旧库的数据量，确保迁移完整
-- ============================================

-- ============================================
-- 步骤 1：准备新库（已创建 jiaoyi_3, jiaoyi_4, jiaoyi_5）
-- ============================================

-- ============================================
-- 步骤 2：数据迁移（按 bucket 范围迁移）
-- ============================================

-- 示例：迁移 bucket_id 513-683 从 ds1 到 ds3
-- 注意：实际迁移时需要使用数据迁移工具（如 mysqldump + 脚本，或自研迁移工具）

-- 2.1 导出需要迁移的数据（从旧库）
-- 使用 mysqldump 或 SELECT INTO OUTFILE
-- 示例 SQL（实际应使用工具导出）：
SELECT 
    id, merchant_id, shard_id, user_id, order_type, status, 
    local_status, kitchen_status, order_price, customer_info, 
    delivery_address, notes, pos_order_id, payment_method, 
    payment_status, stripe_payment_intent_id, refund_amount, 
    refund_reason, version, create_time, update_time, delivery_id
FROM orders 
WHERE shard_id BETWEEN 513 AND 683
ORDER BY shard_id, id;

-- 2.2 计算目标表索引并插入到新库
-- 注意：shard_id % 32 决定表索引
-- 例如：shard_id=513 → table_idx=1 → orders_01
-- 例如：shard_id=514 → table_idx=2 → orders_02

-- 迁移脚本伪代码（实际应使用 Java/Python 脚本）：
-- FOR EACH shard_id IN [513, 683]:
--     table_idx = shard_id % 32
--     table_name = "orders_" + format("%02d", table_idx)
--     INSERT INTO ds3.{table_name} SELECT * FROM ds1.{table_name} WHERE shard_id = {shard_id}

-- ============================================
-- 步骤 3：验证数据完整性
-- ============================================

-- 3.1 验证 orders 表数据量
SELECT 
    'ds1 (旧库)' as source,
    COUNT(*) as total_count,
    MIN(shard_id) as min_shard_id,
    MAX(shard_id) as max_shard_id
FROM ds1.orders 
WHERE shard_id BETWEEN 513 AND 683

UNION ALL

SELECT 
    'ds3 (新库)' as source,
    COUNT(*) as total_count,
    MIN(shard_id) as min_shard_id,
    MAX(shard_id) as max_shard_id
FROM ds3.orders 
WHERE shard_id BETWEEN 513 AND 683;

-- 3.2 验证 order_outbox 表数据量
SELECT 
    'ds1 (旧库)' as source,
    COUNT(*) as total_count,
    MIN(shard_id) as min_shard_id,
    MAX(shard_id) as max_shard_id
FROM ds1.order_outbox 
WHERE shard_id BETWEEN 513 AND 683

UNION ALL

SELECT 
    'ds3 (新库)' as source,
    COUNT(*) as total_count,
    MIN(shard_id) as min_shard_id,
    MAX(shard_id) as max_shard_id
FROM ds3.order_outbox 
WHERE shard_id BETWEEN 513 AND 683;

-- 3.3 验证关键字段一致性（示例：验证订单金额总和）
SELECT 
    'ds1 (旧库)' as source,
    SUM(CAST(JSON_EXTRACT(order_price, '$.total') AS DECIMAL(10,2))) as total_amount
FROM ds1.orders 
WHERE shard_id BETWEEN 513 AND 683

UNION ALL

SELECT 
    'ds3 (新库)' as source,
    SUM(CAST(JSON_EXTRACT(order_price, '$.total') AS DECIMAL(10,2))) as total_amount
FROM ds3.orders 
WHERE shard_id BETWEEN 513 AND 683;

-- ============================================
-- 步骤 4：更新路由表（切路由）
-- ============================================

-- ⚠️ 重要：只有在数据迁移完成并验证后，才能更新路由表
-- 否则会导致"路由已切，数据未迁"的事故

-- 4.1 更新被迁移的 bucket 的路由
UPDATE shard_bucket_route 
SET ds_name = 'ds3',
    status = 'NORMAL',
    updated_at = NOW()
WHERE bucket_id BETWEEN 513 AND 683;

-- 4.2 验证路由更新
SELECT bucket_id, ds_name, status, updated_at
FROM shard_bucket_route
WHERE bucket_id BETWEEN 513 AND 683
ORDER BY bucket_id
LIMIT 10;

-- ============================================
-- 步骤 5：刷新路由缓存（通过 API）
-- ============================================

-- 使用 curl 或 Postman 调用刷新接口
-- curl -X POST http://instance1:8080/api/admin/route-cache/refresh
-- curl -X POST http://instance2:8080/api/admin/route-cache/refresh
-- curl -X POST http://instance3:8080/api/admin/route-cache/refresh

-- ============================================
-- 步骤 6：验证路由正确性
-- ============================================

-- 通过 API 验证路由
-- curl http://instance1:8080/api/admin/route-cache/route/513
-- 应该返回：{"shardId":513,"dsName":"ds3","status":"NORMAL"}

-- ============================================
-- 注意事项
-- ============================================
-- 1. 迁移前必须备份数据
-- 2. 迁移过程中监控数据库性能
-- 3. 迁移后必须验证数据完整性
-- 4. 路由表更新后必须立即刷新缓存
-- 5. 建议在低峰期执行迁移
-- 6. 如果数据量大，可以按更小的 bucket 范围分批迁移



