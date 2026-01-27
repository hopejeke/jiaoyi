-- ============================================
-- 直接插入 merchant_012 到正确位置
-- 如果数据已存在，先删除再插入
-- ============================================

-- 1. 先删除所有位置的 merchant_012（避免重复）
DELETE FROM jiaoyi_0.merchants_0 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_0.merchants_1 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_0.merchants_2 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_1.merchants_0 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_1.merchants_1 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_1.merchants_2 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_2.merchants_0 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_2.merchants_1 WHERE merchant_id = 'merchant_012';
DELETE FROM jiaoyi_2.merchants_2 WHERE merchant_id = 'merchant_012';

-- 2. 从旧位置获取完整数据（如果还存在）
-- 如果旧位置没有数据，使用默认值插入
USE jiaoyi_0;

INSERT INTO merchants_0 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, display, activate, dl_activate,
    pickup_have_setted, delivery_have_setted, enable_note, enable_auto_send,
    enable_auto_receipt, enable_sdi_auto_receipt, enable_sdi_auto_send, enable_popular_item,
    version, create_time, update_time
) VALUES (
    'merchant_012', 'group_002', 'encrypt_012', '法式餐厅', 'Asia/Shanghai', 
    'https://example.com/logo12.jpg', 'fsct',
    1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
    '{"min":45,"max":90}', '{"min":60,"max":120}',
    '[{"day":"MONDAY","open":"12:00","close":"23:00"}]', '[{"day":"MONDAY","open":"12:00","close":"23:00"}]',
    'FLAT_RATE', 10.00, NULL, NULL,
    '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"purple","font":"Arial"}',
    1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    1, NOW(), NOW()
);

-- 3. 验证插入结果
SELECT 'merchant_012 已插入到 jiaoyi_0.merchants_0' AS status;
SELECT * FROM jiaoyi_0.merchants_0 WHERE merchant_id = 'merchant_012';
















