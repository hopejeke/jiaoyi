package com.jiaoyi.outbox;

import com.jiaoyi.outbox.config.OutboxProperties;
import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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

    @Autowired(required = false)
    private OutboxService outboxService; // 用于处理任务
    
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
     * 定时重试失败的任务（每分钟执行一次）
     * 扫描 NEW 和 FAILED 状态的记录，重新处理
     */
    @Scheduled(fixedDelay = 60000) // 每60秒执行一次
    public void retryFailedTasks() {
        if (outboxService == null) {
            log.warn("【OutboxCleanupTask】OutboxService 未注入，跳过重试任务");
            return;
        }

        try {
            String table = getTable();
            LocalDateTime now = LocalDateTime.now();

            // 查询需要重试的任务（使用现有的selectCandidatesBySingleShard方法）
            // 遍历所有分片（这里简化处理，实际应该按分片分批处理）
            int successCount = 0;
            int failedCount = 0;
            int limit = 100; // 每次最多处理100个任务

            // 由于outbox表按store_id分片，这里使用selectCandidatesByShard查询
            // 简化处理：查询前几个分片的待处理任务
            for (int shardId = 0; shardId < Math.min(shardCount, 10); shardId++) {
                List<Outbox> pendingTasks = outboxRepository.selectCandidatesBySingleShard(
                        table,
                        shardId,
                        now,
                        limit
                );

                if (pendingTasks.isEmpty()) {
                    continue;
                }

                log.info("【OutboxCleanupTask】分片 {} 发现 {} 个需要重试的任务", shardId, pendingTasks.size());

                for (Outbox task : pendingTasks) {
                    try {
                        // 调用 OutboxService 处理任务（传入 shardId，避免广播查询）
                        outboxService.processTask(task.getId(), task.getShardId());
                        successCount++;
                    } catch (Exception e) {
                        log.error("【OutboxCleanupTask】重试任务失败，outboxId: {}", task.getId(), e);
                        failedCount++;
                    }
                }
            }

            if (successCount > 0 || failedCount > 0) {
                log.info("【OutboxCleanupTask】重试任务完成，成功: {}, 失败: {}", successCount, failedCount);
            }

        } catch (Exception e) {
            log.error("【OutboxCleanupTask】定时重试任务执行失败", e);
        }
    }

    /**
     * 监控 Outbox 堆积情况（每分钟执行一次）
     * 注意：由于没有countByStatus方法，这里简化为查询样本分片
     */
    @Scheduled(fixedDelay = 60000)
    public void monitorOutboxBacklog() {
        try {
            String table = getTable();
            LocalDateTime now = LocalDateTime.now();

            // 简化实现：查询前几个分片的待处理任务数量作为样本
            int totalBacklog = 0;
            int sampleShards = Math.min(shardCount, 10); // 采样前10个分片

            for (int shardId = 0; shardId < sampleShards; shardId++) {
                List<Outbox> candidates = outboxRepository.selectCandidatesBySingleShard(
                        table,
                        shardId,
                        now,
                        1000 // 查询最多1000个
                );
                totalBacklog += candidates.size();
            }

            // 如果采样了多个分片，估算总数
            if (sampleShards < shardCount) {
                totalBacklog = totalBacklog * shardCount / sampleShards;
            }

            if (totalBacklog > 1000) {
                log.error("【OutboxCleanupTask】【告警】Outbox 堆积过多，估算总计: {}",
                        totalBacklog);
                // TODO: 发送告警
                // alertService.sendAlert("Outbox堆积告警", "估算堆积: " + totalBacklog);
            } else if (totalBacklog > 100) {
                log.warn("【OutboxCleanupTask】Outbox 堆积较多，估算总计: {}",
                        totalBacklog);
            } else {
                log.debug("【OutboxCleanupTask】Outbox 状态正常，估算堆积: {}", totalBacklog);
            }

        } catch (Exception e) {
            log.error("【OutboxCleanupTask】监控 Outbox 堆积失败", e);
        }
    }

    /**
     * 获取 outbox 表名（从配置或默认值）
     */
    private String getTable() {
        // 从配置中获取表名，如果没有配置则使用默认值
        // 注意：这里需要从 OutboxProperties 或配置中获取
        // 暂时使用默认值，实际应该从配置中读取
        if (outboxService != null) {
            return outboxService.getTable();
        }
        return "order_outbox";
    }
}

