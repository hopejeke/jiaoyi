-- ============================================
-- 修复 inventory_transactions 并发问题
-- ============================================
-- 问题：当前唯一索引 (order_id, sku_id, transaction_type) 允许同一订单同一SKU有多个记录
--      导致 OUT 和 UNLOCK 可以同时插入，造成并发漏洞
-- 
-- 解决方案：改为 (order_id, sku_id) 唯一索引，只允许一条记录
--           用 CAS 更新 transaction_type 做状态机：LOCKED → DEDUCTED/UNLOCKED
-- ============================================

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS fix_inventory_transactions_concurrent_issue(db_name VARCHAR(255), table_prefix VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);
    DECLARE old_index_exists INT DEFAULT 0;
    DECLARE new_index_exists INT DEFAULT 0;
    
    WHILE i < 32 DO
        SET table_name = CONCAT(table_prefix, LPAD(i, 2, '0'));

        -- 检查旧索引是否存在
        SELECT COUNT(*) INTO old_index_exists
        FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = db_name 
          AND TABLE_NAME = table_name 
          AND INDEX_NAME = 'uk_order_sku_type';
        
        -- 检查新索引是否存在
        SELECT COUNT(*) INTO new_index_exists
        FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = db_name 
          AND TABLE_NAME = table_name 
          AND INDEX_NAME = 'uk_order_sku';
        
        -- 删除旧索引（如果存在）
        IF old_index_exists > 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, ' DROP INDEX uk_order_sku_type');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 删除旧唯一索引 uk_order_sku_type 成功') AS message;
        END IF;
        
        -- 创建新索引（如果不存在）
        IF new_index_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', db_name, '.', table_name, 
                ' ADD UNIQUE KEY uk_order_sku (order_id, sku_id) COMMENT ''状态机唯一索引：同一订单同一SKU只能有一条记录，用 transaction_type 做状态机''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('✓ 数据库 ', db_name, ' 表 ', table_name, ': 创建新唯一索引 uk_order_sku 成功') AS message;
        ELSE
            SELECT CONCAT('⚠ 数据库 ', db_name, ' 表 ', table_name, ': 新唯一索引 uk_order_sku 已存在，跳过') AS message;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 对每个分片数据库执行
CALL fix_inventory_transactions_concurrent_issue('jiaoyi_product_0', 'inventory_transactions_');
CALL fix_inventory_transactions_concurrent_issue('jiaoyi_product_1', 'inventory_transactions_');
CALL fix_inventory_transactions_concurrent_issue('jiaoyi_product_2', 'inventory_transactions_');

DROP PROCEDURE IF EXISTS fix_inventory_transactions_concurrent_issue;
