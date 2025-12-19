package com.jiaoyi.order.config;

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

/**
 * ShardingSphere 5.4.1 配置（Order Service）
 * 配置 orders, order_items, order_coupons, payments 表的分片规则
 */
@Configuration
@org.springframework.core.annotation.Order(2) // 在DatabaseInitializer之后执行
public class ShardingSphereConfig {

    @Bean(name = "shardingSphereDataSource")
    public DataSource shardingSphereDataSource() throws SQLException {
        Map<String, DataSource> dataSourceMap = createDataSourceMap();
        ShardingRuleConfiguration shardingRuleConfig = createShardingRuleConfiguration();
        
        Properties props = new Properties();
        props.setProperty("sql-show", "true");
        
        return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, List.of(shardingRuleConfig), props);
    }
    
    private Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        HikariDataSource ds0 = new HikariDataSource();
        ds0.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds0.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_0?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds0.setUsername("root");
        ds0.setPassword("root");
        dataSourceMap.put("ds0", ds0);
        
        HikariDataSource ds1 = new HikariDataSource();
        ds1.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds1.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds1.setUsername("root");
        ds1.setPassword("root");
        dataSourceMap.put("ds1", ds1);
        
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds2.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_2?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
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
        
        // 配置分片表：orders, order_items, order_coupons, payments, merchant_stripe_config, merchant_fee_config
        // 使用 merchant_id 作为分片键（在线点餐业务）
        shardingRuleConfig.getTables().add(createOrdersTableRule());
        shardingRuleConfig.getTables().add(createOrderItemsTableRule());
        shardingRuleConfig.getTables().add(createOrderCouponsTableRule());
        shardingRuleConfig.getTables().add(createPaymentsTableRule());
        shardingRuleConfig.getTables().add(createMerchantStripeConfigTableRule());
        shardingRuleConfig.getTables().add(createMerchantFeeConfigTableRule());
        
        // 配置绑定表（orders, order_items, order_coupons, payments 绑定，确保同一订单的数据在同一分片）
        ShardingTableReferenceRuleConfiguration bindingTableRule = new ShardingTableReferenceRuleConfiguration("order_binding", 
            "orders,order_items,order_coupons,payments");
        shardingRuleConfig.getBindingTableGroups().add(bindingTableRule);
        
        // 配置分片算法（使用 merchant_id 作为分片键，字符串类型）
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_database", 
            createClassBasedAlgorithm("com.jiaoyi.order.config.MerchantIdDatabaseShardingAlgorithm"));
        shardingRuleConfig.getShardingAlgorithms().put("merchant_id_table", 
            createClassBasedAlgorithm("com.jiaoyi.order.config.MerchantIdTableShardingAlgorithm"));
        
        // 配置分布式主键生成策略（雪花算法）
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建订单表分片规则
     * 使用 merchant_id 作为分片键（在线点餐业务）
     */
    private ShardingTableRuleConfiguration createOrdersTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("orders", 
            "ds${0..2}.orders_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单项表分片规则（与 orders 绑定）
     * 使用 merchant_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createOrderItemsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_items", 
            "ds${0..2}.order_items_${0..2}");
        // order_items 表使用 merchant_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单优惠券关联表分片规则（与 orders 绑定）
     * 注意：order_coupons 表可能没有 merchant_id 字段，需要通过 order_id 关联查询
     * 但为了分片一致性，建议在 order_coupons 表中也添加 merchant_id 字段
     * 这里暂时使用 order_id 分片（通过绑定表规则，ShardingSphere 会自动处理）
     */
    private ShardingTableRuleConfiguration createOrderCouponsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_coupons", 
            "ds${0..2}.order_coupons_${0..2}");
        // 如果 order_coupons 表有 merchant_id 字段，使用 merchant_id 分片
        // 否则需要通过绑定表规则，ShardingSphere 会根据关联的 orders 表自动路由
        // 这里假设 order_coupons 表也有 merchant_id 字段（如果没有，需要添加）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建支付记录表分片规则（与 orders 绑定）
     * 使用 merchant_id 作为分片键，确保与 orders 表在同一分片
     */
    private ShardingTableRuleConfiguration createPaymentsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("payments", 
            "ds${0..2}.payments_${0..2}");
        // payments 表使用 merchant_id 作为分片键，与 orders 表保持一致
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商户 Stripe 配置表分片规则
     * 使用 merchant_id 作为分片键
     */
    private ShardingTableRuleConfiguration createMerchantStripeConfigTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchant_stripe_config", 
            "ds${0..2}.merchant_stripe_config_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商户费用配置表分片规则
     * 使用 merchant_id 作为分片键
     */
    private ShardingTableRuleConfiguration createMerchantFeeConfigTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("merchant_fee_config", 
            "ds${0..2}.merchant_fee_config_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_database"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("merchant_id", "merchant_id_table"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
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
}

