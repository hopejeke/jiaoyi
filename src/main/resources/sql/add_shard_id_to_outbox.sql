-- 为outbox表添加shard_id列
-- 1. 添加shard_id列（允许NULL，后续更新）
ALTER TABLE outbox 
ADD COLUMN shard_id INT NULL COMMENT '分片ID（用于分片处理）' AFTER id;

-- 2. 为现有数据计算并设置shard_id（假设shardCount=10）
UPDATE outbox 
SET shard_id = (id % 10) 
WHERE shard_id IS NULL;

-- 3. 将shard_id设置为NOT NULL
ALTER TABLE outbox 
MODIFY COLUMN shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）';

-- 4. 添加索引
ALTER TABLE outbox 
ADD INDEX idx_shard_id (shard_id),
ADD INDEX idx_shard_id_status (shard_id, status);

