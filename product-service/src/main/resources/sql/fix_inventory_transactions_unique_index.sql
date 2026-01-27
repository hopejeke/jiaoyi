-- ============================================
-- 修复 inventory_transactions 表的唯一索引
-- ============================================
-- 说明：
-- 1. 删除旧的唯一索引 uk_order_sku (order_id, sku_id)
-- 2. 创建新的唯一索引 uk_order_sku_type (order_id, sku_id, transaction_type)
-- 3. 原因：同一订单同一SKU可能有多种操作（LOCK、UNLOCK、OUT等），需要按操作类型区分
-- ============================================

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS fix_inventory_transactions_unique_index(db_name VARCHAR(255), table_prefix VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE old_index_exists INT DEFAULT 0;
    DECLARE new_index_exists INT DEFAULT 0;
    DECLARE order_id_exists INT DEFAULT 0;
    DECLARE sku_id_exists INT DEFAULT 0;
    DECLARE transaction_type_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT(table_prefix, LPAD(i, 2, '0'));

        -- 检查 order_id 字段是否存在
        SELECT COUNT(*) INTO order_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'order_id';

        -- 检查 sku_id 字段是否存在
        SELECT COUNT(*) INTO sku_id_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'sku_id';

        -- 检查 transaction_type 字段是否存在
        SELECT COUNT(*) INTO transaction_type_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = db_name
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = 'transaction_type';
        
        IF order_id_exists > 0 AND sku_id_exists > 0 AND transaction_type_exists > 0 THEN
            -- 检查旧索引是否存在
            SELECT COUNT(*) INTO old_index_exists
            FROM INFORMATION_SCHEMA.STATISTICS 
            WHERE TABLE_SCHEMA = db_name 
              AND TABLE_NAME = table_name 
              AND INDEX_NAME = 'uk_order_sku';
            
            -- 检查新索引是否存在
            SELECT COUNT(*) INTO new_index_exists
            FROM INFORMATION_SCHEMA.STATISTICS 
            WHERE TABLE_SCHEMA = db_name 
              AND TABLE_NAME = table_name 
              AND INDEX_NAME = 'uk_order_sku_type';
            
            -- 删除旧索引（如果存在）
            IF old_index_exists > 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, ' DROP INDEX uk_order_sku');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 删除旧唯一索引 uk_order_sku 成功') AS message;
            END IF;
            
            -- 创建新索引（如果不存在）
            IF new_index_exists = 0 THEN
                SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, 
                    ' ADD UNIQUE KEY uk_order_sku_type (order_id, sku_id, transaction_type) COMMENT ''幂等性唯一索引：同一订单同一SKU同一操作类型只能执行一次''');
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 创建新唯一索引 uk_order_sku_type 成功') AS message;
            ELSE
                SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': 新唯一索引 uk_order_sku_type 已存在，跳过') AS message;
            END IF;
        ELSE
            SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': order_id、sku_id 或 transaction_type 字段不存在，跳过索引修复') AS message;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 对每个分片数据库执行
CALL fix_inventory_transactions_unique_index('jiaoyi_product_0', 'inventory_transactions_');
CALL fix_inventory_transactions_unique_index('jiaoyi_product_1', 'inventory_transactions_');
CALL fix_inventory_transactions_unique_index('jiaoyi_product_2', 'inventory_transactions_');

DROP PROCEDURE IF EXISTS fix_inventory_transactions_unique_index;


