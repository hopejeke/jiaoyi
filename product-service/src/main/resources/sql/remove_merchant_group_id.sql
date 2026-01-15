-- ============================================
-- 删除 merchant_group_id 字段的 SQL 脚本
-- ============================================
-- 注意：执行此脚本前，请确保已备份数据库
-- MySQL 不支持 DROP COLUMN IF EXISTS，需要先检查字段是否存在

DELIMITER //

-- 存储过程：删除索引（如果存在）
CREATE PROCEDURE IF NOT EXISTS DropIndexIfExists(
    IN dbName VARCHAR(255),
    IN tableName VARCHAR(255),
    IN indexName VARCHAR(255)
)
BEGIN
    DECLARE index_exists INT;
    SELECT COUNT(*)
    INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = dbName
      AND TABLE_NAME = tableName
      AND INDEX_NAME = indexName;

    IF index_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', dbName, '`.`', tableName, '` DROP INDEX `', indexName, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Dropped index ', indexName, ' from table ', dbName, '.', tableName) AS Message;
    ELSE
        SELECT CONCAT('Index ', indexName, ' does not exist in table ', dbName, '.', tableName) AS Message;
    END IF;
END //

-- 存储过程：删除列（如果存在）
CREATE PROCEDURE IF NOT EXISTS DropColumnIfExists(
    IN dbName VARCHAR(255),
    IN tableName VARCHAR(255),
    IN colName VARCHAR(255)
)
BEGIN
    DECLARE column_exists INT;
    SELECT COUNT(*)
    INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = dbName
      AND TABLE_NAME = tableName
      AND COLUMN_NAME = colName;

    IF column_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', dbName, '`.`', tableName, '` DROP COLUMN `', colName, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Dropped column ', colName, ' from table ', dbName, '.', tableName) AS Message;
    ELSE
        SELECT CONCAT('Column ', colName, ' does not exist in table ', dbName, '.', tableName) AS Message;
    END IF;
END //

DELIMITER ;

-- 针对每个数据库的每个分片表执行
CALL DropIndexIfExists('jiaoyi_product_0', 'merchants_0', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_0', 'merchants_0', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_0', 'merchants_1', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_0', 'merchants_1', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_0', 'merchants_2', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_0', 'merchants_2', 'merchant_group_id');

CALL DropIndexIfExists('jiaoyi_product_1', 'merchants_0', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_1', 'merchants_0', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_1', 'merchants_1', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_1', 'merchants_1', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_1', 'merchants_2', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_1', 'merchants_2', 'merchant_group_id');

CALL DropIndexIfExists('jiaoyi_product_2', 'merchants_0', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_2', 'merchants_0', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_2', 'merchants_1', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_2', 'merchants_1', 'merchant_group_id');
CALL DropIndexIfExists('jiaoyi_product_2', 'merchants_2', 'idx_merchant_group_id');
CALL DropColumnIfExists('jiaoyi_product_2', 'merchants_2', 'merchant_group_id');

-- 清理存储过程
DROP PROCEDURE IF EXISTS DropIndexIfExists;
DROP PROCEDURE IF EXISTS DropColumnIfExists;

