-- ============================================
-- 餐馆（merchants）测试数据插入脚本
-- merchants 表按 merchant_id 分片（3库 × 3表 = 9个分片）
-- ============================================

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;

-- 插入餐馆数据到 merchants_0
INSERT INTO merchants_0 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_001', 'group_001', 'encrypt_001', '川味小厨', 'Asia/Shanghai', 'https://example.com/logo1.jpg', 'cwxc',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":30,"max":60}', '{"min":45,"max":90}',
 '[{"day":"MONDAY","open":"09:00","close":"22:00"}]', '[{"day":"MONDAY","open":"09:00","close":"22:00"}]',
 'FLAT_RATE', 5.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"red","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_004', 'group_001', 'encrypt_004', '湘味餐厅', 'Asia/Shanghai', 'https://example.com/logo4.jpg', 'xwct',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":25,"max":50}', '{"min":40,"max":75}',
 '[{"day":"MONDAY","open":"10:00","close":"21:00"}]', '[{"day":"MONDAY","open":"10:00","close":"21:00"}]',
 'FLAT_RATE', 6.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"orange","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_007', 'group_002', 'encrypt_007', '粤式茶餐厅', 'Asia/Shanghai', 'https://example.com/logo7.jpg', 'ysct',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":20,"max":40}', '{"min":35,"max":65}',
 '[{"day":"MONDAY","open":"08:00","close":"23:00"}]', '[{"day":"MONDAY","open":"08:00","close":"23:00"}]',
 'FLAT_RATE', 4.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"green","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_1
INSERT INTO merchants_1 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_010', 'group_002', 'encrypt_010', '东北饺子馆', 'Asia/Shanghai', 'https://example.com/logo10.jpg', 'dbjzg',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":15,"max":30}', '{"min":30,"max":60}',
 '[{"day":"MONDAY","open":"07:00","close":"22:00"}]', '[{"day":"MONDAY","open":"07:00","close":"22:00"}]',
 'FLAT_RATE', 3.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"blue","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_2
INSERT INTO merchants_2 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_013', 'group_003', 'encrypt_013', '西式快餐', 'Asia/Shanghai', 'https://example.com/logo13.jpg', 'xskc',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":10,"max":20}', '{"min":25,"max":45}',
 '[{"day":"MONDAY","open":"09:00","close":"22:00"}]', '[{"day":"MONDAY","open":"09:00","close":"22:00"}]',
 'FLAT_RATE', 5.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"yellow","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;

-- 插入餐馆数据到 merchants_0
INSERT INTO merchants_0 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_002', 'group_001', 'encrypt_002', '鲁菜馆', 'Asia/Shanghai', 'https://example.com/logo2.jpg', 'lcg',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":35,"max":70}', '{"min":50,"max":100}',
 '[{"day":"MONDAY","open":"11:00","close":"21:00"}]', '[{"day":"MONDAY","open":"11:00","close":"21:00"}]',
 'FLAT_RATE', 7.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"brown","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_005', 'group_001', 'encrypt_005', '苏菜餐厅', 'Asia/Shanghai', 'https://example.com/logo5.jpg', 'sct',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":30,"max":60}', '{"min":45,"max":90}',
 '[{"day":"MONDAY","open":"10:30","close":"21:30"}]', '[{"day":"MONDAY","open":"10:30","close":"21:30"}]',
 'FLAT_RATE', 5.50, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"purple","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_008', 'group_002', 'encrypt_008', '日式料理', 'Asia/Shanghai', 'https://example.com/logo8.jpg', 'rsll',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":25,"max":50}', '{"min":40,"max":80}',
 '[{"day":"MONDAY","open":"11:30","close":"22:30"}]', '[{"day":"MONDAY","open":"11:30","close":"22:30"}]',
 'FLAT_RATE', 8.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"pink","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_1
INSERT INTO merchants_1 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_011', 'group_002', 'encrypt_011', '韩式烤肉', 'Asia/Shanghai', 'https://example.com/logo11.jpg', 'hskr',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":20,"max":40}', '{"min":35,"max":70}',
 '[{"day":"MONDAY","open":"11:00","close":"23:00"}]', '[{"day":"MONDAY","open":"11:00","close":"23:00"}]',
 'FLAT_RATE', 6.50, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"red","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_2
INSERT INTO merchants_2 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_014', 'group_003', 'encrypt_014', '泰式餐厅', 'Asia/Shanghai', 'https://example.com/logo14.jpg', 'tsct',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":30,"max":60}', '{"min":45,"max":90}',
 '[{"day":"MONDAY","open":"12:00","close":"22:00"}]', '[{"day":"MONDAY","open":"12:00","close":"22:00"}]',
 'FLAT_RATE', 7.50, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"orange","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;

-- 插入餐馆数据到 merchants_0
INSERT INTO merchants_0 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_003', 'group_001', 'encrypt_003', '徽菜馆', 'Asia/Shanghai', 'https://example.com/logo3.jpg', 'hcg',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":40,"max":80}', '{"min":55,"max":110}',
 '[{"day":"MONDAY","open":"10:00","close":"22:00"}]', '[{"day":"MONDAY","open":"10:00","close":"22:00"}]',
 'FLAT_RATE', 6.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"green","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_006', 'group_001', 'encrypt_006', '浙菜餐厅', 'Asia/Shanghai', 'https://example.com/logo6.jpg', 'zct',
 1, 1, '["CASH","CARD","ONLINE"]', '["CASH","CARD","ONLINE"]',
 '{"min":25,"max":50}', '{"min":40,"max":80}',
 '[{"day":"MONDAY","open":"10:00","close":"21:00"}]', '[{"day":"MONDAY","open":"10:00","close":"21:00"}]',
 'FLAT_RATE', 5.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"blue","font":"Arial"}',
 1, NOW(), NOW()),
('merchant_009', 'group_002', 'encrypt_009', '意式餐厅', 'Asia/Shanghai', 'https://example.com/logo9.jpg', 'ysct',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":30,"max":60}', '{"min":45,"max":90}',
 '[{"day":"MONDAY","open":"11:00","close":"22:00"}]', '[{"day":"MONDAY","open":"11:00","close":"22:00"}]',
 'FLAT_RATE', 8.50, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"red","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_1
INSERT INTO merchants_1 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_012', 'group_002', 'encrypt_012', '法式餐厅', 'Asia/Shanghai', 'https://example.com/logo12.jpg', 'fsct',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":45,"max":90}', '{"min":60,"max":120}',
 '[{"day":"MONDAY","open":"12:00","close":"23:00"}]', '[{"day":"MONDAY","open":"12:00","close":"23:00"}]',
 'FLAT_RATE', 10.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"purple","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入餐馆数据到 merchants_2
INSERT INTO merchants_2 (
    merchant_id, merchant_group_id, encrypt_merchant_id, name, time_zone, logo, short_url,
    is_pickup, is_delivery, pickup_payment_acceptance, delivery_payment_acceptance,
    pickup_prepare_time, delivery_prepare_time, pickup_open_time, delivery_open_time,
    default_delivery_fee, delivery_flat_fee, delivery_variable_rate, delivery_zone_rate,
    capability_of_order, personalization, version, create_time, update_time
) VALUES
('merchant_015', 'group_003', 'encrypt_015', '印度餐厅', 'Asia/Shanghai', 'https://example.com/logo15.jpg', 'ydct',
 1, 1, '["CARD","ONLINE"]', '["CARD","ONLINE"]',
 '{"min":35,"max":70}', '{"min":50,"max":100}',
 '[{"day":"MONDAY","open":"11:30","close":"22:30"}]', '[{"day":"MONDAY","open":"11:30","close":"22:30"}]',
 'FLAT_RATE', 7.00, NULL, NULL,
 '{"pickup":true,"delivery":true,"dineIn":true}', '{"theme":"yellow","font":"Arial"}',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

