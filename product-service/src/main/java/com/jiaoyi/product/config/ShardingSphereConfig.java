package com.jiaoyi.product.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

// 导入分片算法类（用于 IDE 识别，ShardingSphere 通过反射动态加载）
import com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithm;
import com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithm;

/**
 * ShardingSphere 5.4.1 配置（Product Service）
 * 配置 store_products, product_sku, inventory 表的分片规则
 * 
 * 分片策略（优化版）：
 * - 商品域绑定表（store_products, product_sku, inventory, inventory_transactions）：2库 × 4表 = 8 物理表/逻辑表
 * - Outbox：2库 × 1表 = 2 物理表（不分表，临时数据无需分表）
 * - Merchant 域（merchants, store_services, menu_items）：2库 × 4表 = 8 物理表/逻辑表（与商品域统一 product_shard_id 分片）
 * - 虚拟桶：1024个，通过路由表映射到物理分片，支持无代码扩容
 */
@Configuration
@org.springframework.core.annotation.Order(2) // 在DatabaseInitializer之后执行
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "databaseInitializer") // 确保 DatabaseInitializer 先执行
public class ShardingSphereConfig {

    @Bean(name = "shardingSphereDataSource")
    public DataSource shardingSphereDataSource() throws SQLException {
        Map<String, DataSource> dataSourceMap = createDataSourceMap();
        ShardingRuleConfiguration shardingRuleConfig = createShardingRuleConfiguration();
        
        Properties props = new Properties();
        props.setProperty("sql-show", "true");
        // 禁用元数据校验，允许表结构动态变化（开发环境）
        props.setProperty("check-table-metadata-enabled", "false");
        props.setProperty("metadata-refresh-interval-seconds", "0");
        
        return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, List.of(shardingRuleConfig), props);
    }
    
    private Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        // 基础数据库 jiaoyi（用于非分片表：stores, users, outbox_node, snowflake_worker）
        HikariDataSource dsBase = new HikariDataSource();
        dsBase.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dsBase.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        dsBase.setUsername("root");
        dsBase.setPassword("root");
        dataSourceMap.put("ds_base", dsBase);
        
        // 商品服务专用数据库：jiaoyi_product_0/1（2库，优化后无需3库）
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
        
        return dataSourceMap;
    }
    
    private ShardingRuleConfiguration createShardingRuleConfiguration() {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        
        try {
            java.lang.reflect.Method setDefaultDataSourceMethod = 
                ShardingRuleConfiguration.class.getMethod("setDefaultDataSourceName", String.class);
            setDefaultDataSourceMethod.invoke(shardingRuleConfig, "ds0");
        } catch (Exception e) {
            // 忽略
        }
        
        // 配置分片表：商品事实表（store_products, product_sku, inventory, inventory_transactions）
        shardingRuleConfig.getTables().add(createStoreProductsTableRule());
        shardingRuleConfig.getTables().add(createProductSkuTableRule());
        shardingRuleConfig.getTables().add(createInventoryTableRule());
        shardingRuleConfig.getTables().add(createInventoryTransactionsTableRule());
        shardingRuleConfig.getTables().add(createOutboxTableRule());
        
        // 配置 online-order-v2 分片表（merchants, store_services, menu_items）
        shardingRuleConfig.getTables().add(createMerchantsTableRule());
        shardingRuleConfig.getTables().add(createStoreServicesTableRule());
        shardingRuleConfig.getTables().add(createMenuItemsTableRule());
        
        // 配置非分片表（单表，存在于基础数据库 jiaoyi 中）
        shardingRuleConfig.getTables().add(createStoresTableRule());
        shardingRuleConfig.getTables().add(createUsersTableRule());
        shardingRuleConfig.getTables().add(createOutboxNodeTableRule());
        shardingRuleConfig.getTables().add(createSnowflakeWorkerTableRule());
        
        // 配置库存扣减幂等性日志表（每个分片库一张表，不分片）
        shardingRuleConfig.getTables().add(createInventoryDeductionIdempotencyTableRule());
        
        // 配置绑定表（store_products, product_sku, inventory, inventory_transactions 绑定，确保同一商品的数据在同一分片）
        ShardingTableReferenceRuleConfiguration bindingTableRule = new ShardingTableReferenceRuleConfiguration("product_binding", 
            "store_products,product_sku,inventory,inventory_transactions");
        shardingRuleConfig.getBindingTableGroups().add(bindingTableRule);
        
        // 配置 Merchant 域绑定表（merchants, store_services, menu_items 绑定，统一使用 product_shard_id 分片）
        ShardingTableReferenceRuleConfiguration merchantBindingTableRule = new ShardingTableReferenceRuleConfiguration("merchant_binding", 
            "merchants,store_services,menu_items");
        shardingRuleConfig.getBindingTableGroups().add(merchantBindingTableRule);
        
        // 配置基于 product_shard_id 的分片算法（用于商品表）
        // 方案1：使用路由表（推荐，支持动态扩容）
        Properties dbShardingPropsV2 = new Properties();
        dbShardingPropsV2.setProperty("strategy", "STANDARD");
        dbShardingPropsV2.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithmV2");
        dbShardingPropsV2.setProperty("use-routing-table", "true"); // 使用路由表
        dbShardingPropsV2.setProperty("fallback-to-mod", "true"); // 路由表不可用时降级为取模
        dbShardingPropsV2.setProperty("ds-count", "2"); // 降级时使用
        dbShardingPropsV2.setProperty("ds-prefix", "ds"); // 降级时使用
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", dbShardingPropsV2));
        
        // 方案2：纯函数（兼容模式，保留备用）
        Properties dbShardingPropsOld = new Properties();
        dbShardingPropsOld.setProperty("strategy", "STANDARD");
        dbShardingPropsOld.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithm");
        dbShardingPropsOld.setProperty("ds-count", "2");
        dbShardingPropsOld.setProperty("ds-prefix", "ds");
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_database_old", 
            new AlgorithmConfiguration("CLASS_BASED", dbShardingPropsOld));
        
        // 表路由：基于路由表查询 tbl_id
        Properties tableShardingPropsV2 = new Properties();
        tableShardingPropsV2.setProperty("strategy", "STANDARD");
        tableShardingPropsV2.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithmV2");
        tableShardingPropsV2.setProperty("use-routing-table", "true"); // 使用路由表
        tableShardingPropsV2.setProperty("fallback-to-mod", "true"); // 路由表不可用时降级为取模
        tableShardingPropsV2.setProperty("table.count.per.db", "4"); // 降级时使用
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingPropsV2));
        
        // 表路由：纯函数（兼容模式，保留备用）
        Properties tableShardingPropsOld = new Properties();
        tableShardingPropsOld.setProperty("strategy", "STANDARD");
        tableShardingPropsOld.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithm");
        tableShardingPropsOld.setProperty("table.count.per.db", "4");
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_table_old", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingPropsOld));
        
        // 配置基于 store_id 的分片算法（用于 outbox 表）
        // 数据库路由：从 store_id 计算 product_shard_id，然后 product_shard_id % dsCount -> ds0/ds1
        Properties storeIdDbShardingProps = new Properties();
        storeIdDbShardingProps.setProperty("strategy", "STANDARD");
        storeIdDbShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.StoreIdDatabaseShardingAlgorithm");
        storeIdDbShardingProps.setProperty("ds-count", "2");
        storeIdDbShardingProps.setProperty("ds-prefix", "ds");
        shardingRuleConfig.getShardingAlgorithms().put("store_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", storeIdDbShardingProps));
        
        // 表路由：从 store_id 计算 product_shard_id，然后 product_shard_id % 4 -> table_00..table_03
        Properties storeIdTableShardingProps = new Properties();
        storeIdTableShardingProps.setProperty("strategy", "STANDARD");
        storeIdTableShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.StoreIdTableShardingAlgorithm");
        storeIdTableShardingProps.setProperty("table.count.per.db", "4");
        shardingRuleConfig.getShardingAlgorithms().put("store_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", storeIdTableShardingProps));
        
        // Merchant 域表已统一使用 product_shard_id 分片算法（与商品域共享 product_shard_id_database / product_shard_id_table）
        // 不再需要独立的 merchant_id 分片算法
        
        // 配置分布式主键生成策略（雪花算法）
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建商品表分片规则
     * 使用 product_shard_id 作为分片键
     * 物理分表：4 张表/库（store_products_00..store_products_03）
     */
    private ShardingTableRuleConfiguration createStoreProductsTableRule() {
        String actualDataNodes = buildActualDataNodes("store_products", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_products", actualDataNodes);
        
        // 使用 product_shard_id 作为分片键（数据库路由：基于 product_shard_id 计算）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        // 使用 product_shard_id 作为分片键（表路由：基于 product_shard_id 计算）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商品SKU表分片规则（与 store_products 绑定）
     */
    private ShardingTableRuleConfiguration createProductSkuTableRule() {
        String actualDataNodes = buildActualDataNodes("product_sku", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("product_sku", actualDataNodes);
        // product_sku 表使用 product_shard_id 作为分片键，与 store_products 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建库存表分片规则（与 store_products 绑定）
     */
    private ShardingTableRuleConfiguration createInventoryTableRule() {
        String actualDataNodes = buildActualDataNodes("inventory", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory", actualDataNodes);
        // inventory 表使用 product_shard_id 作为分片键，与 store_products 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建库存变动记录表分片规则（与 store_products 绑定）
     */
    private ShardingTableRuleConfiguration createInventoryTransactionsTableRule() {
        String actualDataNodes = buildActualDataNodes("inventory_transactions", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory_transactions", actualDataNodes);
        // inventory_transactions 表使用 product_shard_id 作为分片键，与 store_products 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 outbox 表规则（仅分库，不分表）
     * Outbox 是临时中转数据，正常运行时数据量极小，无需分表
     * 使用 store_id 做数据库路由，确保同一门店的 outbox 消息在同一库
     */
    private ShardingTableRuleConfiguration createOutboxTableRule() {
        // 2库 × 1表 = 2张物理表，表名无后缀
        String actualDataNodes = "ds0.outbox,ds1.outbox";
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("outbox", actualDataNodes);
        // outbox 表使用 store_id 作为分片键，仅做数据库路由
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        // 不设置表分片策略（每库只有1张 outbox 表）
        return tableRule;
    }
    
    /**
     * 创建 merchants 表规则（分片表，统一使用 product_shard_id 分片）
     * 物理分表：每个库4张表（merchants_00..merchants_03），2库共8张
     * 与商品域共享分片策略，确保同一门店数据在同一分片
     */
    private ShardingTableRuleConfiguration createMerchantsTableRule() {
        String actualDataNodes = buildActualDataNodes("merchants", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchants", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 store_services 表规则（分片表，统一使用 product_shard_id 分片）
     * 物理分表：每个库4张表（store_services_00..store_services_03），2库共8张
     */
    private ShardingTableRuleConfiguration createStoreServicesTableRule() {
        String actualDataNodes = buildActualDataNodes("store_services", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_services", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 menu_items 表规则（分片表，统一使用 product_shard_id 分片）
     * 物理分表：每个库4张表（menu_items_00..menu_items_03），2库共8张
     */
    private ShardingTableRuleConfiguration createMenuItemsTableRule() {
        String actualDataNodes = buildActualDataNodes("menu_items", 2, 4);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("menu_items", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 stores 表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createStoresTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("stores", "ds_base.stores");
        return tableRule;
    }
    
    /**
     * 创建 users 表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createUsersTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("users", "ds_base.users");
        return tableRule;
    }
    
    /**
     * 创建 outbox_node 表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createOutboxNodeTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("outbox_node", "ds_base.outbox_node");
        return tableRule;
    }
    
    /**
     * 创建 snowflake_worker 表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createSnowflakeWorkerTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("snowflake_worker", "ds_base.snowflake_worker");
        return tableRule;
    }
    
    /**
     * 创建库存扣减幂等性日志表规则（使用 product_shard_id 作为分片键）
     * 物理分表：每个库一张表（inventory_deduction_idempotency）
     */
    private ShardingTableRuleConfiguration createInventoryDeductionIdempotencyTableRule() {
        String actualDataNodes = "ds0.inventory_deduction_idempotency,ds1.inventory_deduction_idempotency";
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory_deduction_idempotency", actualDataNodes);
        // 使用 product_shard_id 作为分片键（数据库路由：基于 product_shard_id 计算）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        // 不分表（每个库只有一张表）
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    private AlgorithmConfiguration createSnowflakeKeyGenerator() {
        Properties props = new Properties();
        return new AlgorithmConfiguration("SNOWFLAKE", props);
    }
    
    /**
     * 构建实际数据节点字符串（显式拼接，确保补零正确）
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
}

