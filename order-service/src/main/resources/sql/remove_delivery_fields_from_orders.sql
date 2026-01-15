-- 从 orders 表移除 delivery 相关字段（保留 delivery_id 作为外键）
-- 这些字段已经迁移到独立的 deliveries 表中
-- 需要在所有分片表上执行

-- 移除 delivery_fee_quoted 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_fee_quoted;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_fee_quoted;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_fee_quoted;

-- 移除 delivery_fee_charged_to_user 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_fee_charged_to_user;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_fee_charged_to_user;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_fee_charged_to_user;

-- 移除 delivery_fee_billed 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_fee_billed;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_fee_billed;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_fee_billed;

-- 移除 delivery_fee_variance 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_fee_variance;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_fee_variance;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_fee_variance;

-- 移除 additional_data 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS additional_data;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS additional_data;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS additional_data;

-- 移除 delivery_tracking_url 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_tracking_url;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_tracking_url;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_tracking_url;

-- 移除 delivery_distance_miles 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_distance_miles;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_distance_miles;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_distance_miles;

-- 移除 delivery_eta_minutes 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_eta_minutes;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_eta_minutes;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_eta_minutes;

-- 移除 delivery_status 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_status;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_status;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_status;

-- 移除 delivery_driver_name 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_driver_name;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_driver_name;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_driver_name;

-- 移除 delivery_driver_phone 字段
ALTER TABLE orders_0 DROP COLUMN IF EXISTS delivery_driver_phone;
ALTER TABLE orders_1 DROP COLUMN IF EXISTS delivery_driver_phone;
ALTER TABLE orders_2 DROP COLUMN IF EXISTS delivery_driver_phone;

-- 注意：delivery_id 字段保留，作为外键关联 deliveries 表








