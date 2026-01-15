package com.jiaoyi.order.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 store_id 的数据库分片算法
 * 
 * 算法：store_id % dsCount -> ds0/ds1/ds2
 * 
 * 注意：这是一个纯函数，不依赖 RouteCache，适用于所有线程（包括定时任务）
 */
public class StoreIdDatabaseShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private int dsCount = 3;
    private String dsPrefix = "ds";

    @Override
    public void init(Properties props) {
        if (props.containsKey("ds-count")) {
            dsCount = Integer.parseInt(props.getProperty("ds-count"));
        }
        if (props.containsKey("ds-prefix")) {
            dsPrefix = props.getProperty("ds-prefix");
        }
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        Long storeId = shardingValue.getValue();
        if (storeId == null) {
            throw new IllegalArgumentException("store_id 不能为空");
        }

        // 先计算 shard_id = hash(store_id) & 1023（与商品服务保持一致）
        int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(storeId);
        
        // 再计算数据库索引：shard_id % dsCount
        int dsIndex = shardId % dsCount;
        String dsName = dsPrefix + dsIndex;

        // 验证数据源是否存在
        if (!availableTargetNames.contains(dsName)) {
            throw new IllegalStateException(
                    String.format("路由到的数据源 %s 不在可用数据源列表中: %s", dsName, availableTargetNames));
        }

        return dsName;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        // 范围查询：返回所有可用的数据源
        return availableTargetNames;
    }

    @Override
    public String getType() {
        return "STORE_ID_DATABASE";
    }
}

