# Outbox 多实例防重复机制

## 概述

当前项目的 outbox 模式使用 **两段式 claim + FOR UPDATE SKIP LOCKED** 机制，确保多个实例并发时不会拿到同一个任务。

## 核心机制

### 1. FOR UPDATE SKIP LOCKED（MySQL 8.0.4+）

这是 MySQL 的行锁机制，在 SELECT 时：
- `FOR UPDATE`：对查询到的行加排他锁
- `SKIP LOCKED`：跳过已经被其他事务锁住的行，不等待

**优势：**
- 多实例并发时不阻塞（跳过别人锁住的行）
- 各自 claim 不同任务，提高吞吐量
- 避免 UPDATE ... ORDER BY ... LIMIT 的锁等待问题

### 2. 两段式 Claim 流程

在同一个事务内执行三步：

#### 第一步：SELECT id ... FOR UPDATE SKIP LOCKED

```sql
SELECT id FROM outbox
WHERE shard_id = ?
  AND status IN ('NEW', 'FAILED')
  AND (next_retry_time IS NULL OR next_retry_time <= ?)
  AND (lock_until IS NULL OR lock_until < ?)
ORDER BY id ASC LIMIT ?
FOR UPDATE SKIP LOCKED
```

**作用：**
- 查询待处理的任务ID列表
- 只查询未被其他实例锁住的任务（SKIP LOCKED）
- 如果任务已被其他实例锁定，直接跳过，不等待

#### 第二步：UPDATE ... WHERE id IN (...)

```sql
UPDATE outbox
SET status = 'PROCESSING',
    lock_owner = ?,      -- 实例ID
    lock_time = ?,       -- 锁定时间
    lock_until = ?,      -- 锁过期时间
    updated_at = ?
WHERE shard_id = ?
  AND id IN (...)
  AND status IN ('NEW', 'FAILED')
  AND (next_retry_time IS NULL OR next_retry_time <= ?)
  AND (lock_until IS NULL OR lock_until < ?)
```

**作用：**
- 原子性地更新任务状态为 PROCESSING
- 设置 `lock_owner`（实例ID）和 `lock_until`（锁过期时间）
- WHERE 条件再次检查状态和锁条件，确保原子性

**关键点：**
- UPDATE 的 WHERE 条件包含状态检查，确保只有符合条件的任务才会被更新
- 如果任务在第一步和第二步之间被其他实例 claim，UPDATE 会返回 0（没有行被更新）

#### 第三步：SELECT * ... WHERE id IN (...)

```sql
SELECT * FROM outbox
WHERE shard_id = ?
  AND id IN (...)
```

**作用：**
- 返回已 claim 的任务完整数据
- 用于后续处理

## 代码实现

### OutboxClaimService.java

```java
@Transactional(transactionManager = "shardingTransactionManager")
public List<Outbox> claimAndLoad(String table, Integer shardId, String lockedBy,
                                 LocalDateTime lockUntil, LocalDateTime now, int limit) {
    // 第一步：SELECT id ... FOR UPDATE SKIP LOCKED
    // ⚠️ 注意：FOR UPDATE 获取的行锁会一直持有到事务提交（方法返回）
    List<Long> ids = outboxRepository.selectIdsForClaim(table, shardId, now, limit);
    
    if (ids.isEmpty()) {
        return new ArrayList<>();
    }
    
    // 第二步：UPDATE ... WHERE id IN (...)
    // ⚠️ 此时 FOR UPDATE 的锁仍然持有，确保其他实例无法 claim 这些任务
    int updated = outboxRepository.claimByIds(table, shardId, ids, lockedBy, lockUntil, now);
    if (updated != ids.size()) {
        log.warn("claim 更新的行数 {} 与查询到的 ID 数量 {} 不一致", updated, ids.size());
    }
    
    // 第三步：SELECT * ... WHERE id IN (...)
    // ⚠️ 锁仍然持有
    List<Outbox> tasks = outboxRepository.selectByIds(table, shardId, ids);
    
    // ⚠️ 方法返回时，Spring 事务管理器提交事务，FOR UPDATE 的锁释放
    return tasks;
}
```

**重要：锁的生命周期**

- `FOR UPDATE` 获取的行锁会在**事务提交或回滚时释放**
- 由于整个方法在 `@Transactional` 注解的事务内：
  - 第一步 SELECT FOR UPDATE 获取锁
  - 第二步 UPDATE 执行时，锁仍然持有（确保原子性）
  - 第三步 SELECT 执行时，锁仍然持有
  - **方法正常返回时**：Spring 事务管理器提交事务，锁释放
  - **方法抛出异常时**：Spring 事务管理器回滚事务，锁释放

**这样设计的好处：**
- ✅ 确保在 SELECT 和 UPDATE 之间，其他实例无法 claim 这些任务
- ✅ 如果 UPDATE 失败，事务回滚，锁释放，任务状态不变
- ✅ 如果 UPDATE 成功，事务提交，锁释放，任务状态已更新为 PROCESSING

### JdbcOutboxRepository.java

#### selectIdsForClaim（第一步）

```java
public List<Long> selectIdsForClaim(String table, Integer shardId, LocalDateTime now, int limit) {
    String sql = "SELECT id FROM " + table +
            " WHERE shard_id = ?" +
            " AND status IN ('NEW', 'FAILED')" +
            " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
            " AND (lock_until IS NULL OR lock_until < ?)" +
            " ORDER BY id ASC LIMIT ?" +
            " FOR UPDATE SKIP LOCKED";  // 关键：跳过被锁住的行
    
    return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), 
            shardId, Timestamp.valueOf(now), Timestamp.valueOf(now), limit);
}
```

#### claimByIds（第二步）

```java
public int claimByIds(String table, Integer shardId, List<Long> ids, String lockedBy, 
                     LocalDateTime lockUntil, LocalDateTime now) {
    String sql = "UPDATE " + table +
            " SET status = 'PROCESSING'," +
            " lock_owner = ?, lock_time = ?, lock_until = ?, updated_at = ?" +
            " WHERE shard_id = ?" +
            " AND id IN (" + placeholders + ")" +
            " AND status IN ('NEW', 'FAILED')" +  // 再次检查状态
            " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
            " AND (lock_until IS NULL OR lock_until < ?)";  // 再次检查锁条件
    
    return jdbcTemplate.update(sql, params.toArray());
}
```

## 防重复保证

### 1. 数据库层面的原子性

- **FOR UPDATE SKIP LOCKED**：确保 SELECT 时只查询未被锁住的行
- **UPDATE WHERE 条件**：再次检查状态和锁条件，确保原子性
- **事务隔离**：整个 claim 过程在同一个事务内，保证原子性

### 2. 锁机制

- **lock_owner**：记录哪个实例持有锁（实例ID）
- **lock_until**：锁过期时间（默认30秒）
- **lock_time**：锁定时间

### 3. 状态检查

- 只处理 `status IN ('NEW', 'FAILED')` 的任务
- UPDATE 时再次检查状态，确保任务状态未变

## 并发场景示例

### 场景：两个实例同时 claim 任务

**时间线：**

1. **T1**：实例A执行 `SELECT id ... FOR UPDATE SKIP LOCKED`
   - 查询到任务 ID=1, 2, 3
   - 对这三行加锁

2. **T2**：实例B执行 `SELECT id ... FOR UPDATE SKIP LOCKED`
   - 任务 ID=1, 2, 3 已被实例A锁住
   - **SKIP LOCKED** 跳过这三行
   - 查询到任务 ID=4, 5, 6
   - 对这三行加锁

3. **T3**：实例A执行 `UPDATE ... WHERE id IN (1,2,3)`
   - 成功更新，设置 `lock_owner = 'instance-A'`

4. **T4**：实例B执行 `UPDATE ... WHERE id IN (4,5,6)`
   - 成功更新，设置 `lock_owner = 'instance-B'`

**结果：**
- 实例A拿到任务 1, 2, 3
- 实例B拿到任务 4, 5, 6
- **没有重复！**

## 锁过期机制

如果任务处理超时（超过 `lock_until`），其他实例可以重新 claim：

```sql
-- 查询条件包含锁过期检查
AND (lock_until IS NULL OR lock_until < ?)
```

**场景：**
- 实例A claim 任务后，处理时间超过30秒
- `lock_until` 过期
- 实例B可以重新 claim 该任务

## 索引优化

为了支持高效的 claim 查询，创建了专门的索引：

```sql
CREATE INDEX idx_claim ON outbox(shard_id, status, next_retry_time, lock_until, id);
```

**索引覆盖：**
- `shard_id`：分片路由
- `status`：状态过滤
- `next_retry_time`：重试时间过滤
- `lock_until`：锁过期时间过滤
- `id`：排序和主键

## 总结

当前项目的 outbox 模式通过以下机制防止多个实例拿到同一个任务：

1. ✅ **FOR UPDATE SKIP LOCKED**：跳过被锁住的行，不等待
2. ✅ **两段式 claim**：SELECT → UPDATE → SELECT，在事务内保证原子性
3. ✅ **UPDATE WHERE 条件**：再次检查状态和锁条件，确保原子性
4. ✅ **锁机制**：`lock_owner` + `lock_until`，记录锁持有者和过期时间
5. ✅ **索引优化**：`idx_claim` 索引覆盖所有查询条件

**优势：**
- 多实例并发时不阻塞
- 各自 claim 不同任务，提高吞吐量
- 避免锁等待，提高性能
- 支持锁过期，防止任务卡死

