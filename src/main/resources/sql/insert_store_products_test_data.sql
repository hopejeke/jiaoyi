SET NAMES utf8mb4;
USE jiaoyi;

-- 清空现有数据（如果有）
DELETE FROM store_products;

-- 重置自增ID
ALTER TABLE store_products AUTO_INCREMENT = 1;

-- 为店铺1（苹果官方旗舰店）- 电子产品
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(1, 'iPhone 15 Pro', '苹果最新款旗舰手机，A17 Pro芯片，钛金属材质', 8999.00, 'https://example.com/iphone15pro.jpg', '电子产品', 'ACTIVE'),
(1, 'MacBook Pro 14英寸', 'M3芯片，专业级性能，适合创意工作', 15999.00, 'https://example.com/macbookpro14.jpg', '电子产品', 'ACTIVE'),
(1, 'iPad Air', 'M2芯片，轻薄便携，支持Apple Pencil', 4399.00, 'https://example.com/ipadair.jpg', '电子产品', 'ACTIVE'),
(1, 'AirPods Pro', '主动降噪，空间音频，无线充电', 1999.00, 'https://example.com/airpodspro.jpg', '电子产品', 'ACTIVE'),
(1, 'Apple Watch Series 9', '智能健康监测，GPS定位，全天候显示', 2999.00, 'https://example.com/watch9.jpg', '电子产品', 'ACTIVE');

-- 为店铺2（数码科技商城）- 电子产品
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(2, 'iPhone 15 Pro', '苹果最新款旗舰手机，A17 Pro芯片', 8999.00, 'https://example.com/iphone15pro.jpg', '电子产品', 'ACTIVE'),
(2, 'iPad Air', 'M2芯片平板电脑，轻薄便携', 4399.00, 'https://example.com/ipadair.jpg', '电子产品', 'ACTIVE'),
(2, '华为Mate 60 Pro', '麒麟9000S芯片，支持卫星通信', 5999.00, 'https://example.com/mate60pro.jpg', '电子产品', 'ACTIVE'),
(2, '小米14 Pro', '骁龙8 Gen3，徕卡影像系统', 4999.00, 'https://example.com/mi14pro.jpg', '电子产品', 'ACTIVE');

-- 为店铺3（时尚潮流馆）- 服装鞋帽
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(3, 'Nike Air Max 270', '经典气垫跑鞋，舒适透气', 899.00, 'https://example.com/airmax270.jpg', '服装鞋帽', 'ACTIVE'),
(3, 'Adidas Ultraboost 22', 'boost缓震科技，舒适跑步鞋', 1299.00, 'https://example.com/ultraboost22.jpg', '服装鞋帽', 'ACTIVE'),
(3, 'Uniqlo 优衣库基础T恤', '纯棉材质，简约百搭', 99.00, 'https://example.com/uniqlo_tshirt.jpg', '服装鞋帽', 'ACTIVE'),
(3, 'Zara 休闲衬衫', '时尚百搭，商务休闲两相宜', 299.00, 'https://example.com/zara_shirt.jpg', '服装鞋帽', 'ACTIVE'),
(3, 'H&M 牛仔裤', '经典版型，舒适耐穿', 199.00, 'https://example.com/hm_jeans.jpg', '服装鞋帽', 'ACTIVE'),
(3, '限量版球鞋', '联名款球鞋，限量发售', 2999.00, 'https://example.com/limited_sneakers.jpg', '服装鞋帽', 'ACTIVE'),
(3, '过季服装特价', '清仓处理，超值优惠', 99.00, 'https://example.com/clearance.jpg', '服装鞋帽', 'ACTIVE');

-- 为店铺4（运动用品专营店）- 运动装备
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(4, 'Nike Air Max 270', '经典气垫跑鞋，舒适透气', 899.00, 'https://example.com/airmax270.jpg', '运动装备', 'ACTIVE'),
(4, 'Adidas Ultraboost 22', 'boost缓震科技，舒适跑步鞋', 1299.00, 'https://example.com/ultraboost22.jpg', '运动装备', 'ACTIVE'),
(4, '限量版球鞋', '联名款球鞋，限量发售', 2999.00, 'https://example.com/limited_sneakers.jpg', '运动装备', 'ACTIVE'),
(4, '瑜伽垫', '防滑瑜伽垫，加厚设计', 129.00, 'https://example.com/yoga_mat.jpg', '运动装备', 'ACTIVE'),
(4, '哑铃套装', '可调节重量，家庭健身必备', 399.00, 'https://example.com/dumbbells.jpg', '运动装备', 'ACTIVE');

-- 为店铺5（宜家家居商城）- 家居用品
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(5, '小米空气净化器4', '高效除甲醛，智能控制', 1299.00, 'https://example.com/air_purifier.jpg', '家居用品', 'ACTIVE'),
(5, '戴森V15吸尘器', '激光探测微尘，强劲吸力', 3999.00, 'https://example.com/dyson_v15.jpg', '家居用品', 'ACTIVE'),
(5, '宜家书桌', '简约风格，环保材质', 599.00, 'https://example.com/ikea_desk.jpg', '家居用品', 'ACTIVE'),
(5, '无印良品收纳盒', '简约设计，实用收纳', 49.00, 'https://example.com/muji_box.jpg', '家居用品', 'ACTIVE'),
(5, '飞利浦台灯', '护眼LED，多档调光', 299.00, 'https://example.com/philips_lamp.jpg', '家居用品', 'ACTIVE'),
(5, '记忆棉床垫', '慢回弹，舒适睡眠', 899.00, 'https://example.com/mattress.jpg', '家居用品', 'ACTIVE');

-- 为店铺6（美妆精品店）- 美妆护肤
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(6, '兰蔻小黑瓶精华', '抗老修护，紧致肌肤', 899.00, 'https://example.com/lancome_serum.jpg', '美妆护肤', 'ACTIVE'),
(6, '雅诗兰黛小棕瓶', '经典修护精华，淡化细纹', 799.00, 'https://example.com/estee_serum.jpg', '美妆护肤', 'ACTIVE'),
(6, 'SK-II神仙水', '明星产品，调理肌肤', 1299.00, 'https://example.com/sk2_toner.jpg', '美妆护肤', 'ACTIVE'),
(6, '香奈儿口红', '经典色号，持久显色', 399.00, 'https://example.com/chanel_lipstick.jpg', '美妆护肤', 'ACTIVE'),
(6, '迪奥香水', '优雅香调，持久留香', 599.00, 'https://example.com/dior_perfume.jpg', '美妆护肤', 'ACTIVE'),
(6, '高端护肤品套装', '全系列护理，奢华体验', 1999.00, 'https://example.com/luxury_set.jpg', '美妆护肤', 'ACTIVE'),
(6, '爆款口红', '网红推荐，热销色号', 199.00, 'https://example.com/hot_lipstick.jpg', '美妆护肤', 'INACTIVE');

-- 为店铺7（零食天地）- 食品饮料
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(7, '三只松鼠坚果礼盒', '混合坚果，营养丰富', 199.00, 'https://example.com/nuts_gift.jpg', '食品饮料', 'ACTIVE'),
(7, '百草味零食大礼包', '多种零食组合，满足味蕾', 149.00, 'https://example.com/snacks_bag.jpg', '食品饮料', 'ACTIVE'),
(7, '星巴克咖啡豆', '精选阿拉比卡豆，浓郁香醇', 89.00, 'https://example.com/starbucks_coffee.jpg', '食品饮料', 'ACTIVE'),
(7, '蒙牛特仑苏牛奶', '优质奶源，营养健康', 69.00, 'https://example.com/mengniu_milk.jpg', '食品饮料', 'ACTIVE'),
(7, '农夫山泉矿泉水', '天然矿泉水，健康安全', 12.00, 'https://example.com/nongfu_water.jpg', '食品饮料', 'ACTIVE'),
(7, '进口巧克力', '比利时进口，丝滑香醇', 88.00, 'https://example.com/chocolate.jpg', '食品饮料', 'ACTIVE');

-- 为店铺8（综合购物中心）- 各类商品
INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status) VALUES
(8, 'iPhone 15 Pro', '苹果最新款旗舰手机', 8999.00, 'https://example.com/iphone15pro.jpg', '电子产品', 'ACTIVE'),
(8, 'Nike Air Max 270', '经典气垫跑鞋', 899.00, 'https://example.com/airmax270.jpg', '服装鞋帽', 'ACTIVE'),
(8, '小米空气净化器4', '高效除甲醛', 1299.00, 'https://example.com/air_purifier.jpg', '家居用品', 'ACTIVE'),
(8, '兰蔻小黑瓶精华', '抗老修护精华', 899.00, 'https://example.com/lancome_serum.jpg', '美妆护肤', 'ACTIVE'),
(8, '三只松鼠坚果礼盒', '混合坚果礼盒', 199.00, 'https://example.com/nuts_gift.jpg', '食品饮料', 'ACTIVE'),
(8, '综合大礼包', '多品类组合，超值优惠', 599.00, 'https://example.com/combo_gift.jpg', '综合商品', 'ACTIVE');

SELECT '店铺商品测试数据插入完成！' AS message;
SELECT COUNT(*) AS total_products FROM store_products;

