package com.jiaoyi.product.config;

import com.jiaoyi.product.util.SpringContextHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 商品域数据库分片算法 V2（基于路由表）
 * 
 * 核心逻辑：
 * 1. 从 product_shard_id 查询路由表获取 ds_name
 * 2. 支持迁移状态（MIGRATING 时返回源库）
 * 3. 使用 ProductRouteCache 缓存，性能好
 * 
 * 配置参数：
 * - use-routing-table: 是否使用路由表（默认 true）
 * - fallback-to-mod: 路由表不可用时是否降级为取模（默认 false）
 * 
 * 注意：
 * - 需要 SpringContextHolder 支持（用于获取 ProductRouteCache）
 * - 必须在 ProductRouteCache 初始化之后才能使用
 */
@Slf4j
public class ProductShardIdDatabaseShardingAlgorithmV2 implements StandardShardingAlgorithm<Integer> {
    
    /**
     * 是否使用路由表（默认 true）
     */
    private boolean useRoutingTable = true;
    
    /**
     * 路由表不可用时是否降级为取模（默认 false）
     */
    private boolean fallbackToMod = false;
    
    /**
     * 数据源数量（降级时使用）
     */
    private int dsCount = 2;
    
    /**
     * 数据源名称前缀（降级时使用）
     */
    private String dsPrefix = "ds";
    
    /**
     * 路由缓存（延迟加载）
     */
    private ProductRouteCache routeCache;
    
    @Getter
    private Properties props;
    
    @Override
    public void init(Properties props) {
        this.props = props;
        
        // 读取配置
        if (props.containsKey("use-routing-table")) {
            useRoutingTable = Boolean.parseBoolean(props.getProperty("use-routing-table"));
        }
        if (props.containsKey("fallback-to-mod")) {
            fallbackToMod = Boolean.parseBoolean(props.getProperty("fallback-to-mod"));
        }
        if (props.containsKey("ds-count")) {
            dsCount = Integer.parseInt(props.getProperty("ds-count"));
        }
        if (props.containsKey("ds-prefix")) {
            dsPrefix = props.getProperty("ds-prefix");
        }
        
        log.info("【ProductShardIdDatabaseShardingAlgorithmV2】初始化完成，useRoutingTable={}, fallbackToMod={}, dsCount={}", 
                useRoutingTable, fallbackToMod, dsCount);
    }
    
    /**
     * 获取路由缓存（延迟加载）
     */
    private ProductRouteCache getRouteCache() {
        if (routeCache == null) {
            if (!SpringContextHolder.isInitialized()) {
                throw new IllegalStateException("SpringContextHolder 未初始化，无法获取 ProductRouteCache");
            }
            routeCache = SpringContextHolder.getBean(ProductRouteCache.class);
            if (!routeCache.isInitialized()) {
                throw new IllegalStateException("ProductRouteCache 未初始化，无法使用路由表");
            }
        }
        return routeCache;
    }
    
    /**
     * 精确分片（用于 = 和 IN 查询）
     * 
     * @param availableTargetNames 可用的数据源名称列表（ds0, ds1）
     * @param shardingValue 分片值（product_shard_id）
     * @return 目标数据源名称
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Integer> shardingValue) {
        Integer productShardId = shardingValue.getValue();
        
        if (productShardId == null) {
            log.warn("【ProductShardIdDatabaseShardingAlgorithmV2】product_shard_id 为 null，使用默认路由");
            return availableTargetNames.iterator().next();
        }
        
        // 验证 product_shard_id 范围
        if (productShardId < 0 || productShardId >= 1024) {
            log.warn("【ProductShardIdDatabaseShardingAlgorithmV2】product_shard_id {} 超出范围（0-1023），使用默认路由", productShardId);
            return availableTargetNames.iterator().next();
        }
        
        String dsName;
        
        // 使用路由表查询
        if (useRoutingTable) {
            try {
                ProductRouteCache cache = getRouteCache();
                dsName = cache.getDataSourceName(productShardId);
                
                // 检查是否在迁移中（迁移中返回源库）
                if (cache.isMigrating(productShardId)) {
                    log.debug("【ProductShardIdDatabaseShardingAlgorithmV2】bucket {} 正在迁移中，使用源库 {}", productShardId, dsName);
                }
            } catch (Exception e) {
                log.warn("【ProductShardIdDatabaseShardingAlgorithmV2】路由表查询失败，product_shard_id={}, error={}", 
                        productShardId, e.getMessage());
                
                // 降级为取模
                if (fallbackToMod) {
                    log.info("【ProductShardIdDatabaseShardingAlgorithmV2】降级为取模算法");
                    int dsIndex = Math.floorMod(productShardId, dsCount);
                    dsName = dsPrefix + dsIndex;
                } else {
                    throw new IllegalStateException("路由表查询失败且未启用降级", e);
                }
            }
        } else {
            // 直接使用取模（兼容模式）
            int dsIndex = Math.floorMod(productShardId, dsCount);
            dsName = dsPrefix + dsIndex;
        }
        
        // 必须校验 availableTargetNames，避免返回不存在的 ds（配置错误）
        if (!availableTargetNames.contains(dsName)) {
            String errorMsg = String.format(
                "product_shard_id %d 路由到 %s，但该库不在可用列表中（可用列表: %s）。" +
                "可能原因：1) ShardingSphere 配置缺少该数据源；2) 路由表配置错误。",
                productShardId, dsName, availableTargetNames);
            log.error("【ProductShardIdDatabaseShardingAlgorithmV2】{}", errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        log.debug("【ProductShardIdDatabaseShardingAlgorithmV2】product_shard_id {} -> {}", productShardId, dsName);
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
        log.debug("【ProductShardIdDatabaseShardingAlgorithmV2】范围查询，返回所有可用库");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "PRODUCT_SHARD_ID_DATABASE_V2";
    }
}


