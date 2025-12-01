-- ============================================
-- 测试数据插入脚本（可直接执行）
-- 1. 在主库 jiaoyi 中插入 stores 表数据（门店表不分库）
-- 2. 为 jiaoyi_0, jiaoyi_1, jiaoyi_2 三个分片库插入商品和库存数据
-- ============================================

-- ============================================
-- 主库 jiaoyi（stores 表）
-- ============================================
USE jiaoyi;

-- 插入店铺数据（stores表在主库中，不分库）
INSERT INTO stores (id, store_name, store_code, description, owner_name, owner_phone, address, status, product_list_version, create_time, update_time) VALUES
(0, '测试店铺0', 'STORE_000', '这是测试店铺0', '张三', '13800000000', '北京市朝阳区测试街道0号', 'ACTIVE', 0, NOW(), NOW()),
(1, '测试店铺1', 'STORE_001', '这是测试店铺1', '李四', '13800000001', '北京市朝阳区测试街道1号', 'ACTIVE', 0, NOW(), NOW()),
(2, '测试店铺2', 'STORE_002', '这是测试店铺2', '王五', '13800000002', '北京市朝阳区测试街道2号', 'ACTIVE', 0, NOW(), NOW()),
(3, '测试店铺3', 'STORE_003', '这是测试店铺3', '赵六', '13800000003', '上海市浦东新区测试街道3号', 'ACTIVE', 0, NOW(), NOW()),
(4, '测试店铺4', 'STORE_004', '这是测试店铺4', '钱七', '13800000004', '上海市浦东新区测试街道4号', 'ACTIVE', 0, NOW(), NOW()),
(5, '测试店铺5', 'STORE_005', '这是测试店铺5', '孙八', '13800000005', '上海市浦东新区测试街道5号', 'ACTIVE', 0, NOW(), NOW()),
(6, '测试店铺6', 'STORE_006', '这是测试店铺6', '周九', '13800000006', '广州市天河区测试街道6号', 'ACTIVE', 0, NOW(), NOW()),
(7, '测试店铺7', 'STORE_007', '这是测试店铺7', '吴十', '13800000007', '广州市天河区测试街道7号', 'ACTIVE', 0, NOW(), NOW()),
(8, '测试店铺8', 'STORE_008', '这是测试店铺8', '郑十一', '13800000008', '广州市天河区测试街道8号', 'ACTIVE', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE store_name=VALUES(store_name);

-- ============================================
-- jiaoyi_0 数据库
-- ============================================
USE jiaoyi_0;



-- 插入商品数据到 store_products_0（store_id = 0）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(0, '商品0-1', '店铺0的商品1', 99.00, 'https://example.com/image1.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(0, '商品0-2', '店铺0的商品2', 199.00, 'https://example.com/image2.jpg', '电子产品', 'ACTIVE', 0, 1, NOW(), NOW()),
(0, '商品0-3', '店铺0的商品3', 299.00, 'https://example.com/image3.jpg', '服装', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_1（store_id = 1）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(1, '商品1-1', '店铺1的商品1', 89.00, 'https://example.com/image4.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(1, '商品1-2', '店铺1的商品2', 189.00, 'https://example.com/image5.jpg', '食品', 'ACTIVE', 0, 1, NOW(), NOW()),
(1, '商品1-3', '店铺1的商品3', 289.00, 'https://example.com/image6.jpg', '日用品', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_2（store_id = 2）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(2, '商品2-1', '店铺2的商品1', 79.00, 'https://example.com/image7.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(2, '商品2-2', '店铺2的商品2', 179.00, 'https://example.com/image8.jpg', '图书', 'ACTIVE', 0, 1, NOW(), NOW()),
(2, '商品2-3', '店铺2的商品3', 279.00, 'https://example.com/image9.jpg', '文具', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入库存数据到 inventory_0（store_id = 0）
INSERT INTO inventory_0 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(10001, 0, 1001, '商品0-1', 100, 0, 10, 1000, NOW(), NOW()),
(10002, 0, 1002, '商品0-2', 200, 0, 20, 2000, NOW(), NOW()),
(10003, 0, 1003, '商品0-3', 300, 0, 30, 3000, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_1（store_id = 1）
INSERT INTO inventory_1 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(20001, 1, 2001, '商品1-1', 150, 0, 15, 1500, NOW(), NOW()),
(20002, 1, 2002, '商品1-2', 250, 0, 25, 2500, NOW(), NOW()),
(20003, 1, 2003, '商品1-3', 350, 0, 35, 3500, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_2（store_id = 2）
INSERT INTO inventory_2 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(30001, 2, 3001, '商品2-1', 120, 0, 12, 1200, NOW(), NOW()),
(30002, 2, 3002, '商品2-2', 220, 0, 22, 2200, NOW(), NOW()),
(30003, 2, 3003, '商品2-3', 320, 0, 32, 3200, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- ============================================
-- jiaoyi_1 数据库
-- ============================================
USE jiaoyi_1;



-- 插入商品数据到 store_products_0（store_id = 3）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(3, '商品3-1', '店铺3的商品1', 109.00, 'https://example.com/image10.jpg', '家电', 'ACTIVE', 0, 1, NOW(), NOW()),
(3, '商品3-2', '店铺3的商品2', 209.00, 'https://example.com/image11.jpg', '家电', 'ACTIVE', 0, 1, NOW(), NOW()),
(3, '商品3-3', '店铺3的商品3', 309.00, 'https://example.com/image12.jpg', '家具', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_1（store_id = 4）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(4, '商品4-1', '店铺4的商品1', 119.00, 'https://example.com/image13.jpg', '运动用品', 'ACTIVE', 0, 1, NOW(), NOW()),
(4, '商品4-2', '店铺4的商品2', 219.00, 'https://example.com/image14.jpg', '运动用品', 'ACTIVE', 0, 1, NOW(), NOW()),
(4, '商品4-3', '店铺4的商品3', 319.00, 'https://example.com/image15.jpg', '户外用品', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_2（store_id = 5）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(5, '商品5-1', '店铺5的商品1', 129.00, 'https://example.com/image16.jpg', '美妆', 'ACTIVE', 0, 1, NOW(), NOW()),
(5, '商品5-2', '店铺5的商品2', 229.00, 'https://example.com/image17.jpg', '美妆', 'ACTIVE', 0, 1, NOW(), NOW()),
(5, '商品5-3', '店铺5的商品3', 329.00, 'https://example.com/image18.jpg', '个护', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入库存数据到 inventory_0（store_id = 3）
INSERT INTO inventory_0 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(40001, 3, 4001, '商品3-1', 110, 0, 11, 1100, NOW(), NOW()),
(40002, 3, 4002, '商品3-2', 210, 0, 21, 2100, NOW(), NOW()),
(40003, 3, 4003, '商品3-3', 310, 0, 31, 3100, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_1（store_id = 4）
INSERT INTO inventory_1 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(50001, 4, 5001, '商品4-1', 160, 0, 16, 1600, NOW(), NOW()),
(50002, 4, 5002, '商品4-2', 260, 0, 26, 2600, NOW(), NOW()),
(50003, 4, 5003, '商品4-3', 360, 0, 36, 3600, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_2（store_id = 5）
INSERT INTO inventory_2 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(60001, 5, 6001, '商品5-1', 130, 0, 13, 1300, NOW(), NOW()),
(60002, 5, 6002, '商品5-2', 230, 0, 23, 2300, NOW(), NOW()),
(60003, 5, 6003, '商品5-3', 330, 0, 33, 3300, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- ============================================
-- jiaoyi_2 数据库
-- ============================================
USE jiaoyi_2;


-- 插入商品数据到 store_products_0（store_id = 6）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_0 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(6, '商品6-1', '店铺6的商品1', 139.00, 'https://example.com/image19.jpg', '数码配件', 'ACTIVE', 0, 1, NOW(), NOW()),
(6, '商品6-2', '店铺6的商品2', 239.00, 'https://example.com/image20.jpg', '数码配件', 'ACTIVE', 0, 1, NOW(), NOW()),
(6, '商品6-3', '店铺6的商品3', 339.00, 'https://example.com/image21.jpg', '手机', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_1（store_id = 7）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_1 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(7, '商品7-1', '店铺7的商品1', 149.00, 'https://example.com/image22.jpg', '电脑', 'ACTIVE', 0, 1, NOW(), NOW()),
(7, '商品7-2', '店铺7的商品2', 249.00, 'https://example.com/image23.jpg', '电脑', 'ACTIVE', 0, 1, NOW(), NOW()),
(7, '商品7-3', '店铺7的商品3', 349.00, 'https://example.com/image24.jpg', '平板', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入商品数据到 store_products_2（store_id = 8）
-- 注意：不指定 id 字段，让 ShardingSphere 使用雪花算法自动生成
INSERT INTO store_products_2 (store_id, product_name, description, unit_price, product_image, category, status, is_delete, version, create_time, update_time) VALUES
(8, '商品8-1', '店铺8的商品1', 159.00, 'https://example.com/image25.jpg', '智能家居', 'ACTIVE', 0, 1, NOW(), NOW()),
(8, '商品8-2', '店铺8的商品2', 259.00, 'https://example.com/image26.jpg', '智能家居', 'ACTIVE', 0, 1, NOW(), NOW()),
(8, '商品8-3', '店铺8的商品3', 359.00, 'https://example.com/image27.jpg', '智能穿戴', 'ACTIVE', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

-- 插入库存数据到 inventory_0（store_id = 6）
INSERT INTO inventory_0 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(70001, 6, 7001, '商品6-1', 140, 0, 14, 1400, NOW(), NOW()),
(70002, 6, 7002, '商品6-2', 240, 0, 24, 2400, NOW(), NOW()),
(70003, 6, 7003, '商品6-3', 340, 0, 34, 3400, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_1（store_id = 7）
INSERT INTO inventory_1 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(80001, 7, 8001, '商品7-1', 170, 0, 17, 1700, NOW(), NOW()),
(80002, 7, 8002, '商品7-2', 270, 0, 27, 2700, NOW(), NOW()),
(80003, 7, 8003, '商品7-3', 370, 0, 37, 3700, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

-- 插入库存数据到 inventory_2（store_id = 8）
INSERT INTO inventory_2 (id, store_id, product_id, product_name, current_stock, locked_stock, min_stock, max_stock, create_time, update_time) VALUES
(90001, 8, 9001, '商品8-1', 180, 0, 18, 1800, NOW(), NOW()),
(90002, 8, 9002, '商品8-2', 280, 0, 28, 2800, NOW(), NOW()),
(90003, 8, 9003, '商品8-3', 380, 0, 38, 3800, NOW(), NOW())
ON DUPLICATE KEY UPDATE current_stock=VALUES(current_stock);

