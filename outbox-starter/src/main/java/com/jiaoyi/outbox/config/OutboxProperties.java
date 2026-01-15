package com.jiaoyi.outbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox 配置属性
 */
@Data
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {
    
    /**
     * Outbox 表名（必需）
     */
    private String table;
    
    /**
     * 分片数量（默认10）
     */
    private int shardCount = 10;
    
    /**
     * 扫描间隔（毫秒，默认2000）
     */
    private long interval = 2000;
    
    /**
     * 节点注册配置
     */
    private NodeRegistry nodeRegistry = new NodeRegistry();
    
    /**
     * 线程池配置
     */
    private ThreadPool threadPool = new ThreadPool();
    
    /**
     * SqlSessionFactory Bean 名称（用于 Mapper 扫描）
     * 默认值：primarySqlSessionFactory（如果不存在则使用 sqlSessionFactory）
     */
    private String sqlSessionFactoryRef = "primarySqlSessionFactory";
    
    @Data
    public static class NodeRegistry {
        /**
         * 心跳间隔（秒，默认10）
         */
        private long heartbeatInterval = 10;
        
        /**
         * 心跳超时（秒，默认30）
         */
        private long heartbeatTimeout = 30;
        
        /**
         * 清理间隔（毫秒，默认60000）
         */
        private long cleanupInterval = 60000;
    }
    
    @Data
    public static class ThreadPool {
        /**
         * 核心线程数（默认5）
         */
        private int coreSize = 5;
        
        /**
         * 最大线程数（默认10）
         */
        private int maxSize = 10;
        
        /**
         * 队列容量（默认100）
         */
        private int queueCapacity = 100;
        
        /**
         * 线程存活时间（秒，默认60）
         */
        private long keepAliveSeconds = 60;
    }
}



