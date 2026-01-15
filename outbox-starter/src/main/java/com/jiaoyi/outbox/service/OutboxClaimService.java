package com.jiaoyi.outbox.service;

import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox Claim 服务
 * 提供两段式 claim 方法，使用 FOR UPDATE SKIP LOCKED 避免多实例并发锁等待
 * 
 * 注意：不使用 @Service，由 OutboxAutoConfiguration 手动创建 Bean
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxClaimService {
    
    private final OutboxRepository outboxRepository;
    
    /**
     * 两段式 claim：在同一个事务内执行三步
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
    @Transactional(transactionManager = "shardingTransactionManager")
    public List<Outbox> claimAndLoad(String table, Integer shardId, String lockedBy,
                                     LocalDateTime lockUntil, LocalDateTime now, int limit) {
        // 第一步：SELECT id ... FOR UPDATE SKIP LOCKED
        List<Long> ids = outboxRepository.selectIdsForClaim(table, shardId, now, limit);
        
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 第二步：UPDATE ... WHERE id IN (...) AND shard_id = #{shardId}
        int updated = outboxRepository.claimByIds(table, shardId, ids, lockedBy, lockUntil, now);
        if (updated != ids.size()) {
            log.warn("【OutboxClaimService】claim 更新的行数 {} 与查询到的 ID 数量 {} 不一致，表: {}, shardId: {}", 
                    updated, ids.size(), table, shardId);
        }
        
        // 第三步：SELECT * ... WHERE id IN (...) AND shard_id = #{shardId} 返回完整数据
        List<Outbox> tasks = outboxRepository.selectByIds(table, shardId, ids);
        
        log.debug("【OutboxClaimService】两段式 claim 完成，表: {}, shardId: {}, claim 到 {} 个任务", 
                table, shardId, tasks.size());
        
        return tasks;
    }
}

