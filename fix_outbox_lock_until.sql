-- 为分片库的 outbox 表添加 lock_until 列
-- 请依次在 jiaoyi_0, jiaoyi_1, jiaoyi_2 三个数据库中执行

-- ============================================
-- 方式一：直接执行（如果列已存在会报错，可以忽略）
-- ============================================

-- 数据库 jiaoyi_0
USE jiaoyi_0;
ALTER TABLE outbox ADD COLUMN lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）';

-- 数据库 jiaoyi_1
USE jiaoyi_1;
ALTER TABLE outbox ADD COLUMN lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）';

-- 数据库 jiaoyi_2
USE jiaoyi_2;
ALTER TABLE outbox ADD COLUMN lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）';

-- ============================================
-- 方式二：使用存储过程（更安全，自动检查列是否存在）
-- ============================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_lock_until_column$$

CREATE PROCEDURE add_lock_until_column(IN db_name VARCHAR(64))
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = db_name
      AND TABLE_NAME = 'outbox'
      AND COLUMN_NAME = 'lock_until';
    
    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', db_name, '.outbox ADD COLUMN lock_until DATETIME COMMENT ''锁过期时间（用于抢占式 claim）''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('✓ 数据库 ', db_name, ' 的 outbox 表已添加 lock_until 列') AS result;
    ELSE
        SELECT CONCAT('✓ 数据库 ', db_name, ' 的 outbox 表已存在 lock_until 列，跳过') AS result;
    END IF;
END$$

DELIMITER ;

-- 执行存储过程为三个数据库添加列
CALL add_lock_until_column('jiaoyi_0');
CALL add_lock_until_column('jiaoyi_1');
CALL add_lock_until_column('jiaoyi_2');

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_lock_until_column;

