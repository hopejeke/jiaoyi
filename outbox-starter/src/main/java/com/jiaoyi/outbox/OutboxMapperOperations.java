package com.jiaoyi.outbox;

import com.jiaoyi.outbox.entity.Outbox;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Mapper 操作接口（通用接口，不标注 @Mapper）
 * starter 内置的 OutboxMapper 实现此接口
 */
public interface OutboxMapperOperations {
    
    /**
     * 插入outbox记录
     * 
     * @param table 表名（动态表名，防 SQL 注入已在校验层处理）
     * @param outbox outbox 记录
     */
    int insert(String table, Outbox outbox);
    
    /**
     * 根据ID查询
     * 
     * @param table 表名
     * @param id 任务ID
     */
    Outbox selectById(String table, Long id);
    
    /**
     * 查询待处理的任务（NEW 或 FAILED 且 next_retry_time <= now）
     * 根据分片ID列表查询数据
     * 
     * @param table 表名（动态表名）
     * @param shardIds 当前节点负责的分片ID列表
     * @param now 当前时间
     * @param limit 查询数量限制
     * @return 待处理的任务列表
     */
    List<Outbox> selectCandidatesByShard(String table,
                                         List<Integer> shardIds,
                                         LocalDateTime now,
                                         int limit);
    
    /**
     * 查询待处理的任务（NEW 或 FAILED 且 next_retry_time <= now）
     * 按单个分片ID查询（用于分库扫描，避免跨库广播）
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（单个）
     * @param now 当前时间
     * @param limit 查询数量限制
     * @return 待处理的任务列表
     */
    List<Outbox> selectCandidatesBySingleShard(String table,
                                                Integer shardId,
                                                LocalDateTime now,
                                                int limit);
    
    /**
     * CAS 抢锁：将状态从 NEW/FAILED 改为 PROCESSING
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockOwner 锁持有者（实例ID）
     * @param now 当前时间
     * @return 更新的行数（1 表示抢锁成功，0 表示失败）
     */
    int tryLock(String table, Long id, String lockOwner, LocalDateTime now);
    
    /**
     * 标记为成功
     * 
     * @param table 表名（动态表名）
     */
    int markSuccess(String table, Long id, String lockOwner, LocalDateTime completedAt);
    
    /**
     * 标记为失败（可重试）
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockedBy 锁持有者（用于验证）
     * @param retryCount 重试次数
     * @param nextRetryTime 下次重试时间
     * @param lastError 错误信息
     * @return 更新的行数
     */
    int markFailed(String table, Long id, String lockedBy, Integer retryCount,
                   LocalDateTime nextRetryTime, String lastError);
    
    /**
     * 标记为死信（超过最大重试次数）
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockedBy 锁持有者（用于验证）
     * @param lastError 错误信息
     * @return 更新的行数
     */
    int markDead(String table, Long id, String lockedBy, String lastError);
    
    /**
     * 恢复卡死的任务（PROCESSING 状态超过指定时间）
     * 
     * @deprecated 请使用 recoverStuckByShard 方法，按分片恢复避免跨库广播
     * @param table 表名（动态表名）
     */
    @Deprecated
    int recoverStuck(String table, LocalDateTime now, LocalDateTime stuckThreshold);
    
    /**
     * 按分片恢复卡死的任务（PROCESSING 状态且 lock_until 已过期）
     * 
     * 将锁已过期的 PROCESSING 任务恢复为 FAILED，进入统一的指数退避重试流程。
     * 这样避免慢任务被误判为卡死导致并发重复执行，也能控制重试节奏。
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由，避免跨库广播）
     * @param now 当前时间
     * @return 恢复的任务数量
     */
    int recoverStuckByShard(String table, Integer shardId, LocalDateTime now);
    
    /**
     * 更新shard_id
     * 
     * @param table 表名（动态表名）
     */
    int updateShardId(String table, Long id, Integer shardId);
    
    /**
     * 释放锁：将状态从 PROCESSING 改回 NEW，清除 lock_owner
     * 用于当前服务不支持该任务类型时，释放锁让其他服务处理
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockOwner 锁持有者（用于验证）
     * @return 更新的行数（1 表示释放成功，0 表示失败）
     */
    int releaseLock(String table, Long id, String lockOwner);
    
    /**
     * 续租锁：延长 lock_until 时间（用于慢任务避免锁过期）
     * 
     * 如果 handler 执行时间可能超过 lock_timeout，可以在执行过程中定期调用此方法续租。
     * 例如：每隔 10 秒调用一次，将 lock_until 延长到 NOW() + 30秒。
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockedBy 锁持有者（用于验证）
     * @param newLockUntil 新的锁过期时间
     * @param now 当前时间
     * @return 更新的行数（1 表示续租成功，0 表示失败）
     */
    int extendLock(String table, Long id, String lockedBy, LocalDateTime newLockUntil, LocalDateTime now);
    
    /**
     * 抢占式 claim：批量更新状态为 PROCESSING，并设置锁信息（按 shard_id 过滤，避免跨库广播）
     * 
     * @deprecated 已改为两段式 claim（使用 FOR UPDATE SKIP LOCKED），请使用 claimAndLoad 方法
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由）
     * @param lockedBy 锁持有者（实例ID）
     * @param lockUntil 锁过期时间
     * @param now 当前时间
     * @param limit 批量数量
     * @return 更新的行数（成功 claim 的任务数量）
     */
    @Deprecated
    int claim(String table, Integer shardId, String lockedBy, LocalDateTime lockUntil, LocalDateTime now, int limit);
    
    /**
     * 查询已 claim 的任务（用于发送，按 shard_id 过滤，避免跨库广播）
     * 
     * @deprecated 已改为两段式 claim，请使用 claimAndLoad 方法
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由）
     * @param lockedBy 锁持有者（实例ID）
     * @param now 当前时间
     * @param limit 查询数量限制
     * @return 已 claim 的任务列表
     */
    @Deprecated
    List<Outbox> selectClaimed(String table, Integer shardId, String lockedBy, LocalDateTime now, int limit);
    
    /**
     * 两段式 claim：使用 FOR UPDATE SKIP LOCKED 避免多实例并发锁等待
     * 
     * 第一步：SELECT id ... FOR UPDATE SKIP LOCKED（跳过被其他实例锁住的行）
     * 第二步：UPDATE ... WHERE id IN (...)
     * 第三步：SELECT * ... WHERE id IN (...) 返回完整数据
     * 
     * 优势：
     * - 多实例并发时不阻塞（跳过别人锁住的行）
     * - 各自 claim 不同任务，提高吞吐量
     * - 避免 UPDATE ... ORDER BY ... LIMIT 的锁等待问题
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由）
     * @param lockedBy 锁持有者（实例ID）
     * @param lockUntil 锁过期时间
     * @param now 当前时间
     * @param limit 批量数量
     * @return 已 claim 的任务列表（已更新为 PROCESSING 状态）
     */
    List<Outbox> claimAndLoad(String table, Integer shardId, String lockedBy, 
                              LocalDateTime lockUntil, LocalDateTime now, int limit);
    
    /**
     * 第一步：SELECT id ... FOR UPDATE SKIP LOCKED（跳过被其他实例锁住的行）
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由）
     * @param now 当前时间
     * @param limit 查询数量限制
     * @return 待 claim 的任务 ID 列表
     */
    List<Long> selectIdsForClaim(String table, Integer shardId, LocalDateTime now, int limit);
    
    /**
     * 第二步：UPDATE ... WHERE id IN (...) AND shard_id = #{shardId}
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由，必须传入）
     * @param ids 任务 ID 列表
     * @param lockedBy 锁持有者（实例ID）
     * @param lockUntil 锁过期时间
     * @param now 当前时间
     * @return 更新的行数
     */
    int claimByIds(String table, Integer shardId, List<Long> ids, String lockedBy, LocalDateTime lockUntil, LocalDateTime now);
    
    /**
     * 第三步：SELECT * ... WHERE id IN (...) AND shard_id = #{shardId} 返回完整数据
     * 
     * @param table 表名（动态表名）
     * @param shardId 分片ID（用于分库路由，必须传入）
     * @param ids 任务 ID 列表
     * @return 任务列表
     */
    List<Outbox> selectByIds(String table, Integer shardId, List<Long> ids);
    
    /**
     * 标记为已发送（SENT）
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @param lockedBy 锁持有者（用于验证）
     * @return 更新的行数
     */
    int markSent(String table, Long id, String lockedBy);
    
    /**
     * 查询死信列表
     * 
     * @param table 表名（动态表名）
     * @param type 任务类型（可选）
     * @param bizKey 业务键（可选）
     * @param offset 偏移量
     * @param limit 查询数量限制
     * @return 死信任务列表
     */
    List<Outbox> selectDeadLetters(String table, String type, String bizKey, int offset, int limit);
    
    /**
     * 按业务键和类型查询死信任务
     * 
     * @param table 表名（动态表名）
     * @param bizKey 业务键
     * @param type 任务类型（可选）
     * @return 死信任务列表
     */
    List<Outbox> selectDeadByBizKeyAndType(String table, String bizKey, String type);
    
    /**
     * 重置死信任务为 NEW 状态（用于手动重放）
     * 将 DEAD -> NEW，重置 retry_count、next_retry_time、lock 信息
     * 
     * @param table 表名（动态表名）
     * @param id 任务ID
     * @return 更新的行数
     */
    int resetDeadToNew(String table, Long id);
    
    /**
     * 删除 SENT 状态的记录（按 shard_id 范围批量删除）
     * 
     * @param table 表名（动态表名）
     * @param startShardId 起始分片ID（包含）
     * @param endShardId 结束分片ID（不包含）
     * @param cutoffTime 截止时间（created_at < cutoffTime 的记录将被删除）
     * @return 删除的记录数
     */
    int deleteSentRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime);
    
    /**
     * 删除 DEAD 状态的记录（按 shard_id 范围批量删除）
     * 
     * @param table 表名（动态表名）
     * @param startShardId 起始分片ID（包含）
     * @param endShardId 结束分片ID（不包含）
     * @param cutoffTime 截止时间（created_at < cutoffTime 的记录将被删除）
     * @return 删除的记录数
     */
    int deleteDeadRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime);
}

