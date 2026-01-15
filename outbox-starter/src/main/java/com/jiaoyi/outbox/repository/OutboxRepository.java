package com.jiaoyi.outbox.repository;

import com.jiaoyi.outbox.entity.Outbox;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 数据访问接口（通用接口，不依赖 MyBatis）
 * 默认使用 JdbcTemplate 实现，可选 MyBatis 覆盖
 */
public interface OutboxRepository {
    
    /**
     * 插入outbox记录
     * 
     * @param table 表名（动态表名，防 SQL 注入已在校验层处理）
     * @param outbox outbox 记录
     * @return 插入的记录ID（通过 outbox.id 返回）
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
     * 查询待 claim 的任务ID列表（两段式 claim 第一步）
     * 使用 FOR UPDATE SKIP LOCKED 避免锁竞争
     * 
     * @param table 表名
     * @param shardId 分片ID
     * @param now 当前时间
     * @param limit 查询数量限制
     * @return 待 claim 的任务ID列表
     */
    List<Long> selectIdsForClaim(String table, Integer shardId, LocalDateTime now, int limit);
    
    /**
     * 批量 claim 任务（两段式 claim 第二步）
     * 
     * @param table 表名
     * @param shardId 分片ID
     * @param ids 任务ID列表
     * @param lockedBy 锁持有者（实例ID）
     * @param lockUntil 锁过期时间
     * @param now 当前时间
     * @return 更新的行数
     */
    int claimByIds(String table, Integer shardId, List<Long> ids, String lockedBy, 
                   LocalDateTime lockUntil, LocalDateTime now);
    
    /**
     * 根据ID列表查询任务（两段式 claim 第三步）
     * 
     * @param table 表名
     * @param shardId 分片ID
     * @param ids 任务ID列表
     * @return 任务列表
     */
    List<Outbox> selectByIds(String table, Integer shardId, List<Long> ids);
    
    /**
     * 标记任务为已发送（SENT）
     * 
     * @param table 表名
     * @param id 任务ID
     * @param lockedBy 锁持有者（实例ID）
     * @return 更新的行数
     */
    int markSent(String table, Long id, String lockedBy);
    
    /**
     * 标记任务为失败（FAILED）
     * 
     * @param table 表名
     * @param id 任务ID
     * @param lockedBy 锁持有者（实例ID）
     * @param retryCount 重试次数
     * @param nextRetryTime 下次重试时间
     * @param errorMessage 错误信息
     * @return 更新的行数
     */
    int markFailed(String table, Long id, String lockedBy, int retryCount, 
                  LocalDateTime nextRetryTime, String errorMessage);
    
    /**
     * 标记任务为死信（DEAD）
     * 
     * @param table 表名
     * @param id 任务ID
     * @param lockedBy 锁持有者（实例ID）
     * @param errorMessage 错误信息
     * @return 更新的行数
     */
    int markDead(String table, Long id, String lockedBy, String errorMessage);
    
    /**
     * 释放锁（将任务状态从 PROCESSING 恢复为 NEW）
     * 
     * @param table 表名
     * @param id 任务ID
     * @param lockedBy 锁持有者（实例ID）
     * @return 更新的行数
     */
    int releaseLock(String table, Long id, String lockedBy);
    
    /**
     * 恢复卡死任务（将锁已过期的 PROCESSING 任务恢复为 FAILED）
     * 广播查询所有表（用于维护性任务，可接受广播）
     * 
     * @param table 表名
     * @param now 当前时间
     * @return 恢复的任务数量
     */
    int recoverStuck(String table, LocalDateTime now);
    
    /**
     * 恢复卡死任务（将锁已过期的 PROCESSING 任务恢复为 FAILED）
     * 按分片查询（已废弃，因为 outbox 表已改为按 store_id 分片）
     * 
     * @deprecated 请使用 recoverStuck(String, LocalDateTime) 方法
     * @param table 表名
     * @param shardId 分片ID
     * @param now 当前时间
     * @return 恢复的任务数量
     */
    @Deprecated
    int recoverStuckByShard(String table, Integer shardId, LocalDateTime now);
    
    /**
     * 延长锁时间（用于长时间运行的任务）
     * 
     * @param table 表名
     * @param id 任务ID
     * @param lockedBy 锁持有者（实例ID）
     * @param newLockUntil 新的锁过期时间
     * @param now 当前时间
     * @return 更新的行数
     */
    int extendLock(String table, Long id, String lockedBy, LocalDateTime newLockUntil, LocalDateTime now);
    
    /**
     * 重置死信任务为 NEW（用于手动重试）
     * 
     * @param table 表名
     * @param id 任务ID
     * @return 更新的行数
     */
    int resetDeadToNew(String table, Long id);
    
    /**
     * 删除 SENT 状态的记录（按 shard_id 范围批量删除）
     * 
     * @param table 表名
     * @param startShardId 起始分片ID（包含）
     * @param endShardId 结束分片ID（不包含）
     * @param cutoffTime 截止时间（created_at < cutoffTime 的记录将被删除）
     * @return 删除的记录数
     */
    int deleteSentRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime);
    
    /**
     * 删除 DEAD 状态的记录（按 shard_id 范围批量删除）
     * 
     * @param table 表名
     * @param startShardId 起始分片ID（包含）
     * @param endShardId 结束分片ID（不包含）
     * @param cutoffTime 截止时间（created_at < cutoffTime 的记录将被删除）
     * @return 删除的记录数
     */
    int deleteDeadRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime);
    
    /**
     * 查询死信列表
     * 
     * @param table 表名
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
     * @param table 表名
     * @param bizKey 业务键
     * @param type 任务类型（可选）
     * @return 死信任务列表
     */
    List<Outbox> selectDeadByBizKeyAndType(String table, String bizKey, String type);
}

