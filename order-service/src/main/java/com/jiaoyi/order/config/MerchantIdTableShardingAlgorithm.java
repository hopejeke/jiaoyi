package com.jiaoyi.order.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 merchant_id（字符串）的表分片算法
 */
public class MerchantIdTableShardingAlgorithm implements StandardShardingAlgorithm<String> {
    
    @Override
    public void init(Properties props) {
        // 无需配置
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        String merchantId = shardingValue.getValue();
        String logicTableName = shardingValue.getLogicTableName();
        
        // 将 merchant_id 字符串转换为数字（使用哈希值）
        int hashCode = Math.abs(merchantId.hashCode());
        int shardValue = hashCode % 9;
        
        // 表分片：shardValue % 3
        int tableIndex = shardValue % 3;
        
        // 从 availableTargetNames 中找到对应的表名
        // 例如：orders_0, orders_1, orders_2
        String targetTableName = logicTableName + "_" + tableIndex;
        
        if (availableTargetNames.contains(targetTableName)) {
            return targetTableName;
        }
        
        // 如果找不到，返回第一个可用的目标
        return availableTargetNames.iterator().next();
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<String> shardingValue) {
        // 范围查询：返回所有可用的目标
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "MERCHANT_ID_TABLE_HASH";
    }
}

