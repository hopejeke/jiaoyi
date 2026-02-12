package com.jiaoyi.product.config;

import com.jiaoyi.product.util.ProductShardUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 store_id 的表分片算法（用于 outbox 表）
 * 
 * 核心逻辑：
 * 1. 从 store_id 计算 product_shard_id：使用 ProductShardUtil.calculateProductShardId(storeId)
 * 2. 计算表索引：table_idx = product_shard_id % 4
 * 3. 生成物理表名：table_name = logicTableName + "_" + twoDigits(table_idx)
 * 
 * 配置参数：
 * - table.count.per.db: 每库表数量（默认4）
 */
@Slf4j
public class StoreIdTableShardingAlgorithm implements StandardShardingAlgorithm<Long> {
    
    private int tableCountPerDb = 4;
    
    @Override
    public void init(Properties props) {
        if (props != null && props.containsKey("table.count.per.db")) {
            try {
                tableCountPerDb = Integer.parseInt(props.getProperty("table.count.per.db"));
            } catch (NumberFormatException e) {
                log.warn("【StoreIdTableShardingAlgorithm】无法解析 table.count.per.db，使用默认值 4", e);
            }
        }
        log.info("【StoreIdTableShardingAlgorithm】初始化完成，每库表数量: {}", tableCountPerDb);
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        Long storeId = shardingValue.getValue();
        if (storeId == null) {
            throw new IllegalArgumentException("store_id 不能为空");
        }
        
        // 从 store_id 计算 product_shard_id（使用 ProductShardUtil 统一算法）
        int productShardId = ProductShardUtil.calculateProductShardId(storeId);
        
        // 计算表索引（使用 ProductShardUtil 统一方法）
        int tableIndex = ProductShardUtil.calculateTableIndex(productShardId, tableCountPerDb);
        
        // 生成表名后缀（使用 ProductShardUtil 统一格式化）
        String tableSuffix = ProductShardUtil.formatTableIndex(tableIndex);
        
        // 从逻辑表名获取前缀
        String logicTableName = shardingValue.getLogicTableName();
        String targetTableName = logicTableName + "_" + tableSuffix;
        
        if (!availableTargetNames.contains(targetTableName)) {
            throw new IllegalStateException(
                String.format("store_id %d (product_shard_id %d) 计算出的表 %s 不在可用列表 %s 中", 
                    storeId, productShardId, targetTableName, availableTargetNames));
        }
        
        return targetTableName;
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "STORE_ID_TABLE";
    }
}

