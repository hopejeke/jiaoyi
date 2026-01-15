-- 为 merchant_fee_config 表添加配送时段配置字段
-- 需要在所有分片表上执行

-- 添加 delivery_time_slots 字段
ALTER TABLE merchant_fee_config_0 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';
ALTER TABLE merchant_fee_config_1 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';
ALTER TABLE merchant_fee_config_2 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';

-- 配送时段配置示例：
-- 1. 每日统一时段：
--    {"daily": {"start": "09:00", "end": "22:00"}}
--
-- 2. 按星期分别配置：
--    {
--      "monday": {"start": "09:00", "end": "22:00"},
--      "tuesday": {"start": "09:00", "end": "22:00"},
--      "wednesday": {"start": "09:00", "end": "22:00"},
--      "thursday": {"start": "09:00", "end": "22:00"},
--      "friday": {"start": "09:00", "end": "23:00"},
--      "saturday": {"start": "10:00", "end": "23:00"},
--      "sunday": {"start": "10:00", "end": "22:00"}
--    }
--
-- 3. 如果字段为空或 NULL，表示全天可配送














