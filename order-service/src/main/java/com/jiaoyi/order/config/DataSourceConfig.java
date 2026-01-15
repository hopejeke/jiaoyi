package com.jiaoyi.order.config;

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
 * 数据源配置（Order Service）
 * 配置两个数据源：
 * 1. primaryDataSource: 普通数据源，连接 jiaoyi 数据库（用于 outbox_node 和 outbox 表）
 * 2. shardingSphereDataSource: ShardingSphere 数据源，用于分片表（orders, order_items, order_coupons）
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
     * 普通数据源（连接 jiaoyi 数据库，用于基础库单表）
     * 注意：不再使用 @Primary，业务分片库是主库
     */
    @Bean(name = "primaryDataSource")
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
     * 用于 OutboxNodeMapper 和 OutboxMapper（非分片表）
     * 只加载 primary 目录下的 Mapper XML
     */
    @Bean(name = "primarySqlSessionFactory")
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        try {
            // 只加载 primary 目录下的 Mapper XML（非分片表）
            sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/primary/*.xml"));
        } catch (Exception e) {
            // 如果找不到 mapper 文件，忽略
        }
        sessionFactoryBean.afterPropertiesSet();
        SqlSessionFactory factory = sessionFactoryBean.getObject();
        if (factory == null) {
            throw new IllegalStateException("Failed to create SqlSessionFactory");
        }
        return factory;
    }
    
    /**
     * 普通数据源的事务管理器
     */
    @Bean(name = "primaryTransactionManager")
    public DataSourceTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * ShardingSphere 数据源的 SqlSessionFactory
     * 用于其他 Mapper（OrderMapper、OrderItemMapper、OrderCouponMapper 等分片表）
     * 只加载 mapper 目录下的 Mapper XML（排除 primary 目录）
     */
    @Bean(name = "shardingSqlSessionFactory")
    @Primary
    public SqlSessionFactory shardingSqlSessionFactory(
            @Qualifier("shardingSphereDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        try {
            // 加载 mapper 目录下的所有 XML，包括 jar 内的 mapper（如 outbox-starter 的 mapper）
            // 排除 primary 目录下的文件
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // 使用 classpath* 支持从 jar 包中加载 mapper XML
            org.springframework.core.io.Resource[] allResources = resolver.getResources("classpath*:mapper/**/*.xml");
            java.util.List<org.springframework.core.io.Resource> shardingResources = new java.util.ArrayList<>();
            for (org.springframework.core.io.Resource resource : allResources) {
                String path = resource.getURL().getPath();
                // 排除 primary 目录下的文件
                if (!path.contains("/mapper/primary/")) {
                    shardingResources.add(resource);
                }
            }
            sessionFactoryBean.setMapperLocations(shardingResources.toArray(new org.springframework.core.io.Resource[0]));
        } catch (Exception e) {
            // 如果找不到 mapper 文件，记录警告但继续
        }
        sessionFactoryBean.afterPropertiesSet();
        SqlSessionFactory factory = sessionFactoryBean.getObject();
        if (factory == null) {
            throw new IllegalStateException("Failed to create SqlSessionFactory");
        }
        return factory;
    }
    
    /**
     * ShardingSphere 数据源的事务管理器
     * 标记为 @Primary，因为业务代码主要使用分片数据源
     */
    @Bean(name = "shardingTransactionManager")
    @Primary
    public DataSourceTransactionManager shardingTransactionManager(
            @Qualifier("shardingSphereDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
}

