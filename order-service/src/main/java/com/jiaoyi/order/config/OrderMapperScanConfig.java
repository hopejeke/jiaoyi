package com.jiaoyi.order.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Order Mapper 扫描配置（使用 shardingSqlSessionFactory）
 * 确保 OrderMapper 等分片表 Mapper 使用 ShardingSphere 数据源
 * 
 * 注意：必须明确指定 sqlSessionFactoryRef，否则可能使用默认的 primarySqlSessionFactory
 * （连接 jiaoyi 基础库，没有 orders 表）
 */
@Configuration
@MapperScan(
    basePackages = "com.jiaoyi.order.mapper",
    sqlSessionFactoryRef = "shardingSqlSessionFactory"
)
public class OrderMapperScanConfig {
    // 空类，只用于 @MapperScan 配置
}

