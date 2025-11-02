-- 迁移脚本：添加店铺表和修改时间字段
USE jiaoyi;

-- ========== 第一部分：修改现有表的时间字段为自动更新 ==========

-- 修改优惠券表
ALTER TABLE coupons
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 修改订单表
ALTER TABLE orders
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 修改订单优惠券关联表
ALTER TABLE order_coupons
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 修改库存表
ALTER TABLE inventory
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 修改库存变动记录表
ALTER TABLE inventory_transactions
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 修改优惠券使用记录表
ALTER TABLE coupon_usage
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 修改商品表
ALTER TABLE products
MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ========== 第二部分：创建店铺表和关联表 ==========

-- 创建店铺表（如果不存在）
CREATE TABLE IF NOT EXISTS stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name VARCHAR(200) NOT NULL COMMENT '店铺名称',
    store_code VARCHAR(50) NOT NULL UNIQUE COMMENT '店铺编码',
    description TEXT COMMENT '店铺描述',
    owner_name VARCHAR(100) COMMENT '店主姓名',
    owner_phone VARCHAR(20) COMMENT '店主电话',
    address VARCHAR(500) COMMENT '店铺地址',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态：ACTIVE-营业中，INACTIVE-已关闭',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_code (store_code),
    INDEX idx_store_name (store_name),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- 创建店铺商品关联表（如果不存在）
CREATE TABLE IF NOT EXISTS store_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    stock_quantity INT NOT NULL DEFAULT 0 COMMENT '该店铺库存数量',
    price DECIMAL(10,2) COMMENT '该店铺商品价格（如果为空则使用商品表价格）',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态：ACTIVE-上架，INACTIVE-下架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_store_product (store_id, product_id),
    INDEX idx_store_id (store_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺商品关联表';

-- ========== 第三部分：插入店铺测试数据 ==========

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
(9, '已关闭店铺示例', 'STORE009', '这是一个已关闭的店铺示例', '冯十一', '13800138009', '西安市雁塔区高新路52号', 'INACTIVE')
ON DUPLICATE KEY UPDATE
    store_name = VALUES(store_name),
    description = VALUES(description),
    owner_name = VALUES(owner_name),
    owner_phone = VALUES(owner_phone),
    address = VALUES(address),
    status = VALUES(status);

-- 插入店铺商品关联测试数据（假设商品ID 1-31 存在）
INSERT INTO store_products (store_id, product_id, stock_quantity, price, status) VALUES
-- 店铺1（苹果官方旗舰店）- 电子产品
(1, 1, 50, 8999.00, 'ACTIVE'),  -- iPhone 15 Pro
(1, 2, 30, 15999.00, 'ACTIVE'),  -- MacBook Pro 14英寸
(1, 3, 80, 4399.00, 'ACTIVE'),   -- iPad Air
(1, 4, 120, 1999.00, 'ACTIVE'),  -- AirPods Pro
(1, 5, 60, 2999.00, 'ACTIVE'),   -- Apple Watch Series 9

-- 店铺2（数码科技商城）- 电子产品
(2, 1, 25, 8999.00, 'ACTIVE'),   -- iPhone 15 Pro
(2, 3, 40, 4399.00, 'ACTIVE'),   -- iPad Air
(2, 28, 15, 5999.00, 'ACTIVE'),  -- 热门手机

-- 店铺3（时尚潮流馆）- 服装鞋帽
(3, 6, 200, 899.00, 'ACTIVE'),   -- Nike Air Max 270
(3, 7, 150, 1299.00, 'ACTIVE'),  -- Adidas Ultraboost 22
(3, 8, 500, 99.00, 'ACTIVE'),    -- Uniqlo 优衣库基础T恤
(3, 9, 100, 299.00, 'ACTIVE'),   -- Zara 休闲衬衫
(3, 10, 80, 199.00, 'ACTIVE'),   -- H&M 牛仔裤
(3, 26, 5, 2999.00, 'ACTIVE'),   -- 限量版球鞋
(3, 31, 50, 99.00, 'ACTIVE'),    -- 过季服装

-- 店铺4（运动用品专营店）- 服装鞋帽
(4, 6, 100, 899.00, 'ACTIVE'),   -- Nike Air Max 270
(4, 7, 80, 1299.00, 'ACTIVE'),   -- Adidas Ultraboost 22
(4, 26, 3, 2999.00, 'ACTIVE'),   -- 限量版球鞋

-- 店铺5（宜家家居商城）- 家居用品
(5, 11, 40, 1299.00, 'ACTIVE'),  -- 小米空气净化器4
(5, 12, 25, 3999.00, 'ACTIVE'),  -- 戴森V15吸尘器
(5, 13, 60, 599.00, 'ACTIVE'),   -- 宜家书桌
(5, 14, 300, 49.00, 'ACTIVE'),   -- 无印良品收纳盒
(5, 15, 90, 299.00, 'ACTIVE'),   -- 飞利浦台灯

-- 店铺6（美妆精品店）- 美妆护肤
(6, 16, 70, 899.00, 'ACTIVE'),   -- 兰蔻小黑瓶精华
(6, 17, 85, 799.00, 'ACTIVE'),   -- 雅诗兰黛小棕瓶
(6, 18, 45, 1299.00, 'ACTIVE'),  -- SK-II神仙水
(6, 19, 120, 399.00, 'ACTIVE'),  -- 香奈儿口红
(6, 20, 65, 599.00, 'ACTIVE'),  -- 迪奥香水
(6, 27, 8, 1999.00, 'ACTIVE'),   -- 高端护肤品
(6, 29, 0, 199.00, 'INACTIVE'),  -- 爆款口红（已下架）

-- 店铺7（零食天地）- 食品饮料
(7, 21, 200, 199.00, 'ACTIVE'),  -- 三只松鼠坚果礼盒
(7, 22, 300, 149.00, 'ACTIVE'),  -- 百草味零食大礼包
(7, 23, 150, 89.00, 'ACTIVE'),   -- 星巴克咖啡豆
(7, 24, 500, 69.00, 'ACTIVE'),   -- 蒙牛特仑苏牛奶
(7, 25, 1000, 12.00, 'ACTIVE'),  -- 农夫山泉矿泉水

-- 店铺8（综合购物中心）- 各类商品
(8, 1, 20, 8999.00, 'ACTIVE'),   -- iPhone 15 Pro
(8, 6, 50, 899.00, 'ACTIVE'),    -- Nike Air Max 270
(8, 11, 15, 1299.00, 'ACTIVE'),  -- 小米空气净化器4
(8, 16, 30, 899.00, 'ACTIVE'),   -- 兰蔻小黑瓶精华
(8, 21, 80, 199.00, 'ACTIVE')   -- 三只松鼠坚果礼盒
ON DUPLICATE KEY UPDATE
    stock_quantity = VALUES(stock_quantity),
    price = VALUES(price),
    status = VALUES(status);

-- 完成提示
SELECT 'Migration completed successfully!' AS message;

