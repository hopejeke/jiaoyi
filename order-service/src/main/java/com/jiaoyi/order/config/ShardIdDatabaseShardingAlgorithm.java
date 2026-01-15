package com.jiaoyi.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 shard_id 的数据库分片算法（纯函数实现，不依赖 Spring 上下文）
 * 
 * 核心逻辑：
 * 1. 使用纯函数计算：dsIndex = shard_id % dsCount
 * 2. 返回 ds${dsIndex}（如 ds0, ds1, ds2）
 * 3. 不依赖 RouteCache，适用于所有线程（Web、定时任务、MQ 消费等）
 * 
 * 配置参数：
 * - ds-count: 数据源数量（默认 3）
 * - ds-prefix: 数据源名称前缀（默认 "ds"）
 * 
 * 注意：
 * - 此算法适用于固定分片数量的场景（如 3 库、6 库）
 * - 如果需要动态路由（从路由表读取），请使用 RouteCache 版本（但需要解决线程上下文问题）
 */
@Slf4j
public class ShardIdDatabaseShardingAlgorithm implements StandardShardingAlgorithm<Integer> {
    
    /**
     * 数据源数量（从配置读取）
     */
    private int dsCount = 3;
    
    /**
     * 数据源名称前缀（从配置读取）
     */
    private String dsPrefix = "ds";
    
    @Override
    public void init(Properties props) {
        // 从配置读取数据源数量
        if (props.containsKey("ds-count")) {
            dsCount = Integer.parseInt(props.getProperty("ds-count"));
        }
        // 从配置读取数据源前缀
        if (props.containsKey("ds-prefix")) {
            dsPrefix = props.getProperty("ds-prefix");
        }
        log.info("【ShardIdDatabaseShardingAlgorithm】初始化完成，数据源数量: {}, 前缀: {}", dsCount, dsPrefix);
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Integer> shardingValue) {
        Integer shardId = shardingValue.getValue();
        
        if (shardId == null) {
            log.warn("【ShardIdDatabaseShardingAlgorithm】shard_id 为 null，使用默认路由");
            return availableTargetNames.iterator().next();
        }
        
        // 验证 shard_id 范围
        if (shardId < 0 || shardId >= 1024) {
            log.warn("【ShardIdDatabaseShardingAlgorithm】shard_id {} 超出范围（0-1023），使用默认路由", shardId);
            return availableTargetNames.iterator().next();
        }
        
        // 纯函数计算：dsIndex = shard_id % dsCount
        // 使用 Math.floorMod 确保结果为非负数（即使 shardId 为负数）
        int dsIndex = Math.floorMod(shardId, dsCount);
        String dsName = dsPrefix + dsIndex;
        
        // 必须校验 availableTargetNames，避免返回不存在的 ds（配置错误）
        if (!availableTargetNames.contains(dsName)) {
            String errorMsg = String.format(
                "shard_id %d 路由到 %s，但该库不在可用列表中（可用列表: %s）。" +
                "可能原因：1) ShardingSphere 配置缺少该数据源；2) ds-count 配置错误。",
                shardId, dsName, availableTargetNames);
            log.error("【ShardIdDatabaseShardingAlgorithm】{}", errorMsg);
            // 抛出异常，不让应用继续运行（路由错误会导致数据写入错误的库）
            throw new IllegalStateException(errorMsg);
        }
        
        log.debug("【ShardIdDatabaseShardingAlgorithm】shard_id {} -> {}", shardId, dsName);
        return dsName;
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Integer> shardingValue) {
        // 范围查询：返回所有可用的目标
        log.debug("【ShardIdDatabaseShardingAlgorithm】范围查询，返回所有可用库");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "SHARD_ID_DATABASE";
    }
}

