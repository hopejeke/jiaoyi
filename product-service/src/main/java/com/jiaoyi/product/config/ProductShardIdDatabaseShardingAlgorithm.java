package com.jiaoyi.product.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 商品域数据库分片算法（基于 product_shard_id，纯函数实现）
 * 
 * 核心逻辑：
 * 1. 使用纯函数计算：dsIndex = product_shard_id % dsCount
 * 2. 返回 ds${dsIndex}（如 ds0, ds1, ds2）
 * 3. 不依赖 ProductRouteCache，适用于所有线程（Web、定时任务、MQ 消费等）
 * 
 * 配置参数：
 * - ds-count: 数据源数量（默认 3）
 * - ds-prefix: 数据源名称前缀（默认 "ds"）
 * 
 * 注意：
 * - 此算法适用于固定分片数量的场景（如 3 库、6 库）
 * - 如果需要动态路由（从路由表读取），请使用 ProductRouteCache 版本（但需要解决线程上下文问题）
 */
@Slf4j
public class ProductShardIdDatabaseShardingAlgorithm implements StandardShardingAlgorithm<Integer> {
    
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
        log.info("【ProductShardIdDatabaseShardingAlgorithm】初始化完成，数据源数量: {}, 前缀: {}", dsCount, dsPrefix);
    }
    
    /**
     * 精确分片（用于 = 和 IN 查询）
     * 
     * @param availableTargetNames 可用的数据源名称列表（ds0, ds1, ds2）
     * @param shardingValue 分片值（product_shard_id）
     * @return 目标数据源名称
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Integer> shardingValue) {
        Integer productShardId = shardingValue.getValue();
        
        if (productShardId == null) {
            log.warn("【ProductShardIdDatabaseShardingAlgorithm】product_shard_id 为 null，使用默认路由");
            return availableTargetNames.iterator().next();
        }
        
        // 验证 product_shard_id 范围
        if (productShardId < 0 || productShardId >= 1024) {
            log.warn("【ProductShardIdDatabaseShardingAlgorithm】product_shard_id {} 超出范围（0-1023），使用默认路由", productShardId);
            return availableTargetNames.iterator().next();
        }
        
        // 纯函数计算：dsIndex = product_shard_id % dsCount
        // 使用 Math.floorMod 确保结果为非负数（即使 productShardId 为负数）
        int dsIndex = Math.floorMod(productShardId, dsCount);
        String dsName = dsPrefix + dsIndex;
        
        // 必须校验 availableTargetNames，避免返回不存在的 ds（配置错误）
        if (!availableTargetNames.contains(dsName)) {
            String errorMsg = String.format(
                "product_shard_id %d 路由到 %s，但该库不在可用列表中（可用列表: %s）。" +
                "可能原因：1) ShardingSphere 配置缺少该数据源；2) ds-count 配置错误。",
                productShardId, dsName, availableTargetNames);
            log.error("【ProductShardIdDatabaseShardingAlgorithm】{}", errorMsg);
            // 抛出异常，不让应用继续运行（路由错误会导致数据写入错误的库）
            throw new IllegalStateException(errorMsg);
        }
        
        log.debug("【ProductShardIdDatabaseShardingAlgorithm】product_shard_id {} -> {}", productShardId, dsName);
        return dsName;
    }
    
    /**
     * 范围分片（用于 BETWEEN 和 < > 查询）
     * 
     * @param availableTargetNames 可用的数据源名称列表
     * @param shardingValue 分片值范围
     * @return 目标数据源名称列表
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Integer> shardingValue) {
        // 范围查询：返回所有可用的目标
        log.debug("【ProductShardIdDatabaseShardingAlgorithm】范围查询，返回所有可用库");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "PRODUCT_SHARD_ID_DATABASE";
    }
}

