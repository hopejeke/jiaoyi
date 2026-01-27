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
        
        // 配置基于 product_shard_id 的分片算法（用于商品表）
        // 方案1：使用路由表（推荐，支持动态扩容）
        Properties dbShardingPropsV2 = new Properties();
        dbShardingPropsV2.setProperty("strategy", "STANDARD");
        dbShardingPropsV2.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithmV2");
        dbShardingPropsV2.setProperty("use-routing-table", "true"); // 使用路由表
        dbShardingPropsV2.setProperty("fallback-to-mod", "true"); // 路由表不可用时降级为取模
        dbShardingPropsV2.setProperty("ds-count", "3"); // 降级时使用
        dbShardingPropsV2.setProperty("ds-prefix", "ds"); // 降级时使用
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", dbShardingPropsV2));
        
        // 方案2：纯函数（兼容模式，保留备用）
        Properties dbShardingPropsOld = new Properties();
        dbShardingPropsOld.setProperty("strategy", "STANDARD");
        dbShardingPropsOld.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdDatabaseShardingAlgorithm");
        dbShardingPropsOld.setProperty("ds-count", "3");
        dbShardingPropsOld.setProperty("ds-prefix", "ds");
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_database_old", 
            new AlgorithmConfiguration("CLASS_BASED", dbShardingPropsOld));
        
        // 表路由：基于路由表查询 tbl_id
        Properties tableShardingPropsV2 = new Properties();
        tableShardingPropsV2.setProperty("strategy", "STANDARD");
        tableShardingPropsV2.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithmV2");
        tableShardingPropsV2.setProperty("use-routing-table", "true"); // 使用路由表
        tableShardingPropsV2.setProperty("fallback-to-mod", "true"); // 路由表不可用时降级为取模
        tableShardingPropsV2.setProperty("table.count.per.db", "32"); // 降级时使用
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingPropsV2));
        
        // 表路由：纯函数（兼容模式，保留备用）
        Properties tableShardingPropsOld = new Properties();
        tableShardingPropsOld.setProperty("strategy", "STANDARD");
        tableShardingPropsOld.setProperty("algorithmClassName", "com.jiaoyi.product.config.ProductShardIdTableShardingAlgorithm");
        tableShardingPropsOld.setProperty("table.count.per.db", "32");
        shardingRuleConfig.getShardingAlgorithms().put("product_shard_id_table_old", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingPropsOld));
        
        // 配置基于 store_id 的分片算法（用于 outbox 表）
        // 数据库路由：从 store_id 计算 product_shard_id，然后 product_shard_id % dsCount -> ds0/ds1/ds2
        Properties storeIdDbShardingProps = new Properties();
        storeIdDbShardingProps.setProperty("strategy", "STANDARD");
        storeIdDbShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.StoreIdDatabaseShardingAlgorithm");
        storeIdDbShardingProps.setProperty("ds-count", "3");
        storeIdDbShardingProps.setProperty("ds-prefix", "ds");
        shardingRuleConfig.getShardingAlgorithms().put("store_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", storeIdDbShardingProps));
        
        // 表路由：从 store_id 计算 product_shard_id，然后 product_shard_id % 32 -> table_00..table_31
        Properties storeIdTableShardingProps = new Properties();
        storeIdTableShardingProps.setProperty("strategy", "STANDARD");
        storeIdTableShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.StoreIdTableShardingAlgorithm");
        storeIdTableShardingProps.setProperty("table.count.per.db", "32");
        shardingRuleConfig.getShardingAlgorithms().put("store_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", storeIdTableShardingProps));
        
        // 配置基于 merchant_id 的分片算法（用于 merchants, store_services, menu_items 表）
        // 数据库路由：基于 merchant_id 哈希值计算
        Properties merchantIdDbShardingProps = new Properties();
        merchantIdDbShardingProps.setProperty("strategy", "STANDARD");
        merchantIdDbShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.MerchantIdDatabaseShardingAlgorithm");
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", merchantIdDbShardingProps));
        
        // 表路由：基于 merchant_id 哈希值计算，然后 % 3 -> table_0..table_2
        Properties merchantIdTableShardingProps = new Properties();
        merchantIdTableShardingProps.setProperty("strategy", "STANDARD");
        merchantIdTableShardingProps.setProperty("algorithmClassName", "com.jiaoyi.product.config.MerchantIdTableShardingAlgorithm");
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", merchantIdTableShardingProps));
        
        // 配置分布式主键生成策略（雪花算法）
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建商品表分片规则
     * 使用 product_shard_id 作为分片键
     * 物理分表：32 张表（store_products_00..store_products_31）
     */
    private ShardingTableRuleConfiguration createStoreProductsTableRule() {
        String actualDataNodes = buildActualDataNodes("store_products", 3, 32);
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
        String actualDataNodes = buildActualDataNodes("product_sku", 3, 32);
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
        String actualDataNodes = buildActualDataNodes("inventory", 3, 32);
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
        String actualDataNodes = buildActualDataNodes("inventory_transactions", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory_transactions", actualDataNodes);
        // inventory_transactions 表使用 product_shard_id 作为分片键，与 store_products 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 outbox 表规则（分库+分表，按 store_id 分片）
     * 注意：outbox 表使用 store_id 作为分片键，通过 StoreIdDatabaseShardingAlgorithm 和 StoreIdTableShardingAlgorithm
     * 内部会从 store_id 计算 product_shard_id，然后进行路由
     */
    private ShardingTableRuleConfiguration createOutboxTableRule() {
        String actualDataNodes = buildActualDataNodes("outbox", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("outbox", actualDataNodes);
        // outbox 表使用 store_id 作为分片键，使用专门处理 store_id 的分片算法
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        // 不设置 KeyGenerateStrategy，因为 outbox 表使用 AUTO_INCREMENT
        return tableRule;
    }
    
    /**
     * 创建 merchants 表规则（分片表，基于 merchant_id 分片）
     * 物理分表：每个库3张表（merchants_0..merchants_2）
     */
    private ShardingTableRuleConfiguration createMerchantsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 3 张表（merchants_0..merchants_2）
        StringBuilder actualDataNodes = new StringBuilder();
        for (int dbIndex = 0; dbIndex < 3; dbIndex++) {
            for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
                if (dbIndex > 0 || tableIndex > 0) {
                    actualDataNodes.append(",");
                }
                actualDataNodes.append("ds").append(dbIndex).append(".merchants_").append(tableIndex);
            }
        }
        
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchants", actualDataNodes.toString());
        // 使用 merchant_id 作为分片键（数据库路由：基于 merchant_id 计算）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        // 使用 merchant_id 作为分片键（表路由：基于 merchant_id 计算）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 store_services 表规则（分片表，基于 merchant_id 分片）
     * 物理分表：每个库3张表（store_services_0..store_services_2）
     */
    private ShardingTableRuleConfiguration createStoreServicesTableRule() {
        StringBuilder actualDataNodes = new StringBuilder();
        for (int dbIndex = 0; dbIndex < 3; dbIndex++) {
            for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
                if (dbIndex > 0 || tableIndex > 0) {
                    actualDataNodes.append(",");
                }
                actualDataNodes.append("ds").append(dbIndex).append(".store_services_").append(tableIndex);
            }
        }
        
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_services", actualDataNodes.toString());
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建 menu_items 表规则（分片表，基于 merchant_id 分片）
     * 物理分表：每个库3张表（menu_items_0..menu_items_2）
     */
    private ShardingTableRuleConfiguration createMenuItemsTableRule() {
        StringBuilder actualDataNodes = new StringBuilder();
        for (int dbIndex = 0; dbIndex < 3; dbIndex++) {
            for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
                if (dbIndex > 0 || tableIndex > 0) {
                    actualDataNodes.append(",");
                }
                actualDataNodes.append("ds").append(dbIndex).append(".menu_items_").append(tableIndex);
            }
        }
        
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("menu_items", actualDataNodes.toString());
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
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
        String actualDataNodes = "ds0.inventory_deduction_idempotency,ds1.inventory_deduction_idempotency,ds2.inventory_deduction_idempotency";
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

