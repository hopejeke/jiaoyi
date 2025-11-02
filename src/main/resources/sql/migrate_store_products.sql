-- 迁移脚本：将 store_products 表从关联表改为完整商品表
-- 注意：这个脚本会删除现有数据，请谨慎执行

SET NAMES utf8mb4;
USE jiaoyi;

-- 备份现有数据（如果需要）
-- CREATE TABLE store_products_backup AS SELECT * FROM store_products;

-- 删除旧的 store_products 表
DROP TABLE IF EXISTS store_products;

-- 创建新的店铺商品表（包含所有商品字段 + store_id）
CREATE TABLE store_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    product_image VARCHAR(500) COMMENT '商品图片',
    category VARCHAR(100) COMMENT '商品分类',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '商品状态：ACTIVE-上架，INACTIVE-下架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_id (store_id),
    INDEX idx_product_name (product_name),
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺商品表';

-- 如果需要从旧数据迁移，可以执行以下SQL（需要根据实际业务逻辑调整）
-- 注意：这个迁移假设旧数据中 store_products 通过 product_id 关联到 products 表
-- INSERT INTO store_products (store_id, product_name, description, unit_price, product_image, category, status)
-- SELECT 
--     sp.store_id,
--     p.product_name,
--     p.description,
--     COALESCE(sp.price, p.unit_price) as unit_price,
--     p.product_image,
--     p.category,
--     sp.status
-- FROM store_products_backup sp
-- INNER JOIN products p ON sp.product_id = p.id;

SELECT 'Migration completed! Table store_products has been restructured.' AS message;

