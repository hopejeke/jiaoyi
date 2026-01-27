-- Outbox 表优化索引（用于两段式 claim，降低扫表成本）
-- MySQL 8.0.4+ 支持 FOR UPDATE SKIP LOCKED

-- 索引说明：
-- 1. shard_id：分片路由（避免跨库广播）
-- 2. status：状态过滤（NEW, FAILED）
-- 3. next_retry_time：重试时间过滤
-- 4. lock_until：锁过期时间过滤
-- 5. id：排序字段（ORDER BY id ASC）

-- 为每个分片库的 outbox 表添加索引
-- jiaoyi_0.outbox
CREATE INDEX idx_claim ON jiaoyi_0.outbox(shard_id, status, next_retry_time, lock_until, id);

-- jiaoyi_1.outbox
CREATE INDEX idx_claim ON jiaoyi_1.outbox(shard_id, status, next_retry_time, lock_until, id);

-- jiaoyi_2.outbox
CREATE INDEX idx_claim ON jiaoyi_2.outbox(shard_id, status, next_retry_time, lock_until, id);

-- 注意：
-- 1. 如果索引已存在，会报错，可以忽略或先删除：DROP INDEX idx_claim ON jiaoyi_0.outbox;
-- 2. 索引顺序很重要：shard_id 在最前面（用于分库路由），status 和 next_retry_time 用于过滤，id 用于排序
-- 3. 这个索引覆盖了 claim 查询的所有条件，可以显著提高查询性能




