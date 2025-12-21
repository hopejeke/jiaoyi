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
     * 用于 OutboxNodeMapper 和 OutboxMapper（如果存在）
     */
    @Bean(name = "primarySqlSessionFactory")
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        try {
            sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/*.xml"));
        } catch (Exception e) {
            // 如果找不到 mapper 文件，忽略（order-service 可能没有 outbox 相关的 mapper）
        }
        sessionFactoryBean.afterPropertiesSet();
        // 直接调用 getObject() 返回 SqlSessionFactory，避免 FactoryBean 类型问题
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
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * ShardingSphere 数据源的 SqlSessionFactory
     * 用于其他 Mapper（OrderMapper、OrderItemMapper、OrderCouponMapper 等分片表）
     */
    @Bean(name = "shardingSqlSessionFactory")
    public SqlSessionFactory shardingSqlSessionFactory(
            @Qualifier("shardingSphereDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        try {
            sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/*.xml"));
        } catch (Exception e) {
            // 如果找不到 mapper 文件，记录警告但继续
            // 因为 mapper 文件可能在其他位置
        }
        sessionFactoryBean.afterPropertiesSet();
        // 直接调用 getObject() 返回 SqlSessionFactory，避免 FactoryBean 类型问题
        SqlSessionFactory factory = sessionFactoryBean.getObject();
        if (factory == null) {
            throw new IllegalStateException("Failed to create SqlSessionFactory");
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
     * 配置所有 Mapper 使用 ShardingSphere 数据源
     * 由于 order-service 的所有表都是分片表，统一使用 ShardingSphere 数据源
     */
    @Configuration
    @MapperScan(
        basePackages = "com.jiaoyi.order.mapper",
        sqlSessionFactoryRef = "shardingSqlSessionFactory"
    )
    static class ShardingMapperScanConfig {
    }
}

