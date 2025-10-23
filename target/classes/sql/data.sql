-- 插入测试数据（可选）
USE jiaoyi;

-- 插入测试订单数据
INSERT INTO orders (order_no, user_id, status, total_amount, receiver_name, receiver_phone, receiver_address, remark, create_time, update_time) VALUES
('ORD202312010001', 1001, 'PENDING', 299.00, '张三', '13800138001', '北京市朝阳区xxx街道xxx号', '请尽快发货', NOW(), NOW()),
('ORD202312010002', 1002, 'PAID', 599.00, '李四', '13800138002', '上海市浦东新区xxx路xxx号', '', NOW(), NOW()),
('ORD202312010003', 1001, 'SHIPPED', 199.00, '张三', '13800138001', '北京市朝阳区xxx街道xxx号', '', NOW(), NOW());

-- 插入测试订单项数据
INSERT INTO order_items (order_id, product_id, product_name, product_image, unit_price, quantity, subtotal) VALUES
(1, 2001, 'iPhone 15 Pro', 'https://example.com/iphone15pro.jpg', 299.00, 1, 299.00),
(2, 2002, 'MacBook Air M2', 'https://example.com/macbook.jpg', 599.00, 1, 599.00),
(3, 2003, 'AirPods Pro', 'https://example.com/airpods.jpg', 199.00, 1, 199.00);

-- 插入测试库存数据
INSERT INTO inventory (product_id, product_name, current_stock, locked_stock, available_stock, min_stock, max_stock, create_time, update_time) VALUES
(2001, 'iPhone 15 Pro', 100, 0, 100, 10, 1000, NOW(), NOW()),
(2002, 'MacBook Air M2', 50, 0, 50, 5, 500, NOW(), NOW()),
(2003, 'AirPods Pro', 200, 0, 200, 20, 2000, NOW(), NOW()),
(2004, 'iPad Pro', 80, 0, 80, 8, 800, NOW(), NOW()),
(2005, 'Apple Watch', 150, 0, 150, 15, 1500, NOW(), NOW());
