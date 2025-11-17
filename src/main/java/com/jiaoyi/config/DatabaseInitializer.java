package com.jiaoyi.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库初始化器
 * 应用启动时自动创建sched_node表（如果不存在）
 * 使用@PostConstruct确保在其他Bean初始化之前执行
 */
@Component
@Order(1) // 优先执行
@Slf4j
public class DatabaseInitializer {
    
    private final DataSource dataSource;
    
    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // 1. 创建sched_node表
            createSchedNodeTable(conn, metaData);
            
            // 2. 为outbox表添加shard_id列（如果不存在）
            addShardIdToOutbox(conn, metaData);
            
        } catch (Exception e) {
            log.error("数据库初始化失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建sched_node表
     */
    private void createSchedNodeTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "sched_node", null);
            
            if (tables.next()) {
                log.info("sched_node表已存在，跳过创建");
                tables.close();
                return;
            }
            tables.close();
            
            // 创建sched_node表
            String createTableSql = "CREATE TABLE sched_node (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "ip VARCHAR(50) NOT NULL COMMENT '节点IP地址', " +
                    "port INT NOT NULL COMMENT '节点端口', " +
                    "node_id VARCHAR(100) NOT NULL COMMENT '节点唯一标识（IP:PORT）', " +
                    "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用', " +
                    "expired_time DATETIME NOT NULL COMMENT '心跳过期时间', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_node_id (node_id), " +
                    "INDEX idx_enabled_expired (enabled, expired_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度节点表（用于节点注册和分片分配）'";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createTableSql);
                log.info("✓ sched_node表创建成功！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("Table 'sched_node' already exists"))) {
                log.info("sched_node表已存在，跳过创建");
            } else {
                log.error("创建sched_node表失败: {}", errorMsg, e);
            }
        }
    }
    
    /**
     * 为outbox表添加shard_id列
     */
    private void addShardIdToOutbox(Connection conn, DatabaseMetaData metaData) {
        try {
            // 检查outbox表是否存在
            ResultSet tables = metaData.getTables(null, null, "outbox", null);
            if (!tables.next()) {
                log.warn("outbox表不存在，跳过添加shard_id列");
                tables.close();
                return;
            }
            tables.close();
            
            // 检查shard_id列是否存在
            ResultSet columns = metaData.getColumns(null, null, "outbox", "shard_id");
            if (columns.next()) {
                log.info("outbox表的shard_id列已存在，跳过添加");
                columns.close();
                return;
            }
            columns.close();
            
            try (Statement stmt = conn.createStatement()) {
                // 1. 添加shard_id列（允许NULL，后续更新）
                stmt.executeUpdate("ALTER TABLE outbox ADD COLUMN shard_id INT NULL COMMENT '分片ID（用于分片处理）' AFTER id");
                log.info("✓ outbox表已添加shard_id列");
                
                // 2. 为现有数据计算并设置shard_id（假设shardCount=10）
                int updateCount = stmt.executeUpdate("UPDATE outbox SET shard_id = (id % 10) WHERE shard_id IS NULL");
                log.info("✓ 已更新 {} 条记录的shard_id", updateCount);
                
                // 3. 将shard_id设置为NOT NULL
                stmt.executeUpdate("ALTER TABLE outbox MODIFY COLUMN shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）'");
                log.info("✓ shard_id已设置为NOT NULL");
                
                // 4. 添加索引
                try {
                    stmt.executeUpdate("ALTER TABLE outbox ADD INDEX idx_shard_id (shard_id)");
                    log.info("✓ idx_shard_id索引已添加");
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate key name")) {
                        log.info("idx_shard_id索引已存在，跳过");
                    } else {
                        throw e;
                    }
                }
                
                try {
                    stmt.executeUpdate("ALTER TABLE outbox ADD INDEX idx_shard_id_status (shard_id, status)");
                    log.info("✓ idx_shard_id_status索引已添加");
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate key name")) {
                        log.info("idx_shard_id_status索引已存在，跳过");
                    } else {
                        throw e;
                    }
                }
                
                log.info("✓ outbox表shard_id列和相关索引添加完成！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Duplicate column name")) {
                log.info("outbox表的shard_id列已存在，跳过添加");
            } else {
                log.error("为outbox表添加shard_id列失败: {}", errorMsg, e);
            }
        }
    }
}

