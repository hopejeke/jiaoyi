package com.jiaoyi.product.config;

import com.jiaoyi.product.util.ProductShardUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 基于 store_id 的数据库分片算法（用于 outbox 表）
 * 
 * 核心逻辑：
 * 1. 从 store_id 计算 product_shard_id：使用 ProductShardUtil.calculateProductShardId(storeId)
 * 2. 使用纯函数计算：dsIndex = product_shard_id % dsCount
 * 3. 返回 ds${dsIndex}（如 ds0, ds1）
 * 
 * 配置参数：
 * - ds-count: 数据源数量（默认 2）
 * - ds-prefix: 数据源名称前缀（默认 "ds"）
 */
@Slf4j
public class StoreIdDatabaseShardingAlgorithm implements StandardShardingAlgorithm<Long> {
    
    private int dsCount = 2;
    private String dsPrefix = "ds";
    
    @Override
    public void init(Properties props) {
        if (props.containsKey("ds-count")) {
            dsCount = Integer.parseInt(props.getProperty("ds-count"));
        }
        if (props.containsKey("ds-prefix")) {
            dsPrefix = props.getProperty("ds-prefix");
        }
        log.info("【StoreIdDatabaseShardingAlgorithm】初始化完成，数据源数量: {}, 前缀: {}", dsCount, dsPrefix);
    }
    
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        Long storeId = shardingValue.getValue();
        
        if (storeId == null) {
            log.warn("【StoreIdDatabaseShardingAlgorithm】store_id 为 null，使用默认路由");
            return availableTargetNames.iterator().next();
        }
        
        // 从 store_id 计算 product_shard_id（使用 ProductShardUtil 统一算法）
        int productShardId = ProductShardUtil.calculateProductShardId(storeId);
        
        // 计算数据源索引
        int dsIndex = Math.floorMod(productShardId, dsCount);
        String dsName = dsPrefix + dsIndex;
        
        if (!availableTargetNames.contains(dsName)) {
            String errorMsg = String.format(
                "store_id %d (product_shard_id %d) 路由到 %s，但该库不在可用列表中（可用列表: %s）",
                storeId, productShardId, dsName, availableTargetNames);
            log.error("【StoreIdDatabaseShardingAlgorithm】{}", errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        log.debug("【StoreIdDatabaseShardingAlgorithm】store_id {} (product_shard_id {}) -> {}", storeId, productShardId, dsName);
        return dsName;
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        log.debug("【StoreIdDatabaseShardingAlgorithm】范围查询，返回所有可用库");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "STORE_ID_DATABASE";
    }
}

