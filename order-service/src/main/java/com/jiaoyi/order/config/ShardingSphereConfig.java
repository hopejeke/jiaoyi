package com.jiaoyi.order.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/**
 * ShardingSphere 5.4.1 配置（Order Service）
 * 配置 orders, order_items, order_coupons, payments 表的分片规则
 */
@Configuration
@org.springframework.core.annotation.Order(2) // 在DatabaseInitializer之后执行
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "databaseInitializer") // 确保 DatabaseInitializer 先执行
public class ShardingSphereConfig {
    
    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "shardingSphereDataSource")
    @org.springframework.context.annotation.Primary  // 业务分片库是主库
    public DataSource shardingSphereDataSource() throws SQLException {
        Map<String, DataSource> dataSourceMap = createDataSourceMap();
        ShardingRuleConfiguration shardingRuleConfig = createShardingRuleConfiguration();
        
        Properties props = new Properties();
        props.setProperty("sql-show", "true");
        // 禁用元数据校验，允许表结构动态变化（开发环境）
        // 注意：生产环境建议启用校验以确保数据一致性
        props.setProperty("check-table-metadata-enabled", "false");
        // 禁用元数据加载时的表存在性验证（允许表在执行时动态创建）
        props.setProperty("metadata-refresh-interval-seconds", "0");
        // 禁用 SQL 解析时的表存在性验证（ShardingSphere 5.4.1+）
        // 注意：这会让 ShardingSphere 在 SQL 执行时跳过表存在性检查
        props.setProperty("check-table-metadata-enabled", "false");
        
        return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, List.of(shardingRuleConfig), props);
    }
    
    private Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        // 基础数据库 jiaoyi（用于非分片表：webhook_event_log, payment_callback_log, consumer_log, doordash_webhook_log）
        HikariDataSource dsBase = new HikariDataSource();
        dsBase.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dsBase.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        dsBase.setUsername("root");
        dsBase.setPassword("root");
        dataSourceMap.put("ds_base", dsBase);
        
        // 订单服务专用数据库：jiaoyi_order_0/1/2
        HikariDataSource ds0 = new HikariDataSource();
        ds0.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds0.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_order_0?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds0.setUsername("root");
        ds0.setPassword("root");
        dataSourceMap.put("ds0", ds0);
        
        HikariDataSource ds1 = new HikariDataSource();
        ds1.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds1.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_order_1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds1.setUsername("root");
        ds1.setPassword("root");
        dataSourceMap.put("ds1", ds1);
        
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds2.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_order_2?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
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
        
        // 配置分片表：订单事实表（orders, order_items, order_coupons, payments, refunds, refund_items, deliveries, doordash_retry_task, outbox）
        // 使用 shard_id 作为分片键（基于 brandId 计算，固定1024个虚拟桶）
        // 注意：商户配置表（merchant_stripe_config, merchant_fee_config, merchant_capability_config）已迁移到基础库单表
        shardingRuleConfig.getTables().add(createOrdersTableRule());
        shardingRuleConfig.getTables().add(createOrderItemsTableRule());
        shardingRuleConfig.getTables().add(createOrderCouponsTableRule());
        shardingRuleConfig.getTables().add(createPaymentsTableRule());
        shardingRuleConfig.getTables().add(createRefundsTableRule());
        shardingRuleConfig.getTables().add(createRefundItemsTableRule());
        shardingRuleConfig.getTables().add(createDoorDashRetryTaskTableRule());
        shardingRuleConfig.getTables().add(createDeliveriesTableRule());
        shardingRuleConfig.getTables().add(createOutboxTableRule());
        
        // 配置非分片表（单表，存在于基础数据库 jiaoyi 中）
        shardingRuleConfig.getTables().add(createWebhookEventLogTableRule());
        shardingRuleConfig.getTables().add(createPaymentCallbackLogTableRule());
        shardingRuleConfig.getTables().add(createConsumerLogTableRule());
        shardingRuleConfig.getTables().add(createDoorDashWebhookLogTableRule());
        // 商户配置表（单表，存在于基础数据库 jiaoyi 中）
        shardingRuleConfig.getTables().add(createMerchantStripeConfigTableRule());
        shardingRuleConfig.getTables().add(createMerchantFeeConfigTableRule());
        shardingRuleConfig.getTables().add(createMerchantCapabilityConfigTableRule());
        
        // 配置绑定表（orders, order_items, order_coupons, payments, refunds, refund_items 绑定，确保同一订单的数据在同一分片）
        // 注意：所有表必须使用相同的分片策略（shard_id），才能配置为 binding table
        // outbox 表不加入 bindingTables，因为 outbox 表虽然也使用 shard_id，但业务语义不同（不是订单相关表）
        // 但 outbox 表使用相同的分片策略（shard_id），仍然可以保证同库事务
        ShardingTableReferenceRuleConfiguration bindingTableRule = new ShardingTableReferenceRuleConfiguration("order_binding", 
            "orders,order_items,order_coupons,payments,refunds,refund_items");
        shardingRuleConfig.getBindingTableGroups().add(bindingTableRule);
        
        // 配置分片算法（使用 merchant_id 作为分片键，字符串类型）
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_database", 
            createClassBasedAlgorithm("com.jiaoyi.order.config.MerchantIdDatabaseShardingAlgorithm"));
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_table", 
            createClassBasedAlgorithm("com.jiaoyi.order.config.MerchantIdTableShardingAlgorithm"));
        
        // 配置基于 store_id 的分片算法（用于 orders 和 outbox 表）
        // 数据库路由：store_id % dsCount -> ds0/ds1/ds2
        Properties dbShardingProps = new Properties();
        dbShardingProps.setProperty("strategy", "STANDARD");
        dbShardingProps.setProperty("algorithmClassName", "com.jiaoyi.order.config.StoreIdDatabaseShardingAlgorithm");
        dbShardingProps.setProperty("ds-count", "3"); // 数据源数量：ds0, ds1, ds2
        dbShardingProps.setProperty("ds-prefix", "ds"); // 数据源名称前缀
        shardingRuleConfig.getShardingAlgorithms().put("store_id_database", 
            new AlgorithmConfiguration("CLASS_BASED", dbShardingProps));
        // 表路由：基于 store_id 计算 shard_id，然后 shard_id % 32 -> table_00..table_31
        Properties tableShardingProps = new Properties();
        tableShardingProps.setProperty("strategy", "STANDARD");
        tableShardingProps.setProperty("algorithmClassName", "com.jiaoyi.order.config.StoreIdTableShardingAlgorithm");
        tableShardingProps.setProperty("table.count.per.db", "32");
        shardingRuleConfig.getShardingAlgorithms().put("store_id_table", 
            new AlgorithmConfiguration("CLASS_BASED", tableShardingProps));
        
        // 配置分布式主键生成策略（雪花算法）
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建订单表分片规则
     * 使用 store_id 作为分片键（与商品服务保持一致）
     * 物理分表：32 张表（orders_00..orders_31）
     */
    private ShardingTableRuleConfiguration createOrdersTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（orders_00..orders_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("orders", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("orders", actualDataNodes);
        
        // 使用 store_id 作为分片键（数据库路由：基于 store_id 计算）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        // 使用 store_id 作为分片键（表路由：基于 store_id 计算）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单项表分片规则（与 orders 绑定）
     * 使用 shard_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createOrderItemsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（order_items_00..order_items_31）
        String actualDataNodes = buildActualDataNodes("order_items", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_items", actualDataNodes);
        // order_items 表使用 store_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单优惠券关联表分片规则（与 orders 绑定）
     * 使用 shard_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createOrderCouponsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（order_coupons_00..order_coupons_31）
        String actualDataNodes = buildActualDataNodes("order_coupons", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_coupons", actualDataNodes);
        // order_coupons 表使用 store_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建支付记录表分片规则（与 orders 绑定）
     * 使用 shard_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createPaymentsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（payments_00..payments_31）
        String actualDataNodes = buildActualDataNodes("payments", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("payments", actualDataNodes);
        // payments 表使用 store_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建退款单表分片规则（与 orders 绑定）
     * 使用 shard_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createRefundsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（refunds_00..refunds_31）
        String actualDataNodes = buildActualDataNodes("refunds", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("refunds", actualDataNodes);
        // refunds 表使用 store_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("refund_id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建退款明细表分片规则（与 refunds 绑定）
     * 使用 shard_id 作为分片键，通过 refund_id 关联到 refunds 表
     */
    private ShardingTableRuleConfiguration createRefundItemsTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（refund_items_00..refund_items_31）
        String actualDataNodes = buildActualDataNodes("refund_items", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("refund_items", actualDataNodes);
        // refund_items 表使用 store_id 作为分片键，与 refunds 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("refund_item_id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商户 Stripe 配置表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createMerchantStripeConfigTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchant_stripe_config", "ds_base.merchant_stripe_config");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建商户费用配置表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createMerchantFeeConfigTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchant_fee_config", "ds_base.merchant_fee_config");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建商户能力配置表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createMerchantCapabilityConfigTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchant_capability_config", "ds_base.merchant_capability_config");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建 DoorDash 重试任务表分片规则
     * 使用 shard_id 作为分片键（与 orders 表保持一致）
     */
    private ShardingTableRuleConfiguration createDoorDashRetryTaskTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（doordash_retry_task_00..doordash_retry_task_31）
        String actualDataNodes = buildActualDataNodes("doordash_retry_task", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("doordash_retry_task", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建配送表分片规则（deliveries）
     * 使用 store_id 作为分片键（与 orders 表保持一致）
     */
    private ShardingTableRuleConfiguration createDeliveriesTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（deliveries_00..deliveries_31）
        String actualDataNodes = buildActualDataNodes("deliveries", 3, 32);
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("deliveries", actualDataNodes);
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        // deliveries 表的主键是 id（VARCHAR），不是自增ID，所以不需要设置 KeyGenerateStrategy
        return tableRule;
    }
    
    private AlgorithmConfiguration createInlineAlgorithm(String algorithmExpression) {
        Properties props = new Properties();
        props.setProperty("algorithm-expression", algorithmExpression);
        return new AlgorithmConfiguration("INLINE", props);
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
    
    private AlgorithmConfiguration createSnowflakeKeyGenerator() {
        Properties props = new Properties();
        return new AlgorithmConfiguration("SNOWFLAKE", props);
    }
    
    /**
     * 创建 Webhook 事件日志表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createWebhookEventLogTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("webhook_event_log", "ds_base.webhook_event_log");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建支付回调日志表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createPaymentCallbackLogTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("payment_callback_log", "ds_base.payment_callback_log");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建消费日志表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createConsumerLogTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("consumer_log", "ds_base.consumer_log");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建 DoorDash Webhook 日志表规则（非分片表，存在于基础数据库 jiaoyi）
     */
    private ShardingTableRuleConfiguration createDoorDashWebhookLogTableRule() {
        // 单表配置：只存在于 ds_base 数据源中，不进行分片
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("doordash_webhook_log", "ds_base.doordash_webhook_log");
        // 不设置分片策略，表示这是一个单表
        return tableRule;
    }
    
    /**
     * 创建 outbox 表规则（分库+分表，按 shard_id 分片）
     * 使用 shard_id 作为分片键（基于 brandId 计算，固定1024个虚拟桶）
     * 物理分表：32 张表（outbox_00..outbox_31）
     * 
     * 注意：
     * 1. 分库路由：从 RouteCache 获取 shard_id -> ds_name 映射
     * 2. 表路由：shard_id % 32 -> outbox_00..outbox_31
     * 3. 业务方需要在插入时计算并设置 shard_id（基于 brandId）
     * 4. 表名统一为 outbox（不再使用 order_outbox），通过数据库隔离不同服务
     */
    private ShardingTableRuleConfiguration createOutboxTableRule() {
        // 实际数据节点：ds0/1/2 每个库都有 32 张表（outbox_00..outbox_31）
        // 注意：使用显式拼接，避免 inline expression ${00..31} 不补零的问题
        String actualDataNodes = buildActualDataNodes("outbox", 3, 32);
        
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("outbox", actualDataNodes);
        
        // 使用 store_id 作为分片键（数据库路由：基于 store_id 计算）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
        // 使用 store_id 作为分片键（表路由：基于 store_id 计算）
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_id_table"));
        // 不设置 KeyGenerateStrategy，因为 outbox 表使用 AUTO_INCREMENT
        return tableRule;
    }
    
    /**
     * 构建实际数据节点字符串（显式拼接，确保补零正确）
     * 
     * @param tableName 表名前缀（如 "orders", "outbox"）
     * @param dbCount 数据库数量（默认 3）
     * @param tableCountPerDb 每个库的表数量（默认 32）
     * @return 实际数据节点字符串，如 "ds0.orders_00,ds0.orders_01,...,ds2.orders_31"
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

