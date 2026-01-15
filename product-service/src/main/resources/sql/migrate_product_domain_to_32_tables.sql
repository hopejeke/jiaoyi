-- ============================================
-- 商品域：迁移到32张表/库（固定虚拟桶1024 + 路由表映射）
-- ============================================
-- 说明：
-- 1. 所有商品事实表改为32张表/库（store_products_00..store_products_31）
-- 2. 所有表必须包含 product_shard_id 字段（INT NOT NULL）
-- 3. product_shard_id 计算：hash(storeId) & 1023
-- 4. 分库路由：通过 product_shard_bucket_route 表查询
-- 5. 分表路由：product_shard_id % 32
-- 6. 执行前请确保已创建 product_shard_bucket_route 路由表
-- ============================================

-- ============================================
-- 步骤1：创建 product_shard_bucket_route 路由表（如果不存在）
-- ============================================
USE jiaoyi;

-- 参考 create_product_shard_bucket_route.sql 脚本创建路由表

-- ============================================
-- 步骤2：为现有表添加 product_shard_id 字段（如果不存在）
-- ============================================
-- 注意：此步骤需要为所有现有表（store_products_0..store_products_2等）添加 product_shard_id 字段
-- 实际执行时，需要根据现有表结构进行调整

-- store_products 表（示例：为 jiaoyi_product_0 的 store_products_0..store_products_2 添加 product_shard_id）
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，需要使用存储过程或手动检查
USE jiaoyi_product_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_product_shard_id_column_if_not_exists()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    
    -- 检查并添加 store_products_0 的 product_shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_0' 
      AND COLUMN_NAME = 'product_shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE store_products_0 ADD COLUMN product_shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于storeId计算）' AFTER store_id;
    END IF;
    
    -- 检查并添加索引
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_0' 
      AND INDEX_NAME = 'idx_product_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE store_products_0 ADD INDEX idx_product_shard_id (product_shard_id);
    END IF;
    
    -- 检查并添加 store_products_1 的 product_shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_1' 
      AND COLUMN_NAME = 'product_shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE store_products_1 ADD COLUMN product_shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于storeId计算）' AFTER store_id;
    END IF;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_1' 
      AND INDEX_NAME = 'idx_product_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE store_products_1 ADD INDEX idx_product_shard_id (product_shard_id);
    END IF;
    
    -- 检查并添加 store_products_2 的 product_shard_id 列
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_2' 
      AND COLUMN_NAME = 'product_shard_id';
    
    IF column_exists = 0 THEN
        ALTER TABLE store_products_2 ADD COLUMN product_shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于storeId计算）' AFTER store_id;
    END IF;
    
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'jiaoyi_product_0' 
      AND TABLE_NAME = 'store_products_2' 
      AND INDEX_NAME = 'idx_product_shard_id';
    
    IF index_exists = 0 THEN
        ALTER TABLE store_products_2 ADD INDEX idx_product_shard_id (product_shard_id);
    END IF;
END$$

DELIMITER ;

CALL add_product_shard_id_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_product_shard_id_column_if_not_exists;

-- 注意：需要为 jiaoyi_product_1 和 jiaoyi_product_2 也执行相同操作
-- 注意：需要为 product_sku, inventory 也执行相同操作

-- ============================================
-- 步骤3：创建32张表/库（store_products_00..store_products_31）
-- ============================================
-- 注意：此步骤假设已有3张表（store_products_0..store_products_2），需要扩展为32张表
-- 实际执行时，需要先迁移数据，然后创建新表结构

USE jiaoyi_product_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_store_products_tables_32()
BEGIN
    DECLARE i INT DEFAULT 3;
    WHILE i < 32 DO
        SET @table_name = CONCAT('store_products_', LPAD(i, 2, '0'));
        SET @comment_text = CONCAT('店铺商品表_', i);
        SET @sql = CONCAT('
            CREATE TABLE IF NOT EXISTS ', @table_name, ' (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                store_id BIGINT NOT NULL COMMENT ''店铺ID'',
                product_shard_id INT NOT NULL COMMENT ''分片ID（0-1023，基于storeId计算）'',
                product_name VARCHAR(200) NOT NULL COMMENT ''商品名称'',
                description TEXT COMMENT ''商品描述'',
                unit_price DECIMAL(10,2) NOT NULL COMMENT ''商品单价'',
                product_image VARCHAR(500) COMMENT ''商品图片'',
                category VARCHAR(100) COMMENT ''商品分类'',
                status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''商品状态：ACTIVE-上架，INACTIVE-下架'',
                is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除：0-未删除，1-已删除（逻辑删除）'',
                version BIGINT NOT NULL DEFAULT 0 COMMENT ''版本号（用于缓存一致性控制）'',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
                INDEX idx_store_id (store_id),
                INDEX idx_product_shard_id (product_shard_id),
                INDEX idx_product_name (product_name),
                INDEX idx_category (category),
                INDEX idx_status (status),
                INDEX idx_is_delete (is_delete),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''', @comment_text, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_store_products_tables_32();
DROP PROCEDURE IF EXISTS create_store_products_tables_32;

-- 注意：需要为 jiaoyi_product_1 和 jiaoyi_product_2 也执行相同的存储过程
-- 注意：需要为 product_sku, inventory, outbox 也执行类似操作

-- ============================================
-- 步骤4：重命名现有表（store_products_0 -> store_products_00, store_products_1 -> store_products_01, store_products_2 -> store_products_02）
-- ============================================
-- 注意：MySQL不支持直接重命名表，需要先创建新表，迁移数据，然后删除旧表
-- 此步骤需要谨慎执行，建议在低峰期进行

-- 示例（仅展示逻辑，实际执行需要数据迁移）：
-- RENAME TABLE store_products_0 TO store_products_00;
-- RENAME TABLE store_products_1 TO store_products_01;
-- RENAME TABLE store_products_2 TO store_products_02;

-- ============================================
-- 步骤5：更新 outbox 表为32张表/库
-- ============================================
-- outbox_00..outbox_31，添加 claim 索引和 uk_event_id

-- ============================================
-- 步骤6：验证和清理
-- ============================================
-- 1. 验证所有表都有 product_shard_id 字段
-- 2. 验证所有表都有32张表/库
-- 3. 验证索引已创建（idx_product_shard_id, idx_claim, idx_cleanup等）
-- 4. 清理旧的分片表（如果数据已迁移）

