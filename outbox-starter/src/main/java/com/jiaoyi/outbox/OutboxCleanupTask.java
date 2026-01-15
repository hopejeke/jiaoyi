package com.jiaoyi.outbox;

import com.jiaoyi.outbox.config.OutboxProperties;
import com.jiaoyi.outbox.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Outbox 清理任务
 * 
 * 功能：
 * 1. 清理 SENT 状态的记录（保留 7~30 天，可配置）
 * 2. 清理 DEAD 状态的记录（保留 90~180 天，可配置）
 * 3. 按 shard_id 分批执行，避免长事务
 */
@Slf4j
@Component
public class OutboxCleanupTask {
    
    private final OutboxRepository outboxRepository;
    
    /**
     * SENT 记录保留天数（默认 7 天）
     */
    @Value("${outbox.cleanup.sent.retention.days:7}")
    private int sentRetentionDays;
    
    /**
     * DEAD 记录保留天数（默认 90 天）
     */
    @Value("${outbox.cleanup.dead.retention.days:90}")
    private int deadRetentionDays;
    
    /**
     * 每批处理的 shard_id 数量（默认 32，即每次处理一个表的所有 shard）
     */
    @Value("${outbox.cleanup.batch.size:32}")
    private int batchSize;
    
    /**
     * 分片总数（默认 1024）
     */
    @Value("${outbox.shard.count:1024}")
    private int shardCount;
    
    public OutboxCleanupTask(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }
    
    /**
     * 清理 SENT 状态的记录（每天凌晨 2 点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupSentRecords() {
        log.info("【OutboxCleanupTask】开始清理 SENT 状态的记录，保留天数: {}", sentRetentionDays);
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(sentRetentionDays);
            String table = getTable();
            
            int totalDeleted = 0;
            
            // 按 shard_id 分批处理（避免长事务）
            for (int shardId = 0; shardId < shardCount; shardId += batchSize) {
                int endShardId = Math.min(shardId + batchSize, shardCount);
                
                int deleted = outboxRepository.deleteSentRecordsByShardRange(
                    table, shardId, endShardId, cutoffTime
                );
                
                if (deleted > 0) {
                    log.info("【OutboxCleanupTask】清理 SENT 记录，shard_id: {}-{}, 删除数量: {}", 
                            shardId, endShardId - 1, deleted);
                    totalDeleted += deleted;
                }
            }
            
            log.info("【OutboxCleanupTask】SENT 记录清理完成，总删除数量: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("【OutboxCleanupTask】清理 SENT 记录失败", e);
        }
    }
    
    /**
     * 清理 DEAD 状态的记录（每周日凌晨 3 点执行）
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void cleanupDeadRecords() {
        log.info("【OutboxCleanupTask】开始清理 DEAD 状态的记录，保留天数: {}", deadRetentionDays);
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(deadRetentionDays);
            String table = getTable();
            
            int totalDeleted = 0;
            
            // 按 shard_id 分批处理（避免长事务）
            for (int shardId = 0; shardId < shardCount; shardId += batchSize) {
                int endShardId = Math.min(shardId + batchSize, shardCount);
                
                int deleted = outboxRepository.deleteDeadRecordsByShardRange(
                    table, shardId, endShardId, cutoffTime
                );
                
                if (deleted > 0) {
                    log.info("【OutboxCleanupTask】清理 DEAD 记录，shard_id: {}-{}, 删除数量: {}", 
                            shardId, endShardId - 1, deleted);
                    totalDeleted += deleted;
                }
            }
            
            log.info("【OutboxCleanupTask】DEAD 记录清理完成，总删除数量: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("【OutboxCleanupTask】清理 DEAD 记录失败", e);
        }
    }
    
    /**
     * 获取 outbox 表名（从配置或默认值）
     */
    private String getTable() {
        // 从配置中获取表名，如果没有配置则使用默认值
        // 注意：这里需要从 OutboxProperties 或配置中获取
        // 暂时使用默认值，实际应该从配置中读取
        return "order_outbox";
    }
}

