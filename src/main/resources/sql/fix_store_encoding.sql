-- 修复店铺表编码问题并重新插入数据
USE jiaoyi;

-- 设置连接字符集
SET NAMES utf8mb4;

-- 确保表的字符集正确
ALTER TABLE stores CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE store_products CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 删除旧数据（如果存在）
DELETE FROM store_products WHERE store_id <= 9;
DELETE FROM stores WHERE id <= 9;

-- 重新插入店铺数据（确保使用UTF8编码）
INSERT INTO stores (id, store_name, store_code, description, owner_name, owner_phone, address, status) VALUES
(1, '苹果官方旗舰店', 'STORE001', '官方授权苹果产品专营店，正品保障，全国联保', '张三', '13800138001', '北京市朝阳区建国门外大街1号国贸大厦A座', 'ACTIVE'),
(2, '数码科技商城', 'STORE002', '各类电子产品，手机、电脑、配件齐全', '李四', '13800138002', '上海市浦东新区陆家嘴环路1000号', 'ACTIVE'),
(3, '时尚潮流馆', 'STORE003', '潮流服装鞋帽，紧跟时尚趋势', '王五', '13800138003', '广州市天河区珠江新城花城大道85号', 'ACTIVE'),
(4, '运动用品专营店', 'STORE004', '专业运动装备，Nike、Adidas等品牌', '赵六', '13800138004', '深圳市南山区科技园南区深南大道10000号', 'ACTIVE'),
(5, '宜家家居商城', 'STORE005', '北欧风格家具，简约实用', '孙七', '13800138005', '杭州市西湖区文三路259号', 'ACTIVE'),
(6, '美妆精品店', 'STORE006', '国际大牌美妆护肤产品，正品保证', '周八', '13800138006', '成都市锦江区红星路三段1号', 'ACTIVE'),
(7, '零食天地', 'STORE007', '各类零食饮料，满足你的味蕾', '吴九', '13800138007', '武汉市江汉区中山大道818号', 'ACTIVE'),
(8, '综合购物中心', 'STORE008', '一站式购物，各类商品应有尽有', '郑十', '13800138008', '南京市鼓楼区中山路321号', 'ACTIVE'),
(9, '已关闭店铺示例', 'STORE009', '这是一个已关闭的店铺示例', '冯十一', '13800138009', '西安市雁塔区高新路52号', 'INACTIVE')
ON DUPLICATE KEY UPDATE
    store_name = VALUES(store_name),
    description = VALUES(description),
    owner_name = VALUES(owner_name),
    owner_phone = VALUES(owner_phone),
    address = VALUES(address),
    status = VALUES(status);

-- 重新插入店铺商品关联数据
INSERT INTO store_products (store_id, product_id, stock_quantity, price, status) VALUES
(1, 1, 50, 8999.00, 'ACTIVE'),
(1, 2, 30, 15999.00, 'ACTIVE'),
(1, 3, 80, 4399.00, 'ACTIVE'),
(1, 4, 120, 1999.00, 'ACTIVE'),
(1, 5, 60, 2999.00, 'ACTIVE'),
(2, 1, 25, 8999.00, 'ACTIVE'),
(2, 3, 40, 4399.00, 'ACTIVE'),
(2, 28, 15, 5999.00, 'ACTIVE'),
(3, 6, 200, 899.00, 'ACTIVE'),
(3, 7, 150, 1299.00, 'ACTIVE'),
(3, 8, 500, 99.00, 'ACTIVE'),
(3, 9, 100, 299.00, 'ACTIVE'),
(3, 10, 80, 199.00, 'ACTIVE'),
(3, 26, 5, 2999.00, 'ACTIVE'),
(3, 31, 50, 99.00, 'ACTIVE'),
(4, 6, 100, 899.00, 'ACTIVE'),
(4, 7, 80, 1299.00, 'ACTIVE'),
(4, 26, 3, 2999.00, 'ACTIVE'),
(5, 11, 40, 1299.00, 'ACTIVE'),
(5, 12, 25, 3999.00, 'ACTIVE'),
(5, 13, 60, 599.00, 'ACTIVE'),
(5, 14, 300, 49.00, 'ACTIVE'),
(5, 15, 90, 299.00, 'ACTIVE'),
(6, 16, 70, 899.00, 'ACTIVE'),
(6, 17, 85, 799.00, 'ACTIVE'),
(6, 18, 45, 1299.00, 'ACTIVE'),
(6, 19, 120, 399.00, 'ACTIVE'),
(6, 20, 65, 599.00, 'ACTIVE'),
(6, 27, 8, 1999.00, 'ACTIVE'),
(6, 29, 0, 199.00, 'INACTIVE'),
(7, 21, 200, 199.00, 'ACTIVE'),
(7, 22, 300, 149.00, 'ACTIVE'),
(7, 23, 150, 89.00, 'ACTIVE'),
(7, 24, 500, 69.00, 'ACTIVE'),
(7, 25, 1000, 12.00, 'ACTIVE'),
(8, 1, 20, 8999.00, 'ACTIVE'),
(8, 6, 50, 899.00, 'ACTIVE'),
(8, 11, 15, 1299.00, 'ACTIVE'),
(8, 16, 30, 899.00, 'ACTIVE'),
(8, 21, 80, 199.00, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    stock_quantity = VALUES(stock_quantity),
    price = VALUES(price),
    status = VALUES(status);

SELECT '数据修复完成，请刷新查看！' AS message;

