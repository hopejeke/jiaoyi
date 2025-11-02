-- 插入测试数据
USE jiaoyi;

-- 清空现有数据（按依赖关系顺序）
DELETE FROM coupon_usage;
DELETE FROM order_coupons;
DELETE FROM inventory_transactions;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM store_products;
DELETE FROM stores;
DELETE FROM inventory;
DELETE FROM coupons;
DELETE FROM products;

-- 重置自增ID（模拟TRUNCATE的效果）
ALTER TABLE coupon_usage AUTO_INCREMENT = 1;
ALTER TABLE order_coupons AUTO_INCREMENT = 1;
ALTER TABLE inventory_transactions AUTO_INCREMENT = 1;
ALTER TABLE order_items AUTO_INCREMENT = 1;
ALTER TABLE orders AUTO_INCREMENT = 1;
ALTER TABLE store_products AUTO_INCREMENT = 1;
ALTER TABLE stores AUTO_INCREMENT = 1;
ALTER TABLE inventory AUTO_INCREMENT = 1;
ALTER TABLE coupons AUTO_INCREMENT = 1;
ALTER TABLE products AUTO_INCREMENT = 1;

-- 插入优惠券测试数据（指定ID）
INSERT INTO coupons (id, coupon_code, coupon_name, type, value, min_order_amount, max_discount_amount, status, total_quantity, used_quantity, limit_per_user, applicable_type, applicable_products, start_time, end_time) VALUES
-- 固定金额优惠券
(1, 'NEWUSER100', '新用户专享券', 'FIXED', 100.00, 500.00, NULL, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
(2, 'SAVE200', '满减200元券', 'FIXED', 200.00, 1000.00, NULL, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
(3, 'VIP500', 'VIP专享券', 'FIXED', 500.00, 2000.00, NULL, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),

-- 百分比折扣优惠券
(4, 'DISCOUNT10', '9折优惠券', 'PERCENTAGE', 10.00, 300.00, 500.00, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
(5, 'DISCOUNT15', '85折优惠券', 'PERCENTAGE', 15.00, 800.00, 1000.00, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
(6, 'DISCOUNT20', '8折优惠券', 'PERCENTAGE', 20.00, 1500.00, 2000.00, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),

-- 指定商品优惠券
(7, 'IPHONE100', 'iPhone专享券', 'FIXED', 100.00, 0.00, NULL, 'ACTIVE', 100000, 0, 10000, 'PRODUCT', '[2001,2002,2003,2004]', '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
(8, 'MACBOOK200', 'MacBook专享券', 'FIXED', 200.00, 0.00, NULL, 'ACTIVE', 100000, 0, 10000, 'PRODUCT', '[2005,2006,2007]', '2025-01-01 00:00:00', '2025-12-31 23:59:59'),

-- 已过期优惠券（保留作为测试用例）
(9, 'EXPIRED50', '已过期券', 'FIXED', 50.00, 200.00, NULL, 'EXPIRED', 100, 0, 10000, 'ALL', NULL, '2024-12-01 00:00:00', '2024-12-31 23:59:59'),

-- 限量优惠券
(10, 'LIMITED1000', '限量1000元券', 'FIXED', 1000.00, 5000.00, NULL, 'ACTIVE', 100000, 0, 10000, 'ALL', NULL, '2025-01-01 00:00:00', '2025-12-31 23:59:59');

-- 插入库存测试数据（与商品表ID对应）
INSERT INTO inventory (product_id, product_name, current_stock, locked_stock, min_stock, max_stock) VALUES
-- 电子产品库存
(1, 'iPhone 15 Pro', 50, 0, 10, 200),
(2, 'MacBook Pro 14英寸', 30, 0, 5, 100),
(3, 'iPad Air', 80, 0, 15, 150),
(4, 'AirPods Pro', 120, 0, 20, 300),
(5, 'Apple Watch Series 9', 60, 0, 10, 150),

-- 服装鞋帽库存
(6, 'Nike Air Max 270', 200, 0, 30, 500),
(7, 'Adidas Ultraboost 22', 150, 0, 25, 400),
(8, 'Uniqlo 优衣库基础T恤', 500, 0, 50, 1000),
(9, 'Zara 休闲衬衫', 100, 0, 20, 300),
(10, 'H&M 牛仔裤', 80, 0, 15, 250),

-- 家居用品库存
(11, '小米空气净化器4', 40, 0, 8, 100),
(12, '戴森V15吸尘器', 25, 0, 5, 80),
(13, '宜家书桌', 60, 0, 10, 150),
(14, '无印良品收纳盒', 300, 0, 50, 800),
(15, '飞利浦台灯', 90, 0, 15, 200),

-- 美妆护肤库存
(16, '兰蔻小黑瓶精华', 70, 0, 15, 200),
(17, '雅诗兰黛小棕瓶', 85, 0, 20, 250),
(18, 'SK-II神仙水', 45, 0, 10, 150),
(19, '香奈儿口红', 120, 0, 25, 300),
(20, '迪奥香水', 65, 0, 15, 180),

-- 食品饮料库存
(21, '三只松鼠坚果礼盒', 200, 0, 40, 500),
(22, '百草味零食大礼包', 300, 0, 60, 800),
(23, '星巴克咖啡豆', 150, 0, 30, 400),
(24, '蒙牛特仑苏牛奶', 500, 0, 100, 1000),
(25, '农夫山泉矿泉水', 1000, 0, 200, 2000),

-- 限量商品库存（库存不足）
(26, '限量版球鞋', 5, 0, 2, 20),
(27, '高端护肤品', 8, 0, 3, 30),

-- 热门商品库存（缺货）
(28, '热门手机', 0, 0, 5, 100),
(29, '爆款口红', 0, 0, 10, 200),

-- 下架商品库存
(30, '旧款手机', 20, 0, 5, 50),
(31, '过季服装', 50, 0, 10, 100);

-- 插入订单测试数据
INSERT INTO orders (order_no, user_id, status, total_amount, total_discount_amount, actual_amount, receiver_name, receiver_phone, receiver_address, remark) VALUES
-- 待支付订单（使用优惠券）
('ORD202501150001', 1001, 'PENDING', 8999.00, 100.00, 8899.00, '张三', '13800138001', '北京市朝阳区建国门外大街1号国贸大厦A座1001室', '请尽快发货，急用'),
('ORD202501150002', 1002, 'PENDING', 12999.00, 1299.90, 11699.10, '李四', '13800138002', '上海市浦东新区陆家嘴环路1000号恒生银行大厦20楼', ''),
('ORD202501150003', 1003, 'PENDING', 1999.00, 100.00, 1899.00, '王五', '13800138003', '广州市天河区珠江新城花城大道85号高德置地广场A座', '请包装精美一些'),

-- 已支付订单（使用优惠券）
('ORD202501140001', 1004, 'PAID', 15999.00, 200.00, 15799.00, '赵六', '13800138004', '深圳市南山区科技园南区深南大道10000号腾讯大厦', '发票抬头：腾讯科技（深圳）有限公司'),
('ORD202501140002', 1005, 'PAID', 2499.00, 374.85, 2124.15, '孙七', '13800138005', '杭州市西湖区文三路259号昌地火炬大厦1号楼', ''),
('ORD202501140003', 1001, 'PAID', 899.00, 0.00, 899.00, '张三', '13800138001', '北京市朝阳区建国门外大街1号国贸大厦A座1001室', ''),

-- 已发货订单（使用优惠券）
('ORD202501130001', 1006, 'SHIPPED', 19999.00, 500.00, 19499.00, '周八', '13800138006', '成都市锦江区红星路三段1号国际金融中心', '请使用顺丰快递'),
('ORD202501130002', 1007, 'SHIPPED', 3999.00, 200.00, 3799.00, '吴九', '13800138007', '武汉市江汉区中山大道818号佳丽广场', ''),
('ORD202501130003', 1002, 'SHIPPED', 1799.00, 0.00, 1799.00, '李四', '13800138002', '上海市浦东新区陆家嘴环路1000号恒生银行大厦20楼', ''),

-- 已送达订单（使用优惠券）
('ORD202501120001', 1008, 'DELIVERED', 12999.00, 2000.00, 10999.00, '郑十', '13800138008', '南京市鼓楼区中山路321号鼓楼国际大厦', ''),
('ORD202501120002', 1009, 'DELIVERED', 2999.00, 0.00, 2999.00, '冯十一', '13800138009', '西安市雁塔区高新路52号高科大厦', ''),

-- 已取消订单（使用优惠券）
('ORD202501110001', 1010, 'CANCELLED', 8999.00, 100.00, 8899.00, '陈十二', '13800138010', '重庆市渝中区解放碑步行街88号大都会东方广场', '用户主动取消'),
-- 多优惠券订单（新用户券 + 9折券）
('ORD202501160001', 1011, 'PENDING', 5000.00, 600.00, 4400.00, '刘十三', '13800138011', '天津市和平区南京路219号天津中心', '多优惠券测试订单');

-- 插入订单优惠券关联测试数据
INSERT INTO order_coupons (order_id, coupon_id, coupon_code, applied_amount) VALUES
-- 订单1：使用新用户专享券
(1, 1, 'NEWUSER100', 100.00),
-- 订单2：使用9折优惠券
(2, 4, 'DISCOUNT10', 1299.90),
-- 订单3：使用iPhone专享券
(3, 7, 'IPHONE100', 100.00),
-- 订单4：使用满减200元券
(4, 2, 'SAVE200', 200.00),
-- 订单5：使用85折优惠券
(5, 5, 'DISCOUNT15', 374.85),
-- 订单7：使用VIP专享券
(7, 3, 'VIP500', 500.00),
-- 订单8：使用MacBook专享券
(8, 8, 'MACBOOK200', 200.00),
-- 订单9：使用8折优惠券
(9, 6, 'DISCOUNT20', 2000.00),
-- 订单10：使用新用户专享券（已取消）
(10, 1, 'NEWUSER100', 100.00),
-- 订单11：使用多个优惠券（新用户券 + 9折券）
(11, 1, 'NEWUSER100', 100.00),
(11, 4, 'DISCOUNT10', 500.00);

-- 插入订单项测试数据
INSERT INTO order_items (order_id, product_id, product_name, product_image, unit_price, quantity, subtotal) VALUES
-- 订单1：iPhone 15 Pro + AirPods Pro
(1, 1, 'iPhone 15 Pro', 'https://example.com/iphone15pro.jpg', 8999.00, 1, 8999.00),

-- 订单2：MacBook Pro + Apple Pencil
(2, 2, 'MacBook Pro 14英寸', 'https://example.com/macbookpro14.jpg', 12999.00, 1, 12999.00),

-- 订单3：iPad Air + Apple Pencil
(3, 3, 'iPad Air', 'https://example.com/ipadair.jpg', 1999.00, 1, 1999.00),

-- 订单4：MacBook Pro 16英寸 + Studio Display
(4, 2, 'MacBook Pro 14英寸', 'https://example.com/macbookpro14.jpg', 15999.00, 1, 15999.00),

-- 订单5：iPad Pro + Magic Keyboard
(5, 3, 'iPad Air', 'https://example.com/ipadair.jpg', 2499.00, 1, 2499.00),

-- 订单6：AirPods Pro
(6, 4, 'AirPods Pro', 'https://example.com/airpodspro.jpg', 899.00, 1, 899.00),

-- 订单7：MacBook Pro + iPad Pro + Apple Watch
(7, 2, 'MacBook Pro 14英寸', 'https://example.com/macbookpro14.jpg', 12999.00, 1, 12999.00),
(7, 3, 'iPad Air', 'https://example.com/ipadair.jpg', 2499.00, 1, 2499.00),
(7, 5, 'Apple Watch Series 9', 'https://example.com/applewatch9.jpg', 4501.00, 1, 4501.00),

-- 订单8：iPhone 15 + AirPods
(8, 1, 'iPhone 15 Pro', 'https://example.com/iphone15pro.jpg', 3999.00, 1, 3999.00),

-- 订单9：Apple Watch Ultra
(9, 5, 'Apple Watch Series 9', 'https://example.com/applewatch9.jpg', 1799.00, 1, 1799.00),

-- 订单10：MacBook Air + AirPods Max
(10, 2, 'MacBook Pro 14英寸', 'https://example.com/macbookpro14.jpg', 12999.00, 1, 12999.00),

-- 订单11：iPad mini + Apple Pencil
(11, 3, 'iPad Air', 'https://example.com/ipadair.jpg', 2999.00, 1, 2999.00),

-- 订单12：iPhone 15 Pro（已取消）
(12, 1, 'iPhone 15 Pro', 'https://example.com/iphone15pro.jpg', 8999.00, 1, 8999.00);

-- 插入库存变动记录测试数据
INSERT INTO inventory_transactions (product_id, order_id, transaction_type, quantity, before_stock, after_stock, before_locked, after_locked, remark) VALUES
-- 初始入库记录
(1, NULL, 'IN', 200, 0, 200, 0, 0, '初始入库'),
(2, NULL, 'IN', 100, 0, 100, 0, 0, '初始入库'),
(3, NULL, 'IN', 250, 0, 250, 0, 0, '初始入库'),
(4, NULL, 'IN', 150, 0, 150, 0, 0, '初始入库'),
(5, NULL, 'IN', 80, 0, 80, 0, 0, '初始入库'),
(6, NULL, 'IN', 120, 0, 120, 0, 0, '初始入库'),
(7, NULL, 'IN', 40, 0, 40, 0, 0, '初始入库'),
(8, NULL, 'IN', 100, 0, 100, 0, 0, '初始入库'),
(9, NULL, 'IN', 180, 0, 180, 0, 0, '初始入库'),
(10, NULL, 'IN', 220, 0, 220, 0, 0, '初始入库'),
(11, NULL, 'IN', 140, 0, 140, 0, 0, '初始入库'),
(12, NULL, 'IN', 60, 0, 60, 0, 0, '初始入库'),
(13, NULL, 'IN', 200, 0, 200, 0, 0, '初始入库'),
(14, NULL, 'IN', 350, 0, 350, 0, 0, '初始入库'),
(15, NULL, 'IN', 280, 0, 280, 0, 0, '初始入库'),

-- 销售出库记录
(1, 1, 'OUT', 1, 200, 199, 0, 0, '订单ORD202501150001销售出库'),
(2, 2, 'OUT', 1, 80, 79, 0, 0, '订单ORD202501150002销售出库'),
(3, 3, 'OUT', 1, 180, 179, 0, 0, '订单ORD202501150003销售出库'),
(2, 4, 'OUT', 1, 40, 39, 0, 0, '订单ORD202501140001销售出库'),
(3, 5, 'OUT', 1, 100, 99, 0, 0, '订单ORD202501140002销售出库'),
(4, 6, 'OUT', 1, 350, 349, 0, 0, '订单ORD202501140003销售出库'),
(2, 7, 'OUT', 1, 79, 78, 0, 0, '订单ORD202501130001销售出库'),
(3, 7, 'OUT', 1, 99, 98, 0, 0, '订单ORD202501130001销售出库'),
(5, 7, 'OUT', 1, 140, 139, 0, 0, '订单ORD202501130001销售出库'),
(1, 8, 'OUT', 1, 250, 249, 0, 0, '订单ORD202501130002销售出库'),
(12, 9, 'OUT', 1, 60, 59, 0, 0, '订单ORD202501130003销售出库'),
(6, 10, 'OUT', 1, 120, 119, 0, 0, '订单ORD202501120001销售出库'),
(10, 11, 'OUT', 1, 220, 219, 0, 0, '订单ORD202501120002销售出库'),

-- 库存调整记录
(1, NULL, 'ADJUST', -50, 199, 149, 0, 0, '盘点调整，发现50台损坏'),
(2, NULL, 'ADJUST', 20, 100, 120, 0, 0, '补货入库'),
(3, NULL, 'ADJUST', -30, 249, 219, 0, 0, '盘点调整，发现30台损坏'),
(4, NULL, 'ADJUST', 10, 150, 160, 0, 0, '补货入库'),
(5, NULL, 'ADJUST', -10, 78, 68, 0, 0, '盘点调整，发现10台损坏'),
(6, NULL, 'ADJUST', 15, 119, 134, 0, 0, '补货入库'),
(7, NULL, 'ADJUST', -5, 39, 34, 0, 0, '盘点调整，发现5台损坏'),
(8, NULL, 'ADJUST', 8, 98, 106, 0, 0, '补货入库'),
(9, NULL, 'ADJUST', -20, 179, 159, 0, 0, '盘点调整，发现20台损坏'),
(10, NULL, 'ADJUST', 12, 219, 231, 0, 0, '补货入库'),
(11, NULL, 'ADJUST', -8, 139, 131, 0, 0, '盘点调整，发现8台损坏'),
(12, NULL, 'ADJUST', 5, 59, 64, 0, 0, '补货入库'),
(13, NULL, 'ADJUST', -15, 200, 185, 0, 0, '盘点调整，发现15台损坏'),
(14, NULL, 'ADJUST', 25, 349, 374, 0, 0, '补货入库'),
(15, NULL, 'ADJUST', -12, 280, 268, 0, 0, '盘点调整，发现12台损坏');

-- 插入优惠券使用记录测试数据
INSERT INTO coupon_usage (coupon_id, user_id, order_id, coupon_code, discount_amount, used_time, order_amount, actual_amount, status, remark) VALUES
-- 已使用的优惠券记录
(1, 1001, 1, 'NEWUSER100', 100.00, '2025-01-15 09:30:00', 8999.00, 8899.00, 'USED', '新用户专享券使用'),
(4, 1002, 2, 'DISCOUNT10', 1299.90, '2025-01-15 10:15:00', 12999.00, 11699.10, 'USED', '9折优惠券使用'),
(7, 1003, 3, 'IPHONE100', 100.00, '2025-01-15 11:20:00', 1999.00, 1899.00, 'USED', 'iPhone专享券使用'),
(2, 1004, 4, 'SAVE200', 200.00, '2025-01-14 15:45:00', 15999.00, 15799.00, 'USED', '满减200元券使用'),
(5, 1005, 5, 'DISCOUNT15', 374.85, '2025-01-14 17:10:00', 2499.00, 2124.15, 'USED', '85折优惠券使用'),
(3, 1006, 7, 'VIP500', 500.00, '2025-01-13 16:20:00', 19999.00, 19499.00, 'USED', 'VIP专享券使用'),
(8, 1007, 8, 'MACBOOK200', 200.00, '2025-01-13 14:15:00', 3999.00, 3799.00, 'USED', 'MacBook专享券使用'),
(6, 1008, 10, 'DISCOUNT20', 2000.00, '2025-01-12 16:45:00', 12999.00, 10999.00, 'USED', '8折优惠券使用'),
(1, 1010, 12, 'NEWUSER100', 100.00, '2025-01-11 17:15:00', 8999.00, 8899.00, 'REFUNDED', '订单取消，优惠券已退款');


-- 插入商品测试数据（指定ID）

-- 插入店铺测试数据
INSERT INTO stores (id, store_name, store_code, description, owner_name, owner_phone, address, status) VALUES
-- 电子产品专营店
(1, '苹果官方旗舰店', 'STORE001', '官方授权苹果产品专营店，正品保障，全国联保', '张三', '13800138001', '北京市朝阳区建国门外大街1号国贸大厦A座', 'ACTIVE'),
(2, '数码科技商城', 'STORE002', '各类电子产品，手机、电脑、配件齐全', '李四', '13800138002', '上海市浦东新区陆家嘴环路1000号', 'ACTIVE'),

-- 服装鞋帽专营店
(3, '时尚潮流馆', 'STORE003', '潮流服装鞋帽，紧跟时尚趋势', '王五', '13800138003', '广州市天河区珠江新城花城大道85号', 'ACTIVE'),
(4, '运动用品专营店', 'STORE004', '专业运动装备，Nike、Adidas等品牌', '赵六', '13800138004', '深圳市南山区科技园南区深南大道10000号', 'ACTIVE'),

-- 家居用品专营店
(5, '宜家家居商城', 'STORE005', '北欧风格家具，简约实用', '孙七', '13800138005', '杭州市西湖区文三路259号', 'ACTIVE'),

-- 美妆护肤专营店
(6, '美妆精品店', 'STORE006', '国际大牌美妆护肤产品，正品保证', '周八', '13800138006', '成都市锦江区红星路三段1号', 'ACTIVE'),

-- 食品饮料专营店
(7, '零食天地', 'STORE007', '各类零食饮料，满足你的味蕾', '吴九', '13800138007', '武汉市江汉区中山大道818号', 'ACTIVE'),

-- 综合商城
(8, '综合购物中心', 'STORE008', '一站式购物，各类商品应有尽有', '郑十', '13800138008', '南京市鼓楼区中山路321号', 'ACTIVE'),

-- 已关闭店铺
(9, '已关闭店铺示例', 'STORE009', '这是一个已关闭的店铺示例', '冯十一', '13800138009', '西安市雁塔区高新路52号', 'INACTIVE');

