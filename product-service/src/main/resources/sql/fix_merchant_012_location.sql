-- ============================================
-- 修复 merchant_012 的分片位置
-- 问题：数据在 jiaoyi_2.merchants_1，但分片算法计算应该在 jiaoyi_0.merchants_0
-- 解决方案：将数据从错误位置移动到正确位置
-- ============================================

-- 1. 备份数据（先查询确认数据存在）
USE jiaoyi_2;
SELECT * FROM merchants_1 WHERE merchant_id = 'merchant_012';

-- 2. 插入到正确位置（先插入，避免数据丢失）
USE jiaoyi_0;
INSERT INTO merchants_0 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, display, version, create_time, update_time
) 
SELECT 
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, display, version, create_time, NOW()
FROM jiaoyi_2.merchants_1
WHERE merchant_id = 'merchant_012'
ON DUPLICATE KEY UPDATE 
    name=VALUES(name),
    merchant_group_id=VALUES(merchant_group_id),
    encrypt_merchant_id=VALUES(encrypt_merchant_id),
    time_zone=VALUES(time_zone),
    logo=VALUES(logo),
    short_url=VALUES(short_url),
    is_pickup=VALUES(is_pickup),
    is_delivery=VALUES(is_delivery),
    pickup_payment_acceptance=VALUES(pickup_payment_acceptance),
    delivery_payment_acceptance=VALUES(delivery_payment_acceptance),
    pickup_prepare_time=VALUES(pickup_prepare_time),
    delivery_prepare_time=VALUES(delivery_prepare_time),
    pickup_open_time=VALUES(pickup_open_time),
    delivery_open_time=VALUES(delivery_open_time),
    default_delivery_fee=VALUES(default_delivery_fee),
    delivery_flat_fee=VALUES(delivery_flat_fee),
    delivery_variable_rate=VALUES(delivery_variable_rate),
    delivery_zone_rate=VALUES(delivery_zone_rate),
    capability_of_order=VALUES(capability_of_order),
    personalization=VALUES(personalization),
    display=VALUES(display),
    update_time=NOW();

-- 3. 验证数据已正确插入到新位置
SELECT '验证：merchant_012 在新位置' AS status;
SELECT * FROM jiaoyi_0.merchants_0 WHERE merchant_id = 'merchant_012';

-- 4. 删除旧位置的数据（确认新位置数据正确后再执行）
-- USE jiaoyi_2;
-- DELETE FROM merchants_1 WHERE merchant_id = 'merchant_012';
