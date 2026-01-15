-- ============================================
-- 为所有订单域表添加 store_id 字段
-- ============================================
-- 说明：
-- 1. 所有订单事实表都需要添加 store_id 字段（BIGINT NOT NULL）
-- 2. store_id 用于分片，与商品服务保持一致
-- 3. 执行前请确保已备份数据
-- ============================================

-- ============================================
-- 步骤 1：为 orders 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_orders$$
CREATE PROCEDURE add_store_id_to_orders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        
        -- 检查列是否存在
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        -- 如果列不存在，则添加
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_orders();
DROP PROCEDURE IF EXISTS add_store_id_to_orders;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_orders$$
CREATE PROCEDURE add_store_id_to_orders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_orders();
DROP PROCEDURE IF EXISTS add_store_id_to_orders;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_orders$$
CREATE PROCEDURE add_store_id_to_orders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('orders_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_orders();
DROP PROCEDURE IF EXISTS add_store_id_to_orders;

-- ============================================
-- 步骤 2：为 order_items 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_items$$
CREATE PROCEDURE add_store_id_to_order_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_items();
DROP PROCEDURE IF EXISTS add_store_id_to_order_items;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_items$$
CREATE PROCEDURE add_store_id_to_order_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_items();
DROP PROCEDURE IF EXISTS add_store_id_to_order_items;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_items$$
CREATE PROCEDURE add_store_id_to_order_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_items();
DROP PROCEDURE IF EXISTS add_store_id_to_order_items;

-- ============================================
-- 步骤 3：为 payments 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments$$
CREATE PROCEDURE add_store_id_to_payments()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments();
DROP PROCEDURE IF EXISTS add_store_id_to_payments;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments$$
CREATE PROCEDURE add_store_id_to_payments()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments();
DROP PROCEDURE IF EXISTS add_store_id_to_payments;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_payments$$
CREATE PROCEDURE add_store_id_to_payments()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('payments_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_payments();
DROP PROCEDURE IF EXISTS add_store_id_to_payments;

-- ============================================
-- 步骤 4：为 outbox 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_outbox$$
CREATE PROCEDURE add_store_id_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT COMMENT ''门店ID'' AFTER sharding_key');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_outbox();
DROP PROCEDURE IF EXISTS add_store_id_to_outbox;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_outbox$$
CREATE PROCEDURE add_store_id_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT COMMENT ''门店ID'' AFTER sharding_key');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_outbox();
DROP PROCEDURE IF EXISTS add_store_id_to_outbox;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_outbox$$
CREATE PROCEDURE add_store_id_to_outbox()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('outbox_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT COMMENT ''门店ID'' AFTER sharding_key');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_outbox();
DROP PROCEDURE IF EXISTS add_store_id_to_outbox;

-- ============================================
-- 步骤 5：为 order_coupons 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons$$
CREATE PROCEDURE add_store_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons$$
CREATE PROCEDURE add_store_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons$$
CREATE PROCEDURE add_store_id_to_order_coupons()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('order_coupons_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_order_coupons();
DROP PROCEDURE IF EXISTS add_store_id_to_order_coupons;

-- ============================================
-- 步骤 6：为 refunds 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refunds$$
CREATE PROCEDURE add_store_id_to_refunds()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refunds_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refunds();
DROP PROCEDURE IF EXISTS add_store_id_to_refunds;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refunds$$
CREATE PROCEDURE add_store_id_to_refunds()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refunds_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refunds();
DROP PROCEDURE IF EXISTS add_store_id_to_refunds;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refunds$$
CREATE PROCEDURE add_store_id_to_refunds()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refunds_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refunds();
DROP PROCEDURE IF EXISTS add_store_id_to_refunds;

-- ============================================
-- 步骤 7：为 refund_items 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items$$
CREATE PROCEDURE add_store_id_to_refund_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refund_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refund_items();
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items$$
CREATE PROCEDURE add_store_id_to_refund_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refund_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refund_items();
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items$$
CREATE PROCEDURE add_store_id_to_refund_items()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('refund_items_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_refund_items();
DROP PROCEDURE IF EXISTS add_store_id_to_refund_items;

-- ============================================
-- 步骤 8：为 deliveries 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries$$
CREATE PROCEDURE add_store_id_to_deliveries()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('deliveries_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_deliveries();
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries$$
CREATE PROCEDURE add_store_id_to_deliveries()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('deliveries_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_deliveries();
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries$$
CREATE PROCEDURE add_store_id_to_deliveries()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('deliveries_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_deliveries();
DROP PROCEDURE IF EXISTS add_store_id_to_deliveries;

-- ============================================
-- 步骤 9：为 doordash_retry_task 表添加 store_id 字段
-- ============================================

USE jiaoyi_order_0;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task$$
CREATE PROCEDURE add_store_id_to_doordash_retry_task()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_doordash_retry_task();
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task;

USE jiaoyi_order_1;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task$$
CREATE PROCEDURE add_store_id_to_doordash_retry_task()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_doordash_retry_task();
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task;

USE jiaoyi_order_2;
DELIMITER $$
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task$$
CREATE PROCEDURE add_store_id_to_doordash_retry_task()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    WHILE i < 32 DO
        SET @table_name = CONCAT('doordash_retry_task_', LPAD(i, 2, '0'));
        
        SELECT COUNT(*) INTO column_exists
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'store_id';
        
        IF column_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', @table_name, ' ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0 COMMENT ''门店ID'' AFTER merchant_id');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL add_store_id_to_doordash_retry_task();
DROP PROCEDURE IF EXISTS add_store_id_to_doordash_retry_task;

-- ============================================
-- 完成
-- ============================================
-- 注意：
-- 1. 执行完此脚本后，需要更新业务代码，确保新创建的数据都设置了 store_id
-- 2. 对于历史数据，store_id 默认为 0，需要根据业务逻辑进行数据迁移
-- 3. 建议在业务低峰期执行此脚本
-- 
-- 已添加 store_id 字段的表：
-- - orders (32张表/库 × 3库 = 96张表)
-- - order_items (32张表/库 × 3库 = 96张表)
-- - payments (32张表/库 × 3库 = 96张表)
-- - order_coupons (32张表/库 × 3库 = 96张表)
-- - refunds (32张表/库 × 3库 = 96张表)
-- - refund_items (32张表/库 × 3库 = 96张表)
-- - deliveries (32张表/库 × 3库 = 96张表)
-- - doordash_retry_task (32张表/库 × 3库 = 96张表)
-- - outbox (32张表/库 × 3库 = 96张表)
-- 总计：864 张表

