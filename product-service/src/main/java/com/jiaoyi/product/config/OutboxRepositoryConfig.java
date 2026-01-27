package com.jiaoyi.product.config;

import com.jiaoyi.outbox.repository.OutboxRepository;
import com.jiaoyi.outbox.repository.JdbcOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Outbox Repository 配置（Product Service）
 * 
 * 确保 OutboxRepository 使用 ShardingSphere 数据源，而不是基础数据库
 * 这样 outbox 表的操作才能正确路由到分片表（outbox_00..outbox_31）
 */
@Slf4j
@Configuration
public class OutboxRepositoryConfig {
    
    /**
     * 为 OutboxRepository 创建专用的 JdbcTemplate
     * 使用 shardingSphereDataSource，确保 SQL 能正确路由到分片表
     */
    @Bean(name = "outboxJdbcTemplate")
    @ConditionalOnMissingBean(name = "outboxJdbcTemplate")
    public JdbcTemplate outboxJdbcTemplate(@Qualifier("shardingSphereDataSource") DataSource dataSource) {
        log.info("【OutboxRepositoryConfig】创建 OutboxRepository 专用的 JdbcTemplate（使用 ShardingSphere 数据源）");
        return new JdbcTemplate(dataSource);
    }
    
    /**
     * 创建 OutboxRepository Bean（可选，如果 OutboxAutoConfiguration 没有创建）
     * 使用专用的 outboxJdbcTemplate（基于 ShardingSphere 数据源）
     */
    @Bean
    @ConditionalOnMissingBean(OutboxRepository.class)
    public OutboxRepository outboxRepository(@Qualifier("outboxJdbcTemplate") JdbcTemplate jdbcTemplate) {
        log.info("【OutboxRepositoryConfig】创建 OutboxRepository Bean（使用 ShardingSphere 数据源）");
        return new JdbcOutboxRepository(jdbcTemplate);
    }
}

