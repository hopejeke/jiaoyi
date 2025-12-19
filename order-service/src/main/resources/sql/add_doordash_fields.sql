-- 为 orders 表添加 DoorDash 相关字段
-- 需要在所有分片表上执行

-- 添加 delivery_id 字段
ALTER TABLE orders_0 ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID';
ALTER TABLE orders_1 ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID';
ALTER TABLE orders_2 ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID';

-- 添加 delivery_fee_quoted 字段
ALTER TABLE orders_0 ADD COLUMN delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用';
ALTER TABLE orders_1 ADD COLUMN delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用';
ALTER TABLE orders_2 ADD COLUMN delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用';

-- 添加 delivery_fee_charged_to_user 字段
ALTER TABLE orders_0 ADD COLUMN delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费';
ALTER TABLE orders_1 ADD COLUMN delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费';
ALTER TABLE orders_2 ADD COLUMN delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费';

-- 添加 delivery_fee_billed 字段
ALTER TABLE orders_0 ADD COLUMN delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用';
ALTER TABLE orders_1 ADD COLUMN delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用';
ALTER TABLE orders_2 ADD COLUMN delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用';

-- 添加 delivery_fee_variance 字段
ALTER TABLE orders_0 ADD COLUMN delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）';
ALTER TABLE orders_1 ADD COLUMN delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）';
ALTER TABLE orders_2 ADD COLUMN delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）';

-- 添加 additional_data 字段
ALTER TABLE orders_0 ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）';
ALTER TABLE orders_1 ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）';
ALTER TABLE orders_2 ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）';

-- 添加配送跟踪相关字段
ALTER TABLE orders_0 ADD COLUMN delivery_tracking_url VARCHAR(500) COMMENT 'DoorDash配送跟踪URL';
ALTER TABLE orders_1 ADD COLUMN delivery_tracking_url VARCHAR(500) COMMENT 'DoorDash配送跟踪URL';
ALTER TABLE orders_2 ADD COLUMN delivery_tracking_url VARCHAR(500) COMMENT 'DoorDash配送跟踪URL';

ALTER TABLE orders_0 ADD COLUMN delivery_distance_miles DECIMAL(10,2) COMMENT '配送距离（英里）';
ALTER TABLE orders_1 ADD COLUMN delivery_distance_miles DECIMAL(10,2) COMMENT '配送距离（英里）';
ALTER TABLE orders_2 ADD COLUMN delivery_distance_miles DECIMAL(10,2) COMMENT '配送距离（英里）';

ALTER TABLE orders_0 ADD COLUMN delivery_eta_minutes INT COMMENT '预计送达时间（分钟）';
ALTER TABLE orders_1 ADD COLUMN delivery_eta_minutes INT COMMENT '预计送达时间（分钟）';
ALTER TABLE orders_2 ADD COLUMN delivery_eta_minutes INT COMMENT '预计送达时间（分钟）';

ALTER TABLE orders_0 ADD COLUMN delivery_status VARCHAR(50) COMMENT 'DoorDash配送状态：CREATED/ASSIGNED/PICKED_UP/DELIVERED/CANCELLED';
ALTER TABLE orders_1 ADD COLUMN delivery_status VARCHAR(50) COMMENT 'DoorDash配送状态：CREATED/ASSIGNED/PICKED_UP/DELIVERED/CANCELLED';
ALTER TABLE orders_2 ADD COLUMN delivery_status VARCHAR(50) COMMENT 'DoorDash配送状态：CREATED/ASSIGNED/PICKED_UP/DELIVERED/CANCELLED';

ALTER TABLE orders_0 ADD COLUMN delivery_driver_name VARCHAR(100) COMMENT '骑手姓名';
ALTER TABLE orders_1 ADD COLUMN delivery_driver_name VARCHAR(100) COMMENT '骑手姓名';
ALTER TABLE orders_2 ADD COLUMN delivery_driver_name VARCHAR(100) COMMENT '骑手姓名';

ALTER TABLE orders_0 ADD COLUMN delivery_driver_phone VARCHAR(50) COMMENT '骑手电话';
ALTER TABLE orders_1 ADD COLUMN delivery_driver_phone VARCHAR(50) COMMENT '骑手电话';
ALTER TABLE orders_2 ADD COLUMN delivery_driver_phone VARCHAR(50) COMMENT '骑手电话';

-- 添加索引
ALTER TABLE orders_0 ADD INDEX idx_delivery_id (delivery_id);
ALTER TABLE orders_1 ADD INDEX idx_delivery_id (delivery_id);
ALTER TABLE orders_2 ADD INDEX idx_delivery_id (delivery_id);


