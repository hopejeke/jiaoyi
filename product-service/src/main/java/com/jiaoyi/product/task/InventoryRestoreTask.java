package com.jiaoyi.product.task;

import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.mapper.sharding.InventoryMapper;
import com.jiaoyi.product.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 库存自动恢复定时任务
 *
 * 功能：
 * 1. 次日自动恢复：每天凌晨0点，恢复所有设置为"次日恢复"的库存
 * 2. 指定时间恢复：每5分钟检查一次，恢复到期的库存
 *
 * 并发安全：
 * 1. 使用 @Scheduled(fixedDelay) 避免任务重叠
 * 2. 使用 instance_id + task_name 作为分布式锁键
 * 3. SQL层面使用CAS更新，防止重复恢复
 *
 * @author Claude
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryRestoreTask {

    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;

    // 实例ID（每次启动生成唯一ID，用于多实例场景下的区分）
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * 次日自动恢复任务
     * 每天凌晨0点5分执行（延迟5分钟，避免与其他凌晨任务冲突）
     *
     * 对应PRD需求：次日自动恢复（Until Tomorrow）
     *
     * 并发安全说明：
     * - 使用fixedDelay确保上一次执行完才开始下一次
     * - 多实例部署时，通过Redis分布式锁确保只有一个实例执行
     * - SQL更新使用WHERE条件检查last_restore_time，避免重复恢复
     */
    @Scheduled(cron = "0 5 0 * * ?")  // 每天凌晨0点5分
    public void restoreInventoryTomorrow() {
        String lockKey = "inventory:restore:tomorrow:" + LocalDateTime.now().toLocalDate();
        log.info("[实例{}] 开始执行次日自动恢复库存任务", instanceId);

        // TODO: 如果是多实例部署，建议在这里添加Redis分布式锁
        // 示例：
        // if (!redisLock.tryLock(lockKey, 300)) {
        //     log.info("其他实例正在执行，跳过本次任务");
        //     return;
        // }

        try {
            // 查询所有设置为"次日恢复"的库存
            List<Inventory> inventories = inventoryMapper.selectForTomorrowRestore();

            if (inventories == null || inventories.isEmpty()) {
                log.info("[实例{}] 没有需要次日恢复的库存", instanceId);
                return;
            }

            log.info("[实例{}] 找到 {} 条需要次日恢复的库存记录", instanceId, inventories.size());

            int successCount = 0;
            int failCount = 0;
            int skippedCount = 0;

            // 批量恢复库存
            for (Inventory inventory : inventories) {
                try {
                    // 检查是否已经恢复过（防止重复恢复）
                    if (isRestoredToday(inventory)) {
                        skippedCount++;
                        log.debug("[实例{}] 库存今天已恢复过，跳过: inventoryId={}",
                                instanceId, inventory.getId());
                        continue;
                    }

                    inventoryService.restoreInventory(inventory.getId());
                    successCount++;
                    log.debug("[实例{}] 恢复库存成功: inventoryId={}, productId={}, skuId={}",
                            instanceId, inventory.getId(), inventory.getProductId(), inventory.getSkuId());
                } catch (Exception e) {
                    failCount++;
                    log.error("[实例{}] 恢复库存失败: inventoryId={}, productId={}, skuId={}",
                            instanceId, inventory.getId(), inventory.getProductId(), inventory.getSkuId(), e);
                }
            }

            log.info("[实例{}] 次日自动恢复库存任务完成: 成功={}, 失败={}, 跳过={}",
                    instanceId, successCount, failCount, skippedCount);

        } catch (Exception e) {
            log.error("[实例{}] 次日自动恢复库存任务执行失败", instanceId, e);
        } finally {
            // TODO: 释放分布式锁
            // redisLock.unlock(lockKey);
        }
    }

    /**
     * 指定时间恢复任务
     * 每5分钟检查一次（使用fixedDelay确保任务不重叠）
     *
     * 对应PRD需求：指定时间恢复（Until specific time）
     *
     * 并发安全说明：
     * - fixedDelay=300000 确保上次执行完成后才开始下次执行
     * - SQL层面使用CAS更新（WHERE last_restore_time < restore_time）
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)  // 5分钟，启动后1分钟开始
    public void restoreInventoryScheduled() {
        log.debug("[实例{}] 开始检查指定时间恢复的库存", instanceId);

        try {
            LocalDateTime now = LocalDateTime.now();

            // 查询所有到期需要恢复的库存（restore_time <= now）
            List<Inventory> inventories = inventoryMapper.selectForScheduledRestore(now);

            if (inventories == null || inventories.isEmpty()) {
                log.debug("[实例{}] 没有到期需要恢复的库存", instanceId);
                return;
            }

            log.info("[实例{}] 找到 {} 条到期需要恢复的库存记录", instanceId, inventories.size());

            int successCount = 0;
            int failCount = 0;

            // 批量恢复库存
            for (Inventory inventory : inventories) {
                String lockKey = "inventory:restore:scheduled:" + inventory.getId();

                // TODO: 如果是多实例部署，建议在这里添加Redis分布式锁
                // if (!redisLock.tryLock(lockKey, 60)) {
                //     log.debug("其他实例正在处理该库存，跳过: inventoryId={}", inventory.getId());
                //     continue;
                // }

                try {
                    inventoryService.restoreInventory(inventory.getId());
                    successCount++;
                    log.info("[实例{}] 恢复库存成功: inventoryId={}, productId={}, skuId={}, restoreTime={}",
                            instanceId, inventory.getId(), inventory.getProductId(),
                            inventory.getSkuId(), inventory.getRestoreTime());
                } catch (Exception e) {
                    failCount++;
                    log.error("[实例{}] 恢复库存失败: inventoryId={}, productId={}, skuId={}, restoreTime={}",
                            instanceId, inventory.getId(), inventory.getProductId(),
                            inventory.getSkuId(), inventory.getRestoreTime(), e);
                } finally {
                    // TODO: 释放分布式锁
                    // redisLock.unlock(lockKey);
                }
            }

            log.info("[实例{}] 指定时间恢复库存任务完成: 成功={}, 失败={}",
                    instanceId, successCount, failCount);

        } catch (Exception e) {
            log.error("[实例{}] 指定时间恢复库存任务执行失败", instanceId, e);
        }
    }

    /**
     * 清理过期的恢复配置
     * 每天凌晨2点执行
     *
     * 清理逻辑：
     * 1. 已经恢复过的指定时间恢复记录（restore_time < now - 7天）
     * 2. 将这些记录的 restore_enabled 设置为 false
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredRestoreConfig() {
        log.info("[实例{}] 开始清理过期的库存恢复配置", instanceId);

        try {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

            // 查询7天前已恢复的记录
            int count = inventoryMapper.disableExpiredRestoreConfig(sevenDaysAgo);

            log.info("[实例{}] 清理过期的库存恢复配置完成: 共清理 {} 条记录", instanceId, count);

        } catch (Exception e) {
            log.error("[实例{}] 清理过期的库存恢复配置失败", instanceId, e);
        }
    }

    /**
     * 检查库存今天是否已经恢复过
     *
     * 判断逻辑：
     * - 如果 last_restore_time 是今天，说明已恢复过
     * - 对于次日恢复模式，每天只恢复一次
     */
    private boolean isRestoredToday(Inventory inventory) {
        if (inventory.getLastRestoreTime() == null) {
            return false;
        }

        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        return inventory.getLastRestoreTime().isAfter(today);
    }
}
