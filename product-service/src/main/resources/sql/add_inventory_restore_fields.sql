-- ============================================
-- 为库存表添加自动恢复功能字段
-- 对应PRD需求：定时自动恢复库存
-- ============================================
-- 说明：
-- 1. 此脚本为所有inventory分片表添加恢复相关字段
-- 2. 支持3库×3表=9张表的场景（inventory_0, inventory_1, inventory_2）
-- 3. 如果已迁移到32张表（inventory_00..inventory_31），请使用循环方式执行
-- ============================================

-- ============================================
-- 方案A：如果是9张表（3个数据库，每个数据库3张表）
-- ============================================

-- jiaoyi_product_0 数据库
USE jiaoyi_product_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_inventory_restore_fields()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_count INT DEFAULT 0;

    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);

        -- 检查restore_mode字段是否存在
        SELECT COUNT(*) INTO column_count
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'restore_mode';

        IF column_count = 0 THEN
            -- 添加恢复相关字段
            SET @sql = CONCAT('
                ALTER TABLE ', @table_name, '
                ADD COLUMN restore_mode VARCHAR(20) DEFAULT ''MANUAL'' COMMENT ''恢复模式：MANUAL(手动)/TOMORROW(次日自动)/SCHEDULED(指定时间)'',
                ADD COLUMN restore_time DATETIME DEFAULT NULL COMMENT ''恢复时间（指定时间模式）'',
                ADD COLUMN restore_stock INT DEFAULT NULL COMMENT ''恢复后的库存数量'',
                ADD COLUMN last_restore_time DATETIME DEFAULT NULL COMMENT ''上次恢复时间'',
                ADD COLUMN restore_enabled BOOLEAN DEFAULT FALSE COMMENT ''是否启用自动恢复''
            ');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            -- 添加索引，方便定时任务查询
            SET @sql = CONCAT('CREATE INDEX idx_restore_enabled_time ON ', @table_name, '(restore_enabled, restore_time)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL add_inventory_restore_fields();
DROP PROCEDURE IF EXISTS add_inventory_restore_fields;

-- jiaoyi_product_1 数据库
USE jiaoyi_product_1;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_inventory_restore_fields()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_count INT DEFAULT 0;

    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);

        -- 检查restore_mode字段是否存在
        SELECT COUNT(*) INTO column_count
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_1'
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'restore_mode';

        IF column_count = 0 THEN
            -- 添加恢复相关字段
            SET @sql = CONCAT('
                ALTER TABLE ', @table_name, '
                ADD COLUMN restore_mode VARCHAR(20) DEFAULT ''MANUAL'' COMMENT ''恢复模式：MANUAL(手动)/TOMORROW(次日自动)/SCHEDULED(指定时间)'',
                ADD COLUMN restore_time DATETIME DEFAULT NULL COMMENT ''恢复时间（指定时间模式）'',
                ADD COLUMN restore_stock INT DEFAULT NULL COMMENT ''恢复后的库存数量'',
                ADD COLUMN last_restore_time DATETIME DEFAULT NULL COMMENT ''上次恢复时间'',
                ADD COLUMN restore_enabled BOOLEAN DEFAULT FALSE COMMENT ''是否启用自动恢复''
            ');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            -- 添加索引
            SET @sql = CONCAT('CREATE INDEX idx_restore_enabled_time ON ', @table_name, '(restore_enabled, restore_time)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL add_inventory_restore_fields();
DROP PROCEDURE IF EXISTS add_inventory_restore_fields;

-- jiaoyi_product_2 数据库
USE jiaoyi_product_2;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_inventory_restore_fields()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_count INT DEFAULT 0;

    WHILE i < 3 DO
        SET @table_name = CONCAT('inventory_', i);

        -- 检查restore_mode字段是否存在
        SELECT COUNT(*) INTO column_count
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_2'
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'restore_mode';

        IF column_count = 0 THEN
            -- 添加恢复相关字段
            SET @sql = CONCAT('
                ALTER TABLE ', @table_name, '
                ADD COLUMN restore_mode VARCHAR(20) DEFAULT ''MANUAL'' COMMENT ''恢复模式：MANUAL(手动)/TOMORROW(次日自动)/SCHEDULED(指定时间)'',
                ADD COLUMN restore_time DATETIME DEFAULT NULL COMMENT ''恢复时间（指定时间模式）'',
                ADD COLUMN restore_stock INT DEFAULT NULL COMMENT ''恢复后的库存数量'',
                ADD COLUMN last_restore_time DATETIME DEFAULT NULL COMMENT ''上次恢复时间'',
                ADD COLUMN restore_enabled BOOLEAN DEFAULT FALSE COMMENT ''是否启用自动恢复''
            ');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            -- 添加索引
            SET @sql = CONCAT('CREATE INDEX idx_restore_enabled_time ON ', @table_name, '(restore_enabled, restore_time)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL add_inventory_restore_fields();
DROP PROCEDURE IF EXISTS add_inventory_restore_fields;

-- ============================================
-- 方案B：如果已迁移到32张表（inventory_00..inventory_31）
-- ============================================
-- 取消下面的注释并执行

/*
USE jiaoyi_product_0;

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_inventory_restore_fields_32()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE column_count INT DEFAULT 0;

    WHILE i < 32 DO
        SET @table_name = CONCAT('inventory_', LPAD(i, 2, '0'));

        -- 检查restore_mode字段是否存在
        SELECT COUNT(*) INTO column_count
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'jiaoyi_product_0'
          AND TABLE_NAME = @table_name
          AND COLUMN_NAME = 'restore_mode';

        IF column_count = 0 THEN
            SET @sql = CONCAT('
                ALTER TABLE ', @table_name, '
                ADD COLUMN restore_mode VARCHAR(20) DEFAULT ''MANUAL'' COMMENT ''恢复模式：MANUAL(手动)/TOMORROW(次日自动)/SCHEDULED(指定时间)'',
                ADD COLUMN restore_time DATETIME DEFAULT NULL COMMENT ''恢复时间（指定时间模式）'',
                ADD COLUMN restore_stock INT DEFAULT NULL COMMENT ''恢复后的库存数量'',
                ADD COLUMN last_restore_time DATETIME DEFAULT NULL COMMENT ''上次恢复时间'',
                ADD COLUMN restore_enabled BOOLEAN DEFAULT FALSE COMMENT ''是否启用自动恢复''
            ');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            SET @sql = CONCAT('CREATE INDEX idx_restore_enabled_time ON ', @table_name, '(restore_enabled, restore_time)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL add_inventory_restore_fields_32();
DROP PROCEDURE IF EXISTS add_inventory_restore_fields_32;

-- 对jiaoyi_product_1和jiaoyi_product_2执行相同操作
*/

-- ============================================
-- 字段说明
-- ============================================
-- restore_mode:
--   MANUAL - 手动恢复（默认），不会自动恢复
--   TOMORROW - 次日自动恢复，每天凌晨0点自动恢复库存
--   SCHEDULED - 指定时间恢复，到达restore_time时自动恢复

-- restore_time:
--   TOMORROW模式：NULL（每天凌晨0点执行）
--   SCHEDULED模式：具体的恢复时间，如 '2025-02-10 18:00:00'

-- restore_stock:
--   恢复后的库存数量，NULL表示恢复为不限库存（UNLIMITED模式）

-- restore_enabled:
--   是否启用自动恢复，TRUE时定时任务会处理

-- last_restore_time:
--   记录上次恢复时间，避免重复恢复
