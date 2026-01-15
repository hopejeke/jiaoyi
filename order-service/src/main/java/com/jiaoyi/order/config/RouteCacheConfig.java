package com.jiaoyi.order.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RouteCache 配置
 * 确保 RouteCache 使用基础数据库（ds_base）的 JdbcTemplate
 * 注意：shard_bucket_route 表存在于基础数据库 jiaoyi 中，不分片
 */
@Configuration
public class RouteCacheConfig {
    
    /**
     * 创建基础数据库的 JdbcTemplate（用于读取 shard_bucket_route 路由表）
     * 注意：shard_bucket_route 表存在于基础数据库 jiaoyi 中，不分片
     */
    @Bean(name = "routeCacheJdbcTemplate")
    public JdbcTemplate routeCacheJdbcTemplate() {
        // 直接创建基础数据库的连接（与 ShardingSphereConfig 中的 ds_base 配置一致）
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        return new JdbcTemplate(dataSource);
    }
}

