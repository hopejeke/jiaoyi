package com.jiaoyi.product.config;

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
 * 数据库初始化器（Product Service）
 * 应用启动时自动创建分库分表的数据库和表（如果不存在）
 * 只创建 product-service 相关的表：store_products, inventory, inventory_transaction
 * 使用@PostConstruct确保在其他Bean初始化之前执行
 * 不依赖DataSource，直接使用JDBC连接，避免循环依赖
 */
@Component
@Order(1) // 优先执行
@Slf4j
public class DatabaseInitializer {
    
    // 数据库连接配置（直接使用默认值，因为sharding-config.yaml是独立文件，无法通过@Value读取）
    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "root";
    
    @PostConstruct
    public void init() {
        try {
            // 直接使用默认值（也可以从环境变量读取）
            String actualJdbcUrl = DEFAULT_JDBC_URL;
            String actualUsername = DEFAULT_USERNAME;
            String actualPassword = DEFAULT_PASSWORD;
            
            // 1. 创建分库分表的数据库和表（只创建 store_products 相关表）
            createShardingDatabases(actualJdbcUrl, actualUsername, actualPassword);
            
            // 2. 创建stores表、outbox_node表和outbox表（在 jiaoyi 基础数据库中）
            // 构建连接 jiaoyi 的URL：将数据库名插入到端口号和参数之间
            String dbUrl = buildDatabaseUrl(actualJdbcUrl, "jiaoyi");
            log.info("连接基础数据库URL: {}", dbUrl);
            try (Connection conn = DriverManager.getConnection(dbUrl, actualUsername, actualPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();
                createStoresTable(conn, metaData);
                createOutboxNodeTable(conn, metaData);
                createOutboxTable(conn, metaData);
                createSnowflakeWorkerTable(conn, metaData);
            }
            
            log.info("✓ 所有数据库和表初始化完成！");
            
        } catch (Exception e) {
            log.error("数据库初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }
    
    /**
     * 构建指定数据库的JDBC URL
     */
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
    
    /**
     * 从JDBC URL中提取基础连接URL（不包含数据库名）
     */
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
    
    /**
     * 创建分库分表的数据库和表（只创建 store_products 相关表）
     */
    private void createShardingDatabases(String jdbcUrl, String username, String password) {
        String baseUrl = extractBaseUrl(jdbcUrl);
        
        log.info("开始创建分库分表数据库和表（Product Service）...");
        log.info("原始JDBC URL: {}", jdbcUrl);
        log.info("提取的基础URL: {}", baseUrl);
        
        try (Connection conn = DriverManager.getConnection(baseUrl, username, password)) {
            // 创建3个数据库
            createDatabases(conn);
            
            // 为每个数据库创建分片表（只创建 store_products 表）
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
    
    /**
     * 创建3个数据库
     */
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
    
    /**
     * 为指定数据库创建分片表（创建 store_products 和 inventory 表）
     */
    private void createShardingTables(Connection conn, String dbName, int dbIndex) {
        try (Statement stmt = conn.createStatement()) {
            // 切换到指定数据库
            stmt.executeUpdate("USE " + dbName);
            
            // 创建商品表分片
            createStoreProductsTables(stmt, dbIndex);
            
            // 创建商品SKU表分片
            createProductSkuTables(stmt, dbIndex);
            
            // 创建库存表分片
            createInventoryTables(stmt, dbIndex);
            
            log.info("✓ 数据库 {} 的所有分片表创建完成", dbName);
            
        } catch (Exception e) {
            log.error("为数据库 {} 创建分片表失败: {}", dbName, e.getMessage(), e);
        }
    }
    
    /**
     * 创建商品表分片（3个分片表）
     */
    private void createStoreProductsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "store_products_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "store_id BIGINT NOT NULL COMMENT '店铺ID', " +
                    "product_name VARCHAR(200) NOT NULL COMMENT '商品名称', " +
                    "description TEXT COMMENT '商品描述', " +
                    "unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价', " +
                    "product_image VARCHAR(500) COMMENT '商品图片', " +
                    "category VARCHAR(100) COMMENT '商品分类', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '商品状态：ACTIVE-上架，INACTIVE-下架', " +
                    "is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于缓存一致性控制）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_store_id (store_id), " +
                    "INDEX idx_product_name (product_name), " +
                    "INDEX idx_category (category), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_is_delete (is_delete), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺商品表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 商品表分片创建完成（3个分片表）");
    }
    
    /**
     * 创建商品SKU表分片（3个分片表）
     */
    private void createProductSkuTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "product_sku_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "product_id BIGINT NOT NULL COMMENT '商品ID（关联store_products.id）', " +
                    "store_id BIGINT NOT NULL COMMENT '店铺ID（用于分片）', " +
                    "sku_code VARCHAR(100) NOT NULL COMMENT 'SKU编码（唯一标识）', " +
                    "sku_attributes TEXT COMMENT 'SKU属性（JSON格式，如：{\"color\":\"红色\",\"size\":\"L\"}）', " +
                    "sku_name VARCHAR(200) COMMENT 'SKU名称（如：红色 L码）', " +
                    "sku_price DECIMAL(10,2) COMMENT 'SKU价格（如果与商品价格不同）', " +
                    "sku_image VARCHAR(500) COMMENT 'SKU图片（如果与商品图片不同）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SKU状态：ACTIVE-启用，INACTIVE-禁用', " +
                    "is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（逻辑删除）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于缓存一致性控制）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_product_sku_code (product_id, sku_code), " +
                    "INDEX idx_store_id (store_id), " +
                    "INDEX idx_product_id (product_id), " +
                    "INDEX idx_sku_code (sku_code), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_is_delete (is_delete)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 商品SKU表分片创建完成（3个分片表）");
    }
    
    /**
     * 创建库存表分片（3个分片表）
     * 库存按SKU级别管理，sku_id为NULL时表示商品级别库存（兼容旧数据）
     */
    private void createInventoryTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "inventory_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "store_id BIGINT NOT NULL COMMENT '店铺ID', " +
                    "product_id BIGINT NOT NULL COMMENT '商品ID（关联store_products.id）', " +
                    "sku_id BIGINT COMMENT 'SKU ID（关联product_sku.id），NULL表示商品级别库存', " +
                    "product_name VARCHAR(200) NOT NULL COMMENT '商品名称', " +
                    "sku_name VARCHAR(200) COMMENT 'SKU名称（如果存在SKU）', " +
                    "current_stock INT NOT NULL DEFAULT 0 COMMENT '当前库存数量', " +
                    "locked_stock INT NOT NULL DEFAULT 0 COMMENT '锁定库存数量（已下单但未支付）', " +
                    "min_stock INT NOT NULL DEFAULT 0 COMMENT '最低库存预警线', " +
                    "max_stock INT COMMENT '最大库存容量', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_store_product_sku (store_id, product_id, sku_id), " +
                    "INDEX idx_store_id (store_id), " +
                    "INDEX idx_product_id (product_id), " +
                    "INDEX idx_sku_id (sku_id), " +
                    "INDEX idx_store_product (store_id, product_id), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表（SKU级别）_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 库存表分片创建完成（3个分片表）");
    }
    
    /**
     * 创建stores表（在主库 jiaoyi 中，不分库）
     */
    private void createStoresTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "stores", null);
            
            if (tables.next()) {
                log.info("stores表已存在，跳过创建");
                tables.close();
                return;
            }
            tables.close();
            
            // 创建stores表
            String createTableSql = "CREATE TABLE stores (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "store_name VARCHAR(200) NOT NULL COMMENT '店铺名称', " +
                    "store_code VARCHAR(50) NOT NULL UNIQUE COMMENT '店铺编码', " +
                    "description TEXT COMMENT '店铺描述', " +
                    "owner_name VARCHAR(100) COMMENT '店主姓名', " +
                    "owner_phone VARCHAR(20) COMMENT '店主电话', " +
                    "address VARCHAR(500) COMMENT '店铺地址', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态：ACTIVE-营业中，INACTIVE-已关闭', " +
                    "product_list_version BIGINT NOT NULL DEFAULT 0 COMMENT '商品列表版本号（用于缓存一致性控制）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_store_code (store_code), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表（不分库，存储在主库）'";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createTableSql);
                log.info("✓ stores表创建成功！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("Table 'stores' already exists"))) {
                log.info("stores表已存在，跳过创建");
            } else {
                log.error("创建stores表失败: {}", errorMsg, e);
            }
        }
    }
    
    /**
     * 创建outbox_node表
     */
    private void createOutboxNodeTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "outbox_node", null);
            
            if (tables.next()) {
                log.info("outbox_node表已存在，跳过创建");
                tables.close();
                return;
            }
            tables.close();
            
            // 创建outbox_node表
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
    
    /**
     * 创建outbox表（如果不存在）
     */
    private void createOutboxTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "outbox", null);
            
            if (tables.next()) {
                log.info("outbox表已存在，检查shard_id列...");
                tables.close();
                // 检查并添加shard_id列（如果不存在）
                addShardIdToOutbox(conn, metaData);
                return;
            }
            tables.close();
            
            // 创建outbox表（包含shard_id列）
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
    
    /**
     * 为outbox表添加shard_id列（如果不存在）
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
    
    /**
     * 创建 snowflake_worker 表（用于管理雪花算法 worker-id）
     */
    private void createSnowflakeWorkerTable(Connection conn, DatabaseMetaData metaData) {
        try {
            ResultSet tables = metaData.getTables(null, null, "snowflake_worker", null);
            
            if (tables.next()) {
                log.info("snowflake_worker表已存在，跳过创建");
                tables.close();
                return;
            }
            tables.close();
            
            // 创建 snowflake_worker 表
            String createTableSql = "CREATE TABLE snowflake_worker (" +
                    "worker_id INT PRIMARY KEY COMMENT '工作机器ID（0-1023）', " +
                    "instance VARCHAR(128) NOT NULL UNIQUE COMMENT '实例标识（hostname或IP:PORT）', " +
                    "last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后心跳时间', " +
                    "INDEX idx_last_heartbeat (last_heartbeat)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='雪花算法Worker-ID分配表'";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createTableSql);
                log.info("✓ snowflake_worker表创建成功！");
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("Table 'snowflake_worker' already exists"))) {
                log.info("snowflake_worker表已存在，跳过创建");
            } else {
                log.error("创建snowflake_worker表失败: {}", errorMsg, e);
            }
        }
    }
}


