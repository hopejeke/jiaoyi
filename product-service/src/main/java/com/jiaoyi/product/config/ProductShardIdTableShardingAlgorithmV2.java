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
 * 商品域表分片算法 V2（基于路由表）
 * 
 * 核心逻辑：
 * 1. 从 product_shard_id 查询路由表获取 tbl_id
 * 2. 支持迁移状态（MIGRATING 时返回源表）
 * 3. 使用 ProductRouteCache 缓存，性能好
 * 
 * 配置参数：
 * - use-routing-table: 是否使用路由表（默认 true）
 * - fallback-to-mod: 路由表不可用时是否降级为取模（默认 false）
 * - table-count-per-db: 每库表数量（默认4，降级时使用）
 */
@Slf4j
public class ProductShardIdTableShardingAlgorithmV2 implements StandardShardingAlgorithm<Integer> {
    
    /**
     * 是否使用路由表（默认 true）
     */
    private boolean useRoutingTable = true;
    
    /**
     * 路由表不可用时是否降级为取模（默认 false）
     */
    private boolean fallbackToMod = false;
    
    /**
     * 每库表数量（降级时使用）
     */
    private int tableCountPerDb = 4;
    
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
        if (props.containsKey("table.count.per.db")) {
            tableCountPerDb = Integer.parseInt(props.getProperty("table.count.per.db"));
        }
        
        log.info("【ProductShardIdTableShardingAlgorithmV2】初始化完成，useRoutingTable={}, fallbackToMod={}, tableCountPerDb={}", 
                useRoutingTable, fallbackToMod, tableCountPerDb);
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
     * @param availableTargetNames 可用的表名列表（store_products_00..store_products_03）
     * @param shardingValue 分片值（product_shard_id）
     * @return 目标表名
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Integer> shardingValue) {
        Integer productShardId = shardingValue.getValue();
        String logicTableName = shardingValue.getLogicTableName();
        
        if (productShardId == null) {
            log.warn("【ProductShardIdTableShardingAlgorithmV2】product_shard_id 为 null，使用默认路由");
            return availableTargetNames.iterator().next();
        }
        
        // 验证 product_shard_id 范围
        if (productShardId < 0 || productShardId >= 1024) {
            log.warn("【ProductShardIdTableShardingAlgorithmV2】product_shard_id {} 超出范围（0-1023），使用默认路由", productShardId);
            return availableTargetNames.iterator().next();
        }
        
        int tblId;
        
        // 使用路由表查询
        if (useRoutingTable) {
            try {
                ProductRouteCache cache = getRouteCache();
                tblId = cache.getTableId(productShardId);
                
                // 检查是否在迁移中（迁移中返回源表）
                if (cache.isMigrating(productShardId)) {
                    log.debug("【ProductShardIdTableShardingAlgorithmV2】bucket {} 正在迁移中，使用源表 {}", productShardId, tblId);
                }
            } catch (Exception e) {
                log.warn("【ProductShardIdTableShardingAlgorithmV2】路由表查询失败，product_shard_id={}, error={}", 
                        productShardId, e.getMessage());
                
                // 降级为取模
                if (fallbackToMod) {
                    log.info("【ProductShardIdTableShardingAlgorithmV2】降级为取模算法");
                    tblId = productShardId % tableCountPerDb;
                } else {
                    throw new IllegalStateException("路由表查询失败且未启用降级", e);
                }
            }
        } else {
            // 直接使用取模（兼容模式）
            tblId = productShardId % tableCountPerDb;
        }
        
        // 生成表名后缀（两位数字符串）
        String tableSuffix = String.format("%02d", tblId);
        String targetTableName = logicTableName + "_" + tableSuffix;
        
        // 校验目标表是否在可用列表中
        if (!availableTargetNames.contains(targetTableName)) {
            throw new IllegalStateException(
                String.format("product_shard_id %d 计算出的表 %s 不在可用列表 %s 中", 
                    productShardId, targetTableName, availableTargetNames));
        }
        
        log.debug("【ProductShardIdTableShardingAlgorithmV2】product_shard_id {} -> table {}", productShardId, targetTableName);
        return targetTableName;
    }
    
    /**
     * 范围分片（用于 BETWEEN 和 < > 查询）
     * 
     * @param availableTargetNames 可用的表名列表
     * @param shardingValue 分片值范围
     * @return 目标表名列表
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Integer> shardingValue) {
        // 范围查询：返回所有可能的表
        log.debug("【ProductShardIdTableShardingAlgorithmV2】范围查询，返回所有可用表");
        return availableTargetNames;
    }
    
    @Override
    public String getType() {
        return "PRODUCT_SHARD_ID_TABLE_V2";
    }
}


