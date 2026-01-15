package com.jiaoyi.product.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * Outbox 表的 sharding_key 分片算法
 * sharding_key 是字符串，但存的是 store_id 的数字字符串
 * 需要将字符串转换为数字后再计算分片，与商品表保持一致
 */
public class OutboxShardingKeyDatabaseShardingAlgorithm implements StandardShardingAlgorithm<String> {
    
    @Override
    public void init(Properties props) {
        // 无需配置
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        String shardingKey = shardingValue.getValue();
        
        if (shardingKey == null || shardingKey.isEmpty()) {
            // 如果 sharding_key 为空，返回第一个可用的目标
            return availableTargetNames.iterator().next();
        }
        
        try {
            // 将字符串转换为数字（sharding_key 存的是 store_id 的数字字符串）
            long storeId = Long.parseLong(shardingKey);
            
            // 使用与商品表相同的分片算法：(store_id % 9).intdiv(3)
            long shardValue = storeId % 9;
            int dbIndex = (int) (shardValue / 3); // 0, 1, 2
            String targetName = "ds" + dbIndex;
            
            if (availableTargetNames.contains(targetName)) {
                return targetName;
            }
            
            // 如果找不到，返回第一个可用的目标
            return availableTargetNames.iterator().next();
        } catch (NumberFormatException e) {
            // 如果无法转换为数字，返回第一个可用的目标
            return availableTargetNames.iterator().next();
        }
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<String> shardingValue) {
        // 范围查询：返回所有可用的目标
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "OUTBOX_SHARDING_KEY_DATABASE";
    }
}

