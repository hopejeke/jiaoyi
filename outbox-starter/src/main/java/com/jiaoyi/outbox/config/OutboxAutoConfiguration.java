package com.jiaoyi.outbox.config;

import com.jiaoyi.outbox.OutboxDispatcher;
import com.jiaoyi.outbox.OutboxService;
import com.jiaoyi.outbox.repository.OutboxRepository;
import com.jiaoyi.outbox.service.OutboxClaimService;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Outbox Mapper 扫描配置（可选，仅当业务方提供 MyBatis 实现时使用）
 * 默认使用 JdbcTemplate，业务方可以提供 MyBatis 实现作为覆盖
 */
@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(
    basePackages = "com.jiaoyi.outbox.mapper",
    sqlSessionFactoryRef = "primarySqlSessionFactory"
)
@ConditionalOnProperty(prefix = "outbox", name = "table")
@ConditionalOnBean(name = "primarySqlSessionFactory")
@ConditionalOnClass(name = "org.mybatis.spring.annotation.MapperScan")
class OutboxMapperScanWithPrimaryConfiguration {
    // 空类，只用于 @MapperScan 配置（可选）
}

/**
 * Outbox Mapper 扫描配置（可选，仅当业务方提供 MyBatis 实现时使用）
 * 当 primarySqlSessionFactory 不存在时使用默认 sqlSessionFactory
 */
@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(
    basePackages = "com.jiaoyi.outbox.mapper",
    sqlSessionFactoryRef = "sqlSessionFactory"
)
@ConditionalOnProperty(prefix = "outbox", name = "table")
@ConditionalOnMissingBean(name = "primarySqlSessionFactory")
@ConditionalOnBean(name = "sqlSessionFactory")
@ConditionalOnClass(name = "org.mybatis.spring.annotation.MapperScan")
class OutboxMapperScanWithDefaultConfiguration {
    // 空类，只用于 @MapperScan 配置（可选）
}

/**
 * Outbox 自动配置类
 * 使用者只需要：
 * 1. 引入 outbox-starter 依赖
 * 2. 配置 outbox.table=xxx
 * 3. 实现 OutboxHandler 接口
 * 4. 调用 OutboxService.enqueue()
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(OutboxService.class)
@ConditionalOnProperty(prefix = "outbox", name = "table")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
@EnableAsync
public class OutboxAutoConfiguration {
    
    private final OutboxProperties outboxProperties;
    
    public OutboxAutoConfiguration(OutboxProperties outboxProperties) {
        this.outboxProperties = outboxProperties;
    }
    
    @PostConstruct
    public void init() {
        log.info("【OutboxAutoConfiguration】自动配置已加载，outbox.table: {}", outboxProperties.getTable());
    }
    
    /**
     * Outbox消息处理线程池
     */
    @Bean(name = "outboxExecutor")
    @ConditionalOnMissingBean(name = "outboxExecutor")
    public ThreadPoolExecutor outboxExecutor() {
        OutboxProperties.ThreadPool threadPool = outboxProperties.getThreadPool();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadPool.getCoreSize(),
                threadPool.getMaxSize(),
                threadPool.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(threadPool.getQueueCapacity()),
                new ThreadFactory() {
                    private int threadNumber = 1;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "outbox-processor-" + threadNumber++);
                        thread.setDaemon(false);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("【OutboxAutoConfiguration】Outbox线程池初始化完成 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}, 保活时间: {}秒",
                threadPool.getCoreSize(), threadPool.getMaxSize(), threadPool.getQueueCapacity(), threadPool.getKeepAliveSeconds());

        return executor;
    }

    /**
     * 创建 OutboxRepository Bean（默认使用 JdbcTemplate 实现）
     * 确保 OutboxRepository 可以被其他组件（如 OutboxManagementController）注入
     * 
     * 注意：如果业务方提供了自己的 OutboxRepository Bean（如通过 OutboxRepositoryConfig），
     * 则不会创建此 Bean。业务方应该确保使用的 JdbcTemplate 基于 ShardingSphere 数据源。
     */
    @Bean
    @ConditionalOnMissingBean(OutboxRepository.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public OutboxRepository outboxRepository(ApplicationContext applicationContext) {
        // 优先使用业务方提供的专用 JdbcTemplate（如 outboxJdbcTemplate）
        JdbcTemplate jdbcTemplate;
        try {
            jdbcTemplate = applicationContext.getBean("outboxJdbcTemplate", JdbcTemplate.class);
            log.info("【OutboxAutoConfiguration】使用业务方提供的 outboxJdbcTemplate");
        } catch (Exception e) {
            // 如果没有找到 outboxJdbcTemplate，使用默认的 JdbcTemplate
            jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
            log.warn("【OutboxAutoConfiguration】未找到 outboxJdbcTemplate，使用默认 JdbcTemplate。建议业务方提供基于 ShardingSphere 数据源的 JdbcTemplate");
        }
        
        log.info("【OutboxAutoConfiguration】创建 OutboxRepository Bean（使用 JdbcTemplate 实现）");
        return new com.jiaoyi.outbox.repository.JdbcOutboxRepository(jdbcTemplate);
    }
    
    /**
     * 创建 OutboxService Bean（用于写入 outbox 表）
     * 
     * 注意：OutboxService 内部会自动在事务提交后触发 kick 事件，业务方完全无感
     * 
     * 默认使用 JdbcTemplate 实现
     */
    @Bean
    @ConditionalOnMissingBean(OutboxService.class)
    @ConditionalOnBean(OutboxRepository.class)
    public OutboxService outboxService(OutboxRepository outboxRepository,
                                      ApplicationContext applicationContext,
                                      @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        
        log.info("【OutboxAutoConfiguration】创建 OutboxService Bean（事务提交后自动处理）");
        OutboxService outboxService = new OutboxService(outboxRepository);
        
        // 手动注入 ApplicationContext（用于获取 handlers）
        try {
            java.lang.reflect.Field field = OutboxService.class.getDeclaredField("applicationContext");
            field.setAccessible(true);
            field.set(outboxService, applicationContext);
            log.info("【OutboxAutoConfiguration】已注入 ApplicationContext，自动处理功能已启用");
        } catch (Exception e) {
            log.warn("【OutboxAutoConfiguration】注入 ApplicationContext 失败，自动处理功能将不可用: {}", e.getMessage());
        }
        
        // 手动注入 TaskExecutor（用于异步执行任务处理，可选）
        try {
            TaskExecutor taskExecutor = applicationContext.getBean("outboxExecutor", TaskExecutor.class);
            java.lang.reflect.Field field = OutboxService.class.getDeclaredField("taskExecutor");
            field.setAccessible(true);
            field.set(outboxService, taskExecutor);
            log.info("【OutboxAutoConfiguration】已注入 TaskExecutor，任务将异步处理");
        } catch (Exception e) {
            log.debug("【OutboxAutoConfiguration】未找到 TaskExecutor，任务将同步处理（afterCommit 回调中执行，不阻塞主事务）");
        }
        
        return outboxService;
    }
    
    /**
     * 创建 OutboxDispatcher Bean（用于扫表和处理任务）
     */
    @Bean
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    @ConditionalOnBean({OutboxService.class, OutboxRepository.class})
    public OutboxDispatcher outboxDispatcher(
            OutboxRepository outboxRepository,
            OutboxService outboxService,
            @Autowired(required = false) List<OutboxHandler> handlers) {
        
        if (handlers == null || handlers.isEmpty()) {
            log.warn("【OutboxAutoConfiguration】未找到 OutboxHandler 实现，不创建 OutboxDispatcher");
            return null;
        }
        
        log.info("【OutboxAutoConfiguration】创建 OutboxDispatcher Bean，找到 {} 个 OutboxHandler: {}", 
                handlers.size(), 
                handlers.stream().map(h -> h.getClass().getSimpleName()).toList());
        
        return new OutboxDispatcher(outboxRepository, handlers, outboxService);
    }
    
    /**
     * 创建 OutboxClaimService Bean（两段式 claim）
     */
    @Bean
    @ConditionalOnMissingBean(OutboxClaimService.class)
    @ConditionalOnBean(OutboxRepository.class)
    public OutboxClaimService outboxClaimService(OutboxRepository outboxRepository) {
        log.info("【OutboxAutoConfiguration】创建 OutboxClaimService Bean（两段式 claim，使用 FOR UPDATE SKIP LOCKED）");
        return new OutboxClaimService(outboxRepository);
    }
}

