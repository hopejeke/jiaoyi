package com.jiaoyi.order.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox Mapper 扫描配置（使用 shardingSqlSessionFactory）
 * 让 outbox-starter 的 mapper 走分片数据源
 */
@Configuration
@ConditionalOnProperty(prefix = "outbox", name = "table")
@MapperScan(
    basePackages = "com.jiaoyi.outbox.mapper",
    sqlSessionFactoryRef = "shardingSqlSessionFactory"
)
public class OutboxShardingMapperScanConfig {
    // 空类，只用于 @MapperScan 配置
}





