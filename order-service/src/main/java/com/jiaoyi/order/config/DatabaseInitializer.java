package com.jiaoyi.order.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库初始化器（Order Service）
 * 应用启动时自动创建分库分表的数据库和表（如果不存在）
 * 只创建 order-service 相关的表：orders, order_items, order_coupons
 * 使用@PostConstruct确保在其他Bean初始化之前执行
 * 不依赖DataSource，直接使用JDBC连接，避免循环依赖
 */
@Component
@Order(1) // 优先执行
@Slf4j
public class DatabaseInitializer {
    
    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "root";
    
    @PostConstruct
    public void init() {
        try {
            String actualJdbcUrl = DEFAULT_JDBC_URL;
            String actualUsername = DEFAULT_USERNAME;
            String actualPassword = DEFAULT_PASSWORD;
            
            // 1. 创建分库分表的数据库和表（只创建 orders 相关表）
            createShardingDatabases(actualJdbcUrl, actualUsername, actualPassword);
            
            // 2. 创建outbox_node表和outbox表（在 jiaoyi 基础数据库中）
            String dbUrl = buildDatabaseUrl(actualJdbcUrl, "jiaoyi");
            log.info("连接基础数据库URL: {}", dbUrl);
            try (Connection conn = DriverManager.getConnection(dbUrl, actualUsername, actualPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();
                createOutboxNodeTable(conn, metaData);
                createOutboxTable(conn, metaData);
            }
            
            log.info("✓ 所有数据库和表初始化完成！");
            
        } catch (Exception e) {
            log.error("数据库初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }
    
    private String buildDatabaseUrl(String jdbcUrl, String databaseName) {
        if (jdbcUrl == null || jdbcUrl.isEmpty() || databaseName == null || databaseName.isEmpty()) {
            return jdbcUrl;
        }
        
        int questionMarkIndex = jdbcUrl.indexOf('?');
        
        if (questionMarkIndex > 0) {
            String urlWithoutParams = jdbcUrl.substring(0, questionMarkIndex);
            int lastSlashIndex = urlWithoutParams.lastIndexOf('/');
            
            if (lastSlashIndex > 0) {
                String base = jdbcUrl.substring(0, lastSlashIndex);
                String params = jdbcUrl.substring(questionMarkIndex);
                return base + "/" + databaseName + params;
            } else {
                return jdbcUrl.substring(0, questionMarkIndex) + "/" + databaseName + jdbcUrl.substring(questionMarkIndex);
            }
        } else {
            int lastSlashIndex = jdbcUrl.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                return jdbcUrl.substring(0, lastSlashIndex + 1) + databaseName;
            } else {
                return jdbcUrl + "/" + databaseName;
            }
        }
    }
    
    private String extractBaseUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return jdbcUrl;
        }
        
        int questionMarkIndex = jdbcUrl.indexOf('?');
        
        if (questionMarkIndex > 0) {
            String urlWithoutParams = jdbcUrl.substring(0, questionMarkIndex);
            int lastSlashIndex = urlWithoutParams.lastIndexOf('/');
            
            if (lastSlashIndex > 0) {
                String base = jdbcUrl.substring(0, lastSlashIndex);
                String params = jdbcUrl.substring(questionMarkIndex);
                return base + params;
            } else {
                return jdbcUrl;
            }
        } else {
            int lastSlashIndex = jdbcUrl.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                return jdbcUrl.substring(0, lastSlashIndex);
            } else {
                return jdbcUrl;
            }
        }
    }
    
    private void createShardingDatabases(String jdbcUrl, String username, String password) {
        String baseUrl = extractBaseUrl(jdbcUrl);
        
        log.info("开始创建分库分表数据库和表（Order Service）...");
        log.info("原始JDBC URL: {}", jdbcUrl);
        log.info("提取的基础URL: {}", baseUrl);
        
        try (Connection conn = DriverManager.getConnection(baseUrl, username, password)) {
            createDatabases(conn);
            
            for (int dbIndex = 0; dbIndex < 3; dbIndex++) {
                String dbName = "jiaoyi_" + dbIndex;
                createShardingTables(conn, dbName, dbIndex);
            }
            
            log.info("✓ 分库分表数据库和表创建完成！");
            
        } catch (Exception e) {
            log.error("创建分库分表失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建分库分表失败", e);
        }
    }
    
    private void createDatabases(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for (int i = 0; i < 3; i++) {
                String dbName = "jiaoyi_" + i;
                String createDbSql = "CREATE DATABASE IF NOT EXISTS " + dbName + 
                        " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
                stmt.executeUpdate(createDbSql);
                log.info("✓ 数据库 {} 创建成功", dbName);
            }
        }
    }
    
    private void createShardingTables(Connection conn, String dbName, int dbIndex) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("USE " + dbName);
            
            // 创建订单表分片
            createOrdersTables(stmt, dbIndex);
            
            // 创建订单项表分片
            createOrderItemsTables(stmt, dbIndex);
            
            // 创建订单优惠券关联表分片
            createOrderCouponsTables(stmt, dbIndex);
            
            // 注意：coupon_usage 表属于 coupon-service，不在 order-service 中创建
            // 如果需要，可以在 coupon-service 的 DatabaseInitializer 中创建
            
            log.info("✓ 数据库 {} 的所有分片表创建完成", dbName);
            
        } catch (Exception e) {
            log.error("为数据库 {} 创建分片表失败: {}", dbName, e.getMessage(), e);
        }
    }
    
    private void createOrdersTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "orders_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号', " +
                    "user_id BIGINT NOT NULL COMMENT '用户ID', " +
                    "status VARCHAR(20) NOT NULL COMMENT '订单状态', " +
                    "total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额', " +
                    "total_discount_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '总优惠金额', " +
                    "actual_amount DECIMAL(10,2) NOT NULL COMMENT '实际支付金额', " +
                    "receiver_name VARCHAR(100) NOT NULL COMMENT '收货人姓名', " +
                    "receiver_phone VARCHAR(20) NOT NULL COMMENT '收货人电话', " +
                    "receiver_address VARCHAR(500) NOT NULL COMMENT '收货地址', " +
                    "remark TEXT COMMENT '备注', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_user_id (user_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time), " +
                    "INDEX idx_order_no (order_no)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单表分片创建完成（3个分片表）");
    }
    
    private void createOrderItemsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "order_items_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "product_id BIGINT NOT NULL COMMENT '商品ID', " +
                    "product_name VARCHAR(200) NOT NULL COMMENT '商品名称', " +
                    "product_image VARCHAR(500) COMMENT '商品图片', " +
                    "unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价', " +
                    "quantity INT NOT NULL COMMENT '购买数量', " +
                    "subtotal DECIMAL(10,2) NOT NULL COMMENT '小计金额', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_product_id (product_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单项表分片创建完成（3个分片表）");
    }
    
    private void createOrderCouponsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "order_coupons_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "coupon_id BIGINT NOT NULL COMMENT '优惠券ID', " +
                    "coupon_code VARCHAR(50) NOT NULL COMMENT '优惠券代码', " +
                    "applied_amount DECIMAL(10,2) NOT NULL COMMENT '该优惠券实际抵扣金额', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_coupon_id (coupon_id), " +
                    "INDEX idx_coupon_code (coupon_code)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单优惠券关联表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单优惠券关联表分片创建完成（3个分片表）");
    }
    
    
    private void createOutboxNodeTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "outbox_node", null);
            
            if (tables.next()) {
                log.info("outbox_node表已存在，跳过创建");
                tables.close();
                return;
            }
            tables.close();
            
            String createTableSql = "CREATE TABLE outbox_node (" +
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
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox节点表（用于节点注册和分片分配）'";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createTableSql);
                log.info("✓ outbox_node表创建成功！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("Table 'outbox_node' already exists"))) {
                log.info("outbox_node表已存在，跳过创建");
            } else {
                log.error("创建outbox_node表失败: {}", errorMsg, e);
            }
        }
    }
    
    private void createOutboxTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "outbox", null);
            
            if (tables.next()) {
                log.info("outbox表已存在，检查shard_id列...");
                tables.close();
                addShardIdToOutbox(conn, metaData);
                return;
            }
            tables.close();
            
            String createTableSql = "CREATE TABLE outbox (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）', " +
                    "topic VARCHAR(100) NOT NULL COMMENT 'RocketMQ Topic', " +
                    "tag VARCHAR(50) NOT NULL COMMENT 'RocketMQ Tag', " +
                    "message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪）', " +
                    "message_body TEXT NOT NULL COMMENT '消息体（JSON格式）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待发送，SENT-已发送，FAILED-发送失败', " +
                    "retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数', " +
                    "error_message TEXT COMMENT '错误信息（发送失败时记录）', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "sent_at DATETIME COMMENT '发送时间', " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_created_at (created_at), " +
                    "INDEX idx_topic_tag (topic, tag), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_shard_id_status (shard_id, status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息发件箱表（Outbox Pattern）'";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createTableSql);
                log.info("✓ outbox表创建成功！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("already exists")) {
                log.info("outbox表已存在，跳过创建");
            } else {
                log.error("创建outbox表失败: {}", errorMsg, e);
            }
        }
    }
    
    private void addShardIdToOutbox(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "outbox", null);
            if (!tables.next()) {
                log.warn("outbox表不存在，跳过添加shard_id列");
                tables.close();
                return;
            }
            tables.close();
            
            ResultSet columns = metaData.getColumns(null, null, "outbox", "shard_id");
            if (columns.next()) {
                log.info("outbox表的shard_id列已存在，跳过添加");
                columns.close();
                return;
            }
            columns.close();
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE outbox ADD COLUMN shard_id INT NULL COMMENT '分片ID（用于分片处理）' AFTER id");
                log.info("✓ outbox表已添加shard_id列");
                
                int updateCount = stmt.executeUpdate("UPDATE outbox SET shard_id = (id % 10) WHERE shard_id IS NULL");
                log.info("✓ 已更新 {} 条记录的shard_id", updateCount);
                
                stmt.executeUpdate("ALTER TABLE outbox MODIFY COLUMN shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）'");
                log.info("✓ shard_id已设置为NOT NULL");
                
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
