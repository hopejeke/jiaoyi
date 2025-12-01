package com.jiaoyi.product.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
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
@org.springframework.core.annotation.Order(3) // 在DatabaseInitializer和WorkerIdAllocator之后执行
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
        
        // ds0: 分片数据库 0
        HikariDataSource ds0 = new HikariDataSource();
        ds0.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds0.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_0?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds0.setUsername("root");
        ds0.setPassword("root");
        dataSourceMap.put("ds0", ds0);
        
        // ds1: 分片数据库 1
        HikariDataSource ds1 = new HikariDataSource();
        ds1.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds1.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        ds1.setUsername("root");
        ds1.setPassword("root");
        dataSourceMap.put("ds1", ds1);
        
        // ds2: 分片数据库 2
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds2.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi_2?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
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
        
        // 配置分片算法
        // 注意：使用 Groovy 表达式，确保整数除法
        // 数据库分片：store_id % 9 的结果除以 3，取整数部分
        // 例如：store_id=1 -> (1%9)/3 = 1/3 = 0.333 -> 需要取整为 0
        // 使用 intdiv 进行整数除法
        // 商品表和库存表使用相同的分片算法，确保同一店铺的数据在同一数据库中
        shardingRuleConfig.getShardingAlgorithms().put("store_products_database_inline", 
            createInlineAlgorithm("ds${(store_id % 9).intdiv(3)}"));
        shardingRuleConfig.getShardingAlgorithms().put("store_products_table_inline", 
            createInlineAlgorithm("store_products_${(store_id % 9) % 3}"));
        // 库存表使用相同的分片算法
        // 商品SKU表使用相同的分片算法
        shardingRuleConfig.getShardingAlgorithms().put("product_sku_database_inline", 
            createInlineAlgorithm("ds${(store_id % 9).intdiv(3)}"));
        shardingRuleConfig.getShardingAlgorithms().put("product_sku_table_inline", 
            createInlineAlgorithm("product_sku_${(store_id % 9) % 3}"));
        // 库存表使用相同的分片算法
        shardingRuleConfig.getShardingAlgorithms().put("inventory_database_inline", 
            createInlineAlgorithm("ds${(store_id % 9).intdiv(3)}"));
        shardingRuleConfig.getShardingAlgorithms().put("inventory_table_inline", 
            createInlineAlgorithm("inventory_${(store_id % 9) % 3}"));
        
        // 配置分布式主键生成策略（雪花算法）
        // 解决分库分表环境下主键重复的问题
        // 每个分片表独立自增会导致主键重复，使用雪花算法生成全局唯一ID
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGenerator());
        
        return shardingRuleConfig;
    }
    
    /**
     * 创建商品表分片规则
     */
    private ShardingTableRuleConfiguration createStoreProductsTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("store_products", 
            "ds${0..2}.store_products_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_products_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "store_products_table_inline"));
        // 配置主键生成策略：使用雪花算法生成全局唯一ID
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建商品SKU表分片规则
     * 使用与商品表相同的分片策略（基于 store_id），确保同一店铺的商品和SKU在同一数据库中
     */
    private ShardingTableRuleConfiguration createProductSkuTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("product_sku", 
            "ds${0..2}.product_sku_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "product_sku_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "product_sku_table_inline"));
        // 配置主键生成策略：使用雪花算法生成全局唯一ID
        tableRule.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("id", "snowflake"));
        return tableRule;
    }
    
    /**
     * 创建库存表分片规则
     * 使用与商品表相同的分片策略（基于 store_id），确保同一店铺的商品和库存在同一数据库中
     * 库存按SKU级别管理，sku_id为NULL时表示商品级别库存（兼容旧数据）
     */
    private ShardingTableRuleConfiguration createInventoryTableRule() {
        ShardingTableRuleConfiguration tableRule = new ShardingTableRuleConfiguration("inventory", 
            "ds${0..2}.inventory_${0..2}");
        tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "inventory_database_inline"));
        tableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration("store_id", "inventory_table_inline"));
        // 配置主键生成策略：使用雪花算法生成全局唯一ID
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

