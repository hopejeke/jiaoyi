package com.jiaoyi.product.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 配置两个数据源：
 * 1. primaryDataSource: 普通数据源，连接 jiaoyi 数据库（用于 outbox_node 和 outbox 表）
 * 2. shardingSphereDataSource: ShardingSphere 数据源，用于分片表
 * 
 * @author Administrator
 */
@Configuration
public class DataSourceConfig {
    
    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}")
    private String jdbcUrl;
    
    @Value("${spring.datasource.username:root}")
    private String username;
    
    @Value("${spring.datasource.password:root}")
    private String password;
    
    /**
     * 普通数据源（连接 jiaoyi 数据库，用于 outbox_node 和 outbox 表）
     * 使用 @Primary 标记为默认数据源
     */
    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        return dataSource;
    }
    
    /**
     * 普通数据源的 SqlSessionFactory
     * 用于 OutboxNodeMapper 和 OutboxMapper
     * 注意：不要使用 @Primary，避免与 ShardingSphere 数据源冲突
     * 只加载 primary 目录下的 Mapper XML（OutboxMapper.xml, OutboxNodeMapper.xml）
     */
    @Bean(name = "primarySqlSessionFactory")
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        // 只加载 primary 目录下的 Mapper XML（非分片表）
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:mapper/primary/*.xml");
        System.out.println("Primary SqlSessionFactory 加载的 Mapper XML 文件数量: " + resources.length);
        for (org.springframework.core.io.Resource resource : resources) {
            System.out.println("  - " + resource.getFilename());
        }
        sessionFactory.setMapperLocations(resources);
        SqlSessionFactory factory = sessionFactory.getObject();
        if (factory == null) {
            throw new IllegalStateException("Failed to create Primary SqlSessionFactory");
        }
        // 验证加载的 Mapper
        System.out.println("Primary SqlSessionFactory 已加载的 Mapper 数量: " + factory.getConfiguration().getMappedStatements().size());
        return factory;
    }
    
    /**
     * 普通数据源的事务管理器
     */
    @Bean(name = "primaryTransactionManager")
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * ShardingSphere 数据源的 SqlSessionFactory
     * 用于其他 Mapper（StoreProductMapper、StoreMapper 等分片表）
     * 使用 @DependsOn 确保在 shardingSphereDataSource 之后创建
     * 只加载 sharding 目录下的 Mapper XML（分片表）
     */
    @Bean(name = "shardingSqlSessionFactory")
    @org.springframework.context.annotation.DependsOn("shardingSphereDataSource")
    public SqlSessionFactory shardingSqlSessionFactory(
            @Qualifier("shardingSphereDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        // 只加载 sharding 目录下的 Mapper XML（分片表）
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] resources;
        try {
            resources = resolver.getResources("classpath:mapper/sharding/*.xml");
            System.out.println("ShardingSphere SqlSessionFactory 加载的 Mapper XML 文件数量: " + resources.length);
            for (org.springframework.core.io.Resource resource : resources) {
                System.out.println("  - " + resource.getFilename() + " (URL: " + resource.getURL() + ")");
            }
            if (resources.length == 0) {
                System.err.println("警告：未找到任何 Mapper XML 文件！请检查 classpath:mapper/sharding/*.xml 路径是否正确");
            }
        } catch (Exception e) {
            System.err.println("加载 Mapper XML 文件失败: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("无法加载 Mapper XML 文件", e);
        }
        sessionFactory.setMapperLocations(resources);
        SqlSessionFactory factory;
        try {
            factory = sessionFactory.getObject();
        } catch (Exception e) {
            System.err.println("创建 SqlSessionFactory 失败: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("无法创建 SqlSessionFactory", e);
        }
        if (factory == null) {
            throw new IllegalStateException("Failed to create ShardingSphere SqlSessionFactory");
        }
        // 验证加载的 Mapper
        System.out.println("ShardingSphere SqlSessionFactory 已加载的 Mapper 数量: " + factory.getConfiguration().getMappedStatements().size());
        System.out.println("ShardingSphere SqlSessionFactory 已加载的 Mapper 列表:");
        // 使用 getMappedStatementNames() 来安全地获取所有 Mapper 名称，避免 Ambiguity 对象
        factory.getConfiguration().getMappedStatementNames().forEach(name -> {
            try {
                org.apache.ibatis.mapping.MappedStatement ms = factory.getConfiguration().getMappedStatement(name);
                System.out.println("  - " + ms.getId());
            } catch (Exception e) {
                // 如果获取失败，可能是 Ambiguity 对象
                System.out.println("  - [AMBIGUITY] " + name);
            }
        });
        
        // 特别检查 StoreProductMapper.selectByStoreId 是否存在
        String selectByStoreIdKey = "com.jiaoyi.product.mapper.sharding.StoreProductMapper.selectByStoreId";
        try {
            org.apache.ibatis.mapping.MappedStatement ms = factory.getConfiguration().getMappedStatement(selectByStoreIdKey);
            System.out.println("✓ StoreProductMapper.selectByStoreId 已成功加载，SQL: " + ms.getSqlSource().getClass().getSimpleName());
        } catch (Exception e) {
            System.err.println("✗ StoreProductMapper.selectByStoreId 未找到！错误: " + e.getMessage());
            System.err.println("  请检查 mapper/sharding/StoreProductMapper.xml 文件是否存在且包含 selectByStoreId 方法");
        }
        
        return factory;
    }
    
    /**
     * ShardingSphere 数据源的事务管理器
     */
    @Bean(name = "shardingTransactionManager")
    public DataSourceTransactionManager shardingTransactionManager(
            @Qualifier("shardingSphereDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * 配置分片表相关的 Mapper 使用 ShardingSphere 数据源
     * 扫描 sharding 子包下的 Mapper（StoreProductMapper、InventoryMapper 等）
     * 使用 @Order(1) 确保优先加载
     */
    @Configuration
    @org.springframework.core.annotation.Order(1)
    @MapperScan(
        basePackages = "com.jiaoyi.product.mapper.sharding",
        sqlSessionFactoryRef = "shardingSqlSessionFactory"
    )
    static class ShardingMapperScanConfig {
        // 空配置类，仅用于 @MapperScan 注解
        // 只扫描 sharding 子包，避免与 primary 子包的 Mapper 冲突
    }
    
    /**
     * 配置非分片表相关的 Mapper 使用普通数据源
     * 扫描 primary 子包下的 Mapper（StoreMapper、OutboxMapper、OutboxNodeMapper）
     * 这些表都存储在主库（jiaoyi）中，不分库
     * 使用 @Order(2) 确保在 ShardingMapperScanConfig 之后加载
     */
    @Configuration
    @org.springframework.core.annotation.Order(2)
    @MapperScan(
        basePackages = "com.jiaoyi.product.mapper.primary",
        sqlSessionFactoryRef = "primarySqlSessionFactory"
    )
    static class PrimaryMapperScanConfig {
        // 空配置类，仅用于 @MapperScan 注解
        // 只扫描 primary 子包，避免与 sharding 子包的 Mapper 冲突
    }
}

