package com.jiaoyi.product.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 商品域路由缓存配置
 * 提供专用的 JdbcTemplate 用于读取 product_shard_bucket_route 路由表
 * 注意：product_shard_bucket_route 表存在于基础数据库 jiaoyi 中，不分片
 */
@Configuration
public class ProductRouteCacheConfig {
    
    /**
     * 创建基础数据库的 JdbcTemplate（用于读取 product_shard_bucket_route 路由表）
     * 注意：product_shard_bucket_route 表存在于基础数据库 jiaoyi 中，不分片
     */
    @Bean(name = "productRouteCacheJdbcTemplate")
    public JdbcTemplate productRouteCacheJdbcTemplate() {
        // 直接创建基础数据库的连接（与 DataSourceConfig 中的 dataSource 配置一致）
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        return new JdbcTemplate(dataSource);
    }
}

