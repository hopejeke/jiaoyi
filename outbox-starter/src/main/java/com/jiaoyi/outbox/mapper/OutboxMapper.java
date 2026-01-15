package com.jiaoyi.outbox.mapper;

import com.jiaoyi.outbox.OutboxMapperOperations;
import com.jiaoyi.outbox.entity.Outbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox表Mapper（starter内置）
 * 业务方不需要实现此接口，starter会自动注册
 */
@Mapper
public interface OutboxMapper extends OutboxMapperOperations {
    
    /**
     * 插入outbox记录
     */
    @Override
    int insert(@Param("table") String table, @Param("outbox") Outbox outbox);
    
    /**
     * 根据ID查询
     */
    @Override
    Outbox selectById(@Param("table") String table, @Param("id") Long id);
    
    /**
     * 查询待处理的任务（NEW 或 FAILED 且 next_retry_time <= now）
     * 根据分片ID列表查询数据
     */
    @Override
    List<Outbox> selectCandidatesByShard(@Param("table") String table,
                                         @Param("shardIds") List<Integer> shardIds,
                                         @Param("now") LocalDateTime now,
                                         @Param("limit") int limit);
    
    /**
     * 查询待处理的任务（NEW 或 FAILED 且 next_retry_time <= now）
     * 按单个分片ID查询（用于分库扫描，避免跨库广播）
     */
    @Override
    List<Outbox> selectCandidatesBySingleShard(@Param("table") String table,
                                                @Param("shardId") Integer shardId,
                                                @Param("now") LocalDateTime now,
                                                @Param("limit") int limit);
    
    /**
     * CAS 抢锁：将状态从 NEW/FAILED 改为 PROCESSING
     */
    @Override
    int tryLock(@Param("table") String table,
                @Param("id") Long id,
                @Param("lockOwner") String lockOwner,
                @Param("now") LocalDateTime now);
    
    /**
     * 标记为成功
     */
    @Override
    int markSuccess(@Param("table") String table,
                    @Param("id") Long id,
                    @Param("lockOwner") String lockOwner,
                    @Param("completedAt") LocalDateTime completedAt);
    
    /**
     * 标记为失败（可重试）
     */
    @Override
    int markFailed(@Param("table") String table,
                   @Param("id") Long id,
                   @Param("lockedBy") String lockedBy,
                   @Param("retryCount") Integer retryCount,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("lastError") String lastError);
    
    /**
     * 标记为死信（超过最大重试次数）
     */
    @Override
    int markDead(@Param("table") String table,
                 @Param("id") Long id,
                 @Param("lockedBy") String lockedBy,
                 @Param("lastError") String lastError);
    
    /**
     * 恢复卡死的任务（PROCESSING 状态超过指定时间）
     */
    @Override
    @Deprecated
    int recoverStuck(@Param("table") String table,
                     @Param("now") LocalDateTime now,
                     @Param("stuckThreshold") LocalDateTime stuckThreshold);
    
    /**
     * 按分片恢复卡死的任务（PROCESSING 状态且 lock_until 已过期）
     */
    @Override
    int recoverStuckByShard(@Param("table") String table,
                            @Param("shardId") Integer shardId,
                            @Param("now") LocalDateTime now);
    
    /**
     * 更新shard_id
     */
    @Override
    int updateShardId(@Param("table") String table, @Param("id") Long id, @Param("shardId") Integer shardId);
    
    /**
     * 释放锁：将状态从 PROCESSING 改回 NEW，清除 lock_owner
     */
    @Override
    int releaseLock(@Param("table") String table, @Param("id") Long id, @Param("lockOwner") String lockOwner);
    
    /**
     * 续租锁：延长 lock_until 时间（用于慢任务避免锁过期）
     */
    @Override
    int extendLock(@Param("table") String table,
                   @Param("id") Long id,
                   @Param("lockedBy") String lockedBy,
                   @Param("newLockUntil") LocalDateTime newLockUntil,
                   @Param("now") LocalDateTime now);
    
    /**
     * 抢占式 claim：批量更新状态为 PROCESSING，并设置锁信息（按 shard_id 过滤）
     */
    @Override
    int claim(@Param("table") String table,
              @Param("shardId") Integer shardId,
              @Param("lockedBy") String lockedBy,
              @Param("lockUntil") LocalDateTime lockUntil,
              @Param("now") LocalDateTime now,
              @Param("limit") int limit);
    
    /**
     * 查询已 claim 的任务（用于发送，按 shard_id 过滤）
     */
    @Override
    List<Outbox> selectClaimed(@Param("table") String table,
                               @Param("shardId") Integer shardId,
                               @Param("lockedBy") String lockedBy,
                               @Param("now") LocalDateTime now,
                               @Param("limit") int limit);
    
    /**
     * 标记为已发送（SENT）
     */
    @Override
    int markSent(@Param("table") String table, @Param("id") Long id, @Param("lockedBy") String lockedBy);
    
    /**
     * 查询死信列表
     */
    @Override
    List<Outbox> selectDeadLetters(@Param("table") String table,
                                   @Param("type") String type,
                                   @Param("bizKey") String bizKey,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);
    
    /**
     * 按业务键和类型查询死信任务
     */
    @Override
    List<Outbox> selectDeadByBizKeyAndType(@Param("table") String table,
                                           @Param("bizKey") String bizKey,
                                           @Param("type") String type);
    
    /**
     * 重置死信任务为 NEW 状态（用于手动重放）
     */
    @Override
    int resetDeadToNew(@Param("table") String table, @Param("id") Long id);
    
    /**
     * 两段式 claim：使用 FOR UPDATE SKIP LOCKED 避免多实例并发锁等待
     */
    @Override
    List<Outbox> claimAndLoad(@Param("table") String table,
                              @Param("shardId") Integer shardId,
                              @Param("lockedBy") String lockedBy,
                              @Param("lockUntil") LocalDateTime lockUntil,
                              @Param("now") LocalDateTime now,
                              @Param("limit") int limit);
    
    /**
     * 第一步：SELECT id ... FOR UPDATE SKIP LOCKED
     */
    @Override
    List<Long> selectIdsForClaim(@Param("table") String table,
                                 @Param("shardId") Integer shardId,
                                 @Param("now") LocalDateTime now,
                                 @Param("limit") int limit);
    
    /**
     * 第二步：UPDATE ... WHERE id IN (...) AND shard_id = #{shardId}
     */
    @Override
    int claimByIds(@Param("table") String table,
                   @Param("shardId") Integer shardId,
                   @Param("ids") List<Long> ids,
                   @Param("lockedBy") String lockedBy,
                   @Param("lockUntil") LocalDateTime lockUntil,
                   @Param("now") LocalDateTime now);
    
    /**
     * 第三步：SELECT * ... WHERE id IN (...) AND shard_id = #{shardId}
     */
    @Override
    List<Outbox> selectByIds(@Param("table") String table, 
                             @Param("shardId") Integer shardId, 
                             @Param("ids") List<Long> ids);
}

