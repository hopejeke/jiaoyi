package com.jiaoyi.product.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import com.jiaoyi.product.service.WorkerIdAllocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/**
 * ShardingSphere 5.4.1 配置
 * 使用Java API配置方式，完全避免snakeyaml版本冲突
 * 这样可以保持 Spring Boot 3.2.0 + JDK 21 的兼容性
 * @author Administrator
 */
@Configuration
@org.springframework.core.annotation.Order(3) // 在DatabaseInitializer、WorkerIdAllocator和ProductRouteCache之后执行
public class ShardingSphereConfig {
    
    /**
     * Worker-ID 分配器（优先使用）
     * 使用数据库表动态分配 worker-id
     */
    @Autowired(required = false)
    private WorkerIdAllocator workerIdAllocator;
    
    /**
     * 雪花算法 worker-id（工作机器ID，0-1023）
     * 如果不配置，ShardingSphere 会自动生成
     * 多实例部署时，建议为每个实例配置不同的 worker-id，避免ID冲突
     * 可以通过环境变量 SNOWFLAKE_WORKER_ID 或配置文件设置
     * 优先级：WorkerIdAllocator > 配置文件 > Pod名称 > 自动生成
     */
    @Value("${shardingsphere.snowflake.worker-id:}")
    private String workerId;
    
    @Value("${HOSTNAME:}")
    private String hostname;

    @Bean(name = "shardingSphereDataSource")
    public DataSource shardingSphereDataSource() throws SQLException {
        // 创建数据源Map
        Map<String, DataSource> dataSourceMap = createDataSourceMap();
        
        // 创建分片规则配置
        ShardingRuleConfiguration shardingRuleConfig = createShardingRuleConfiguration();
        
        // 创建属性配置
        Properties props = new Properties();
        props.setProperty("sql-show", "true");
        // 禁用元数据校验，允许表结构动态变化（开发环境）
        // 注意：生产环境建议启用校验以确保数据一致性
        props.setProperty("check-table-metadata-enabled", "false");
        
        // 创建ShardingSphere数据源
        // 注意：outbox_node 和 outbox 表不在 ShardingSphere 中配置，使用普通数据源连接（见 DataSourceConfig）
        // ShardingSphere 负责分片表（store_products, inventory）
        return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, List.of(shardingRuleConfig), props);
    }
    
    /**
     * 创建数据源Map（只包含分片数据库，不包含基础数据库 jiaoyi）
     * 基础数据库 jiaoyi 使用普通数据源连接（见 DataSourceConfig）
     */
    private Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        // 商品服务专用数据库：jiaoyi_product_0/1/2
        HikariDataSource ds0 = new HikariDataSource();
        ds0.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds0.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_product_0?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds0.setUsername("root");
        ds0.setPassword("root");
        dataSourceMap.put("ds0", ds0);
        
        HikariDataSource ds1 = new HikariDataSource();
        ds1.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds1.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_product_1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds1.setUsername("root");
        ds1.setPassword("root");
        dataSourceMap.put("ds1", ds1);
        
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds2.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_product_2?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds2.setUsername("root");
        ds2.setPassword("root");
        dataSourceMap.put("ds2", ds2);
        
        return dataSourceMap;
    }
    
    /**
     * 创建分片规则配置
     */
    private ShardingRuleConfiguration createShardingRuleConfiguration() {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        
        // 注意：不要设置默认数据源！
        // 如果设置了默认数据源，没有分片键的查询（如 selectAll）会只查询默认数据源，而不是查询所有分片
        // 对于 store_products 表，所有查询都应该通过 ShardingSphere 路由到正确的分片
        
        // 配置分片表（只配置需要分片的表）
        // 注意：outbox_node 和 outbox 表不在 ShardingSphere 中配置，使用普通数据源连接
        shardingRuleConfig.getTables().add(createStoreProductsTableRule());
        shardingRuleConfig.getTables().add(createProductSkuTableRule());
        shardingRuleConfig.getTables().add(createInventoryTableRule());
        
        // online-order-v2 相关分片表（订单相关已迁移到 order-service）
        shardingRuleConfig.getTables().add(createMerchantsTableRule());
        shardingRuleConfig.getTables().add(createStoreServicesTableRule());
        shardingRuleConfig.getTables().add(createMenuItemsTableRule());
        // 订单表已迁移到 order-service，不再在此配置
        
        // outbox 表（分库不分表，按 store_id 分库路由，与商品表一致）
        shardingRuleConfig.getTables().add(createOutboxTableRule());
        
        // 配置绑定表（store_products, product_sku, inventory 绑定，确保同一店铺的数据在同一分片）
        // 注意：outbox 表不加入 bindingTables，因为 outbox 表只分库不分表，而商品表分库+分表
        // 但 outbox 表使用相同的分库策略（store_id），仍然可以保证同库事务
        ShardingTableReferenceRuleConfiguration bindingTableRule = new ShardingTableReferenceRuleConfiguration("product_binding", 
            "store_products,product_sku,inventory");
        shardingRuleConfig.getBindingTableGroups().add(bindingTableRule);
        
        // 配置分片算法（使用 product_shard_id，纯函数计算）
        // 数据库路由：product_shard_id % dsCount -> ds0/ds1/ds2
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_database", 
            createProductShardIdDatabaseAlgorithm());
        // 表路由：product_shard_id % 32 -> table_00..table_31
        Properties tableShardingProps = new Properties();
        tableShardingProps.setProperty("strategy", "STANDARD");
        tableShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithm");
        tableShardingProps.setProperty("table.count.per.db", "32");
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingProps));
        
        // online-order-v2 表分片算法（基于 merchant_id，使用字符串哈希）
        // merchant_id 是字符串，使用自定义算法类进行分片
        // 注意：INLINE 算法不支持字符串的 hashCode()，需要使用自定义算法
        shardingRuleConfig.getShardingAlgorithms().put("merchants_database_hash", 
            createClassBasedAlgorithm("com.jiaoyi.product.config.MerchantIdDatabaseShardingAlgorithm"));
        shardingRuleConfig.getShardingAlgorithms().put("merchants_table_hash", 
            createClassBasedAlgorithm("com.jiaoyi.product.config.MerchantIdTableShardingAlgorithm"));
        
        // 配置分布式主键生成策略（雪花算法）
        // 解决分库分表环境下主键重复的问题
        // 每个分片表独立自增会导致主键重复，使用雪花算法生成全局唯一ID
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建商品表分片规则
     * 使用 product_shard_id 作为分片键（基于 storeId 计算，固定1024个虚拟桶）
     * 物理分表：32 张表（store_products_00..store_products_31）
     */
    private ShardingTableRuleConfiguration createStoreProductsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（store_products_00..store_products_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("store_products", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_products", actualDataNodes);
        // 使用 product_shard_id 作为分片键（数据库路由：product_shard_id % dsCount）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        // 使用 product_shard_id 作为分片键（表路由：product_shard_id % 32）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商品SKU表分片规则
     * 使用 product_shard_id 作为分片键，与 store_products 表保持一致
     */
    private ShardingTableRuleConfiguration createProductSkuTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（product_sku_00..product_sku_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("product_sku", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("product_sku", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建库存表分片规则
     * 使用 product_shard_id 作为分片键，与 store_products 表保持一致
     * 库存按SKU级别管理，sku_id为NULL时表示商品级别库存（兼容旧数据）
     */
    private ShardingTableRuleConfiguration createInventoryTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（inventory_00..inventory_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("inventory", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建内联分片算法配置
     */
    private AlgorithmConfiguration createInlineAlgorithm(String algorithmExpression) {
        Properties props = new Properties();
        props.setProperty("algorithm-expression", algorithmExpression);
        return new AlgorithmConfiguration("INLINE", props);
    }
    
    /**
     * 创建餐馆表分片规则
     * 基于 merchant_id（字符串）进行分片
     */
    private ShardingTableRuleConfiguration createMerchantsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchants", 
            "ds${0..2}.merchants_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_database_hash"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_table_hash"));
        // 配置主键生成策略：使用雪花算法生成全局唯一ID
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建餐馆服务表分片规则
     * 使用与餐馆表相同的分片策略（基于 merchant_id）
     */
    private ShardingTableRuleConfiguration createStoreServicesTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_services", 
            "ds${0..2}.store_services_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_database_hash"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_table_hash"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建菜单项信息表分片规则
     * 使用与餐馆表相同的分片策略（基于 merchant_id）
     */
    private ShardingTableRuleConfiguration createMenuItemsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("menu_items", 
            "ds${0..2}.menu_items_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_database_hash"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchants_table_hash"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    // 订单表分片规则已迁移到 order-service，不再在此配置
    
    /**
     * 创建 outbox 表规则（分库+分表，32张表/库：outbox_00..outbox_31）
     * 使用 product_shard_id 作为分片键，与商品表保持一致
     * 
     * 注意：
     * 1. 分库路由：product_shard_id % dsCount -> ds0/ds1/ds2
     * 2. 表路由：product_shard_id % 32 -> outbox_00..outbox_31
     * 3. 业务方需要在插入时计算并设置 product_shard_id（基于 storeId）
     */
    private ShardingTableRuleConfiguration createOutboxTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（outbox_00..outbox_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("outbox", 3, 32);
        
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("outbox", actualDataNodes);
        
        // 使用 product_shard_id 作为分片键（数据库路由：product_shard_id % dsCount）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        // 使用 product_shard_id 作为分片键（表路由：product_shard_id % 32）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        // 不设置 KeyGenerateStrategy，因为 outbox 表使用 AUTO_INCREMENT
        return tableRule;
    }
    
    /**
     * 构建实际数据节点字符串（显式拼接，确保补零正确）
     * 
     * @param tableName 表名前缀（如 "outbox", "store_products"）
     * @param dbCount 数据库数量（默认 3）
     * @param tableCountPerDb 每个库的表数量（默认 32）
     * @return 实际数据节点字符串，如 "ds0.outbox_00,ds0.outbox_01,...,ds2.outbox_31"
     */
    private String buildActualDataNodes(String tableName, int dbCount, int tableCountPerDb) {
        StringBuilder actualDataNodes = new StringBuilder();
        for (int dbIndex = 0; dbIndex < dbCount; dbIndex++) {
            for (int tableIndex = 0; tableIndex < tableCountPerDb; tableIndex++) {
                if (dbIndex > 0 || tableIndex > 0) {
                    actualDataNodes.append(",");
                }
                actualDataNodes.append("ds").append(dbIndex)
                    .append(".").append(tableName).append("_")
                    .append(String.format("%02d", tableIndex));
            }
        }
        return actualDataNodes.toString();
    }
    
    /**
     * 创建商品域数据库分片算法（基于 product_shard_id，纯函数实现）
     * 使用 product_shard_id % dsCount 计算数据库索引
     */
    private AlgorithmConfiguration createProductShardIdDatabaseAlgorithm() {
        Properties props = new Properties();
        props.setProperty("strategy", "STANDARD");
        props.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithm");
        // 配置数据源数量和前缀
        props.setProperty("ds-count", "3");
        props.setProperty("ds-prefix", "ds");
        return new AlgorithmConfiguration("CLASS_BASED", props);
    }
    
    /**
     * 创建基于类的分片算法配置
     */
    private AlgorithmConfiguration createClassBasedAlgorithm(String algorithmClassName) {
        Properties props = new Properties();
        props.setProperty("strategy", "STANDARD");
        props.setProperty("algorithmClassName", algorithmClassName);
        return new AlgorithmConfiguration("CLASS_BASED", props);
    }
    
    /**
     * 创建雪花算法主键生成器配置
     * 雪花算法生成的ID格式：64位长整型
     * - 1位符号位（0）
     * - 41位时间戳（毫秒级）
     * - 10位机器ID（5位数据中心ID + 5位机器ID）
     * - 12位序列号
     * 
     * 优点：
     * - 全局唯一
     * - 趋势递增（按时间有序）
     * - 性能高（本地生成，无需数据库交互）
     * - 适合分布式环境
     * 
     * worker-id 配置优先级：
     * 1. WorkerIdAllocator（数据库表动态分配，推荐）
     * 2. 配置文件/环境变量 shardingsphere.snowflake.worker-id
     * 3. 从 Pod 名称提取（StatefulSet）
     * 4. ShardingSphere 自动生成
     */
    private AlgorithmConfiguration createSnowflakeKeyGenerator() {
        Properties props = new Properties();
        
        Integer workerIdValue = null;
        
        // 优先级1: 从 WorkerIdAllocator 获取（数据库表动态分配）
        if (workerIdAllocator != null) {
            workerIdValue = workerIdAllocator.getAllocatedWorkerId();
            if (workerIdValue != null) {
                props.setProperty("worker-id", String.valueOf(workerIdValue));
                System.out.println("✓ 雪花算法 worker-id 已从数据库动态分配: " + workerIdValue);
                return new AlgorithmConfiguration("SNOWFLAKE", props);
            }
        }
        
        // 优先级2: 从配置文件或环境变量读取
        if (workerId != null && !workerId.trim().isEmpty()) {
            try {
                workerIdValue = Integer.parseInt(workerId.trim());
                if (workerIdValue < 0 || workerIdValue > 1023) {
                    throw new IllegalArgumentException("worker-id 必须在 0-1023 之间，当前值: " + workerIdValue);
                }
                props.setProperty("worker-id", String.valueOf(workerIdValue));
                System.out.println("✓ 雪花算法 worker-id 已配置: " + workerIdValue);
                return new AlgorithmConfiguration("SNOWFLAKE", props);
            } catch (NumberFormatException e) {
                System.err.println("✗ 雪花算法 worker-id 配置错误，必须是 0-1023 之间的整数: " + workerId);
            }
        }
        
        // 优先级3: 从 Pod 名称提取（StatefulSet）
        if (hostname != null && !hostname.isEmpty()) {
            try {
                String number = hostname.replaceAll(".*?([0-9]+)$", "$1");
                if (!number.isEmpty() && number.matches("\\d+")) {
                    workerIdValue = Integer.parseInt(number);
                    if (workerIdValue >= 0 && workerIdValue <= 1023) {
                        props.setProperty("worker-id", String.valueOf(workerIdValue));
                        System.out.println("✓ 从 Pod 名称提取 worker-id: " + workerIdValue + " (hostname: " + hostname + ")");
                        return new AlgorithmConfiguration("SNOWFLAKE", props);
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        
        // 优先级4: ShardingSphere 自动生成
        System.out.println("ℹ 雪花算法 worker-id 未配置，ShardingSphere 将自动生成");
        return new AlgorithmConfiguration("SNOWFLAKE", props);
    }
    
}

