package com.jiaoyi.order.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 store_id 的表分片算法
 * 
 * 算法：
 * 1. 先计算 shard_id = hash(store_id) & 1023
 * 2. 再计算表索引 = shard_id % 32
 * 3. 生成表名 = logicTableName + "_" + twoDigits(tableIndex)
 */
public class StoreIdTableShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private int tableCountPerDb = 32; // 默认32张表/库

    @Override
    public void init(Properties props) {
        if (props.containsKey("table.count.per.db")) {
            tableCountPerDb = Integer.parseInt(props.getProperty("table.count.per.db"));
        }
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        Long storeId = shardingValue.getValue();
        if (storeId == null) {
            throw new IllegalArgumentException("store_id 不能为空");
        }

        // 计算 shard_id = hash(store_id) & 1023
        int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(storeId);
        
        // 计算表索引 = shard_id % 32
        int tableIndex = shardId % tableCountPerDb;
        
        // 生成表名后缀（两位数字符串）
        String tableSuffix = String.format("%02d", tableIndex);
        
        // 从逻辑表名获取前缀（例如：orders -> orders_00）
        String logicTableName = shardingValue.getLogicTableName();
        String targetTableName = logicTableName + "_" + tableSuffix;
        
        // 验证目标表是否在可用列表中
        if (!availableTargetNames.contains(targetTableName)) {
            throw new IllegalStateException(
                String.format("store_id %d 计算出的表 %s 不在可用列表 %s 中", 
                    storeId, targetTableName, availableTargetNames));
        }
        
        return targetTableName;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        // 范围查询：返回所有可用的表
        return availableTargetNames;
    }

    @Override
    public String getType() {
        return "STORE_ID_TABLE";
    }
}



