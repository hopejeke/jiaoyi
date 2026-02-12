package com.jiaoyi.product.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 商品域表分片算法（基于 product_shard_id）
 * 
 * 功能：
 * 1. 根据 product_shard_id 计算表索引：table_idx = product_shard_id % 4
 * 2. 生成物理表名：table_name = logicTableName + "_" + twoDigits(table_idx)
 * 
 * 配置参数：
 * - table.count.per.db: 每库表数量（默认4）
 */
@Slf4j
public class ProductShardIdTableShardingAlgorithm implements StandardShardingAlgorithm<Integer> {
    
    private int tableCountPerDb = 4; // 默认4张表/库
    
    @Override
    public void init(Properties props) {
        // 从 Properties 中读取 table.count.per.db（如果有）
        if (props != null && props.containsKey("table.count.per.db")) {
            try {
                tableCountPerDb = Integer.parseInt(props.getProperty("table.count.per.db"));
            } catch (NumberFormatException e) {
                log.warn("【ProductShardIdTableShardingAlgorithm】无法解析 table.count.per.db，使用默认值 4", e);
            }
        }
        log.info("【ProductShardIdTableShardingAlgorithm】初始化完成，每库表数量: {}", tableCountPerDb);
    }
    
    /**
     * 设置每库表数量（由配置类设置）
     */
    public void setTableCountPerDb(int tableCountPerDb) {
        this.tableCountPerDb = tableCountPerDb;
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
        if (productShardId == null) {
            throw new IllegalArgumentException("product_shard_id 不能为空");
        }
        
        // 计算表索引：product_shard_id % tableCountPerDb
        int tableIndex = productShardId % tableCountPerDb;
        
        // 生成表名后缀（两位数字符串）
        String tableSuffix = String.format("%02d", tableIndex);
        
        // 从逻辑表名获取前缀（例如：store_products -> store_products_00）
        String logicTableName = shardingValue.getLogicTableName();
        String targetTableName = logicTableName + "_" + tableSuffix;
        
        // 验证目标表是否在可用列表中
        if (!availableTargetNames.contains(targetTableName)) {
            throw new IllegalStateException(
                String.format("product_shard_id %d 计算出的表 %s 不在可用列表 %s 中", 
                    productShardId, targetTableName, availableTargetNames));
        }
        
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
        // 范围查询需要查询所有可能的表
        // 这里简化处理：返回所有可用表（实际应该根据范围计算）
        // 注意：范围查询性能较差，建议业务层避免使用
        return availableTargetNames;
    }
    
    public String getType() {
        return "PRODUCT_SHARD_ID_TABLE";
    }
}

