package com.jiaoyi.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * Outbox定时任务线程池配置
 * 用于并发处理outbox消息
 */
@Configuration
@EnableAsync
@Slf4j
public class OutboxThreadPoolConfig {

    @Value("${outbox.thread-pool.core-size:5}")
    private int corePoolSize;

    @Value("${outbox.thread-pool.max-size:10}")
    private int maxPoolSize;

    @Value("${outbox.thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${outbox.thread-pool.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    /**
     * Outbox消息处理线程池
     * 用于并发处理单次扫描到的消息
     */
    @Bean(name = "outboxExecutor")
    public ThreadPoolExecutor outboxExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private int threadNumber = 1;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "outbox-processor-" + threadNumber++);
                        thread.setDaemon(false);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );

        log.info("Outbox线程池初始化完成 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}, 保活时间: {}秒",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        return executor;
    }

    /**
     * Outbox节点线程池
     * 用于运行节点处理任务（长期运行）
     * 一个应用一个节点，所以只需要1个线程
     */
    @Bean(name = "outboxNodeExecutor")
    public ThreadPoolExecutor outboxNodeExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "outbox-node");
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("Outbox节点线程池初始化完成 - 线程数: 1（一个应用一个节点）");

        return executor;
    }
}

