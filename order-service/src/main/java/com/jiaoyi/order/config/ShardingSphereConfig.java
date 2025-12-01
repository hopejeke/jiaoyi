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
 * 配置 orders, order_items, order_coupons 表的分片规则
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
        
        // 配置分片表：orders, order_items, order_coupons
        // 使用 user_id 作为分片键
        shardingRuleConfig.getTables().add(createOrdersTableRule());
        shardingRuleConfig.getTables().add(createOrderItemsTableRule());
        shardingRuleConfig.getTables().add(createOrderCouponsTableRule());
        
        // 配置绑定表（orders, order_items, order_coupons 绑定，确保同一订单的数据在同一分片）
        // 注意：由于 order_items 和 order_coupons 使用 order_id 作为分片键，而 orders 使用 user_id，
        // 它们实际上不能直接绑定。这里删除绑定表配置，因为分片键不同。
        // 如果需要绑定，需要确保所有表使用相同的分片键
        
        // 配置分片算法（使用 user_id 作为分片键）
        shardingRuleConfig.getShardingAlgorithms().put("orders_database_inline", 
            createInlineAlgorithm("ds${(user_id % 9) / 3}"));
        shardingRuleConfig.getShardingAlgorithms().put("orders_table_inline", 
            createInlineAlgorithm("orders_${(user_id % 9) % 3}"));
        
        // 配置分布式主键生成策略（雪花算法）
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建订单表分片规则
     */
    private ShardingTableRuleConfiguration createOrdersTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("orders", 
            "ds${0..2}.orders_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "orders_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "orders_table_inline"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单项表分片规则（与 orders 绑定）
     */
    private ShardingTableRuleConfiguration createOrderItemsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_items", 
            "ds${0..2}.order_items_${0..2}");
        // order_items 表通过 order_id 关联到 orders 表，使用相同的分片策略
        // 但由于 order_items 表没有 user_id，需要通过 order_id 路由
        // 这里简化处理，使用 order_id 作为分片键（order_id 是雪花算法生成的，可以取模）
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "orders_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "orders_table_inline"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建订单优惠券关联表分片规则（与 orders 绑定）
     */
    private ShardingTableRuleConfiguration createOrderCouponsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("order_coupons", 
            "ds${0..2}.order_coupons_${0..2}");
        // 使用 order_id 作为分片键
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "orders_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "orders_table_inline"));
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    private AlgorithmConfiguration createInlineAlgorithm(String algorithmExpression) {
        Properties props = new Properties();
        props.setProperty("algorithm-expression", algorithmExpression);
        return new AlgorithmConfiguration("INLINE", props);
    }
    
    private AlgorithmConfiguration createSnowflakeKeyGenerator() {
        Properties props = new Properties();
        return new AlgorithmConfiguration("SNOWFLAKE", props);
    }
}

