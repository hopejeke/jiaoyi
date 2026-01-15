package com.jiaoyi.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 shard_id 的表分片算法
 * 
 * 核心逻辑：
 * - 物理分表固定：32 张表（可配置，默认32）
 * - table_idx = shard_id % 32
 * - 物理表命名：order_00..order_31、outbox_00..outbox_31
 */
@Slf4j
public class ShardIdTableShardingAlgorithm implements StandardShardingAlgorithm<Integer> {
    
    /**
     * 每库表数量（默认32，可配置）
     */
    private int tableCountPerDb = 32;
    
    @Override
    public void init(Properties props) {
        // 从配置中读取表数量（如果配置了）
        if (props.containsKey("table.count.per.db")) {
            tableCountPerDb = Integer.parseInt(props.getProperty("table.count.per.db"));
        }
        log.info("【ShardIdTableShardingAlgorithm】初始化完成，每库表数量: {}", tableCountPerDb);
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Integer> shardingValue) {
        Integer shardId = shardingValue.getValue();
        
        if (shardId == null) {
            log.warn("【ShardIdTableShardingAlgorithm】shard_id 为 null，使用默认表");
            return availableTargetNames.iterator().next();
        }
        
        // 计算表索引：table_idx = shard_id % 32
        int tableIndex = shardId % tableCountPerDb;
        
        // 格式化表索引为两位数字符串（00-31）
        String tableSuffix = String.format("%02d", tableIndex);
        
        // 逻辑表名（如 "orders" 或 "order_outbox"）
        String logicTableName = shardingValue.getLogicTableName();
        
        // 物理表名：logicTableName + "_" + tableSuffix
        // 例如：orders -> orders_00, orders_01, ..., orders_31
        String actualTableName = logicTableName + "_" + tableSuffix;
        
        // 检查表名是否在可用列表中
        if (availableTargetNames.contains(actualTableName)) {
            return actualTableName;
        }
        
        // 如果表名不在可用列表中，尝试查找匹配的表
        for (String availableTable : availableTargetNames) {
            if (availableTable.endsWith("_" + tableSuffix)) {
                return availableTable;
            }
        }
        
        // 如果找不到，返回第一个可用的表
        log.warn("【ShardIdTableShardingAlgorithm】shard_id {} 计算出的表 {} 不在可用列表中，使用默认表", 
                shardId, actualTableName);
        return availableTargetNames.iterator().next();
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Integer> shardingValue) {
        // 范围查询：返回所有可用的表
        log.debug("【ShardIdTableShardingAlgorithm】范围查询，返回所有可用表");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "SHARD_ID_TABLE";
    }
}

