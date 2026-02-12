package com.jiaoyi.product.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 merchant_id（字符串）的数据库分片算法
 * 
 * @deprecated 已废弃。Merchant 域已统一使用 product_shard_id 分片（与商品域共享）。
 *             保留此类仅用于参考，不再被 ShardingSphereConfig 注册。
 */
@Deprecated
public class MerchantIdDatabaseShardingAlgorithm implements StandardShardingAlgorithm<String> {
    
    @Override
    public void init(Properties props) {
        // 无需配置
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        String merchantId = shardingValue.getValue();
        
        // 将 merchant_id 字符串转换为数字（使用哈希值）
        int hashCode = Math.abs(merchantId.hashCode());
        int shardValue = hashCode % 6;
        
        // 数据库分片：shardValue / 3（2库 × 3表 = 6个分片）
        int dbIndex = shardValue / 3;
        String targetName = "ds" + dbIndex;
        
        System.out.println("[MerchantIdDatabaseShardingAlgorithm] merchantId=" + merchantId 
            + ", hashCode=" + hashCode 
            + ", shardValue=" + shardValue 
            + ", dbIndex=" + dbIndex 
            + ", targetName=" + targetName);
        
        if (availableTargetNames.contains(targetName)) {
            return targetName;
        }
        
        // 如果找不到，返回第一个可用的目标
        System.out.println("[MerchantIdDatabaseShardingAlgorithm] 警告：目标数据源 " + targetName + " 不在可用列表中，使用第一个可用数据源");
        return availableTargetNames.iterator().next();
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<String> shardingValue) {
        // 范围查询：返回所有可用的目标
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "MERCHANT_ID_DATABASE_HASH";
    }
}

