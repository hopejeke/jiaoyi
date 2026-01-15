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
            
            // 2. 创建支付回调日志表、Webhook 日志表和商户配置表（在 jiaoyi 基础数据库中）
            String dbUrl = buildDatabaseUrl(actualJdbcUrl, "jiaoyi");
            log.info("连接基础数据库URL: {}", dbUrl);
            try (Connection conn = DriverManager.getConnection(dbUrl, actualUsername, actualPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();
                // outbox_node 表已删除，不再创建
                // outbox 表在分片库中创建（见 createOutboxTable 方法）
                createPaymentCallbackLogTable(conn, metaData);
                createWebhookEventLogTable(conn, metaData);
                createConsumerLogTable(conn, metaData);
                createDoorDashWebhookLogTable(conn, metaData);
                // 商户配置表迁移到基础库单表
                createMerchantConfigTables(conn, metaData);
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
                String dbName = "jiaoyi_order_" + dbIndex;
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
            // 订单服务专用数据库：jiaoyi_order_0/1/2
            for (int i = 0; i < 3; i++) {
                String dbName = "jiaoyi_order_" + i;
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
            
            // 更新订单表结构（如果表已存在，添加缺失的列，如 DoorDash 相关字段）
            updateOrdersTables(stmt, dbIndex);
            
            // 创建订单项表分片
            createOrderItemsTables(stmt, dbIndex);
            
            // 更新订单项表结构（如果表已存在，添加缺失的列）
            updateOrderItemsTables(stmt, dbIndex);
            
            // 创建订单优惠券关联表分片
            createOrderCouponsTables(stmt, dbIndex);
            
            // 创建支付记录表分片
            createPaymentsTables(stmt, dbIndex);
            
            // 创建退款单表分片
            createRefundsTables(stmt, dbIndex);
            
            // 注意：商户配置表（merchant_stripe_config, merchant_fee_config, merchant_capability_config）
            // 已迁移到基础库 jiaoyi 单表，不再在此创建分片表
            
            // 创建配送表分片（32张表/库）
            createDeliveriesTables(stmt, dbIndex);
            
            // 创建 DoorDash 重试任务表分片（32张表/库，使用shard_id分片）
            createDoorDashRetryTaskTables(stmt, dbIndex);
            
            // 创建 outbox 表（32张表/库，与订单同shard_id）
            createOutboxTable(stmt, dbIndex);
            
            // 注意：coupon_usage 表属于 coupon-service，不在 order-service 中创建
            // 如果需要，可以在 coupon-service 的 DatabaseInitializer 中创建
            
            log.info("✓ 数据库 {} 的所有分片表创建完成", dbName);
            
        } catch (Exception e) {
            log.error("为数据库 {} 创建分片表失败: {}", dbName, e.getMessage(), e);
        }
    }
    
    /**
     * 创建订单表分片（32张表/库：orders_00..orders_31）
     * 使用 shard_id 作为分片键（基于 brandId 计算，固定1024个虚拟桶）
     */
    private void createOrdersTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "orders_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT NOT NULL PRIMARY KEY COMMENT '订单ID（雪花算法生成）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "user_id BIGINT NOT NULL COMMENT '用户ID', " +
                    "order_type VARCHAR(20) NOT NULL COMMENT '订单类型：PICKUP/DELIVERY/SELF_DINE_IN', " +
                    "status INT NOT NULL DEFAULT 1 COMMENT '订单状态：1-已下单，2-已支付，3-制作中，4-已完成，5-已取消，7-支付失败，8-配送中，9-待接单', " +
                    "local_status INT NOT NULL DEFAULT 1 COMMENT '本地订单状态：1-已下单，100-成功，200-支付失败', " +
                    "kitchen_status INT NOT NULL DEFAULT 1 COMMENT '厨房状态：1-待送厨，2-部分送厨，3-完全送厨，4-完成', " +
                    "order_price TEXT COMMENT '订单价格信息（JSON）', " +
                    "customer_info TEXT COMMENT '客户信息（JSON）', " +
                    "delivery_address TEXT COMMENT '配送地址（JSON）', " +
                    "notes TEXT COMMENT '备注', " +
                    "pos_order_id VARCHAR(100) COMMENT 'POS系统订单ID', " +
                    "payment_method VARCHAR(50) COMMENT '支付方式', " +
                    "payment_status VARCHAR(50) COMMENT '支付状态', " +
                    "stripe_payment_intent_id VARCHAR(200) COMMENT 'Stripe支付意图ID', " +
                    "refund_amount DECIMAL(10,2) COMMENT '退款金额', " +
                    "refund_reason VARCHAR(500) COMMENT '退款原因', " +
                    "delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID（外键，关联deliveries.id）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_user_id (user_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time), " +
                    "INDEX idx_delivery_id (delivery_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单表分片创建完成（32个分片表：orders_00..orders_31）");
    }
    
    /**
     * 更新订单表结构（如果表已存在，添加缺失的列，如 DoorDash 相关字段）
     * 用于从旧表结构迁移到新表结构
     */
    private void updateOrdersTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "orders_" + String.format("%02d", tableIndex);
            try {
                // 检查表是否存在
                java.sql.ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE '" + tableName + "'");
                if (!rs.next()) {
                    // 表不存在，跳过更新
                    rs.close();
                    continue;
                }
                rs.close();
                
                // 添加 shard_id 字段（如果不存在）
                String[] alterStatements = {
                    "ALTER TABLE " + tableName + " ADD COLUMN shard_id INT NOT NULL DEFAULT 0 COMMENT '分片ID（0-1023，基于brandId计算）' AFTER merchant_id",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID（外键，关联deliveries.id）'"
                };
                
                for (String sql : alterStatements) {
                    try {
                        stmt.executeUpdate(sql);
                        log.debug("为表 {} 添加列成功", tableName);
                    } catch (java.sql.SQLException e) {
                        // 列已存在或其他错误，忽略
                        if (!e.getMessage().contains("Duplicate column name") && 
                            !e.getMessage().contains("already exists") &&
                            !e.getMessage().contains("Duplicate column")) {
                            log.debug("更新表 {} 结构时出错（可忽略）: {}", tableName, e.getMessage());
                        }
                    }
                }
                
                // 添加索引（如果不存在）
                String[] indexStatements = {
                    "ALTER TABLE " + tableName + " ADD INDEX idx_shard_id (shard_id)",
                    "ALTER TABLE " + tableName + " ADD INDEX idx_delivery_id (delivery_id)"
                };
                for (String indexSql : indexStatements) {
                    try {
                        stmt.executeUpdate(indexSql);
                    } catch (java.sql.SQLException e) {
                        // 索引已存在，忽略
                        if (!e.getMessage().contains("Duplicate key name") && 
                            !e.getMessage().contains("already exists")) {
                            log.debug("为表 {} 添加索引时出错（可忽略）: {}", tableName, e.getMessage());
                        }
                    }
                }
                
            } catch (Exception e) {
                log.warn("更新表 {} 结构失败: {}", tableName, e.getMessage());
            }
        }
        log.info("  ✓ 订单表结构更新完成（32个分片表）");
    }
    
    /**
     * 创建订单项表分片（32张表/库：order_items_00..order_items_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createOrderItemsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "order_items_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT NOT NULL PRIMARY KEY COMMENT '订单项ID（雪花算法生成）', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "product_id BIGINT COMMENT '商品ID（用于库存锁定）', " +
                    "sku_id BIGINT COMMENT 'SKU ID（关联 product_sku.id，如果商品有SKU则必须提供）', " +
                    "sale_item_id BIGINT COMMENT '销售项ID（POS系统ID）', " +
                    "order_item_id BIGINT COMMENT '订单项ID', " +
                    "item_name VARCHAR(200) NOT NULL COMMENT '商品名称', " +
                    "sku_name VARCHAR(200) COMMENT 'SKU名称（冗余字段，用于显示）', " +
                    "sku_attributes TEXT COMMENT 'SKU属性（JSON格式，冗余字段，用于显示）', " +
                    "product_image VARCHAR(500) COMMENT '商品图片', " +
                    "item_price DECIMAL(10,2) NOT NULL COMMENT '商品单价', " +
                    "quantity INT NOT NULL COMMENT '购买数量', " +
                    "item_price_total DECIMAL(10,2) NOT NULL COMMENT '小计金额', " +
                    "display_price DECIMAL(10,2) COMMENT '显示价格', " +
                    "detail_price_id BIGINT COMMENT '详细价格ID', " +
                    "detail_price_info TEXT COMMENT '详细价格信息（JSON）', " +
                    "size_id BIGINT COMMENT '尺寸ID', " +
                    "options TEXT COMMENT '选项（JSON数组）', " +
                    "combo_detail TEXT COMMENT '套餐详情（JSON）', " +
                    "discount_name VARCHAR(200) COMMENT '折扣名称', " +
                    "charge_name VARCHAR(200) COMMENT '费用名称', " +
                    "course_number VARCHAR(50) COMMENT '课程编号', " +
                    "item_type VARCHAR(50) COMMENT '商品类型', " +
                    "item_number VARCHAR(50) COMMENT '商品编号', " +
                    "kitchen_index INT COMMENT '厨房索引', " +
                    "category_id BIGINT COMMENT '分类ID', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_product_id (product_id), " +
                    "INDEX idx_sku_id (sku_id), " +
                    "INDEX idx_sale_item_id (sale_item_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单项表分片创建完成（32个分片表：order_items_00..order_items_31）");
    }
    
    /**
     * 更新订单项表结构（如果表已存在，添加缺失的列）
     * 用于从旧表结构迁移到新表结构
     */
    private void updateOrderItemsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "order_items_" + tableIndex;
            try {
                // 检查表是否存在
                java.sql.ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE '" + tableName + "'");
                if (!rs.next()) {
                    // 表不存在，跳过更新
                    rs.close();
                    continue;
                }
                rs.close();
                
                // 由于 MySQL 不支持 IF NOT EXISTS，我们需要先检查列是否存在
                // 这里简化处理：直接执行 ALTER，如果列已存在会报错，我们捕获并忽略
                String[] alterStatements = {
                    "ALTER TABLE " + tableName + " MODIFY COLUMN id BIGINT NOT NULL COMMENT '订单项ID（雪花算法生成）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN merchant_id VARCHAR(50) NOT NULL DEFAULT '' COMMENT '餐馆ID（分片键）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN product_id BIGINT COMMENT '商品ID（用于库存锁定）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN sku_id BIGINT COMMENT 'SKU ID（关联 product_sku.id，如果商品有SKU则必须提供）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN sku_name VARCHAR(200) COMMENT 'SKU名称（冗余字段，用于显示）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN sku_attributes TEXT COMMENT 'SKU属性（JSON格式，冗余字段，用于显示）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN sale_item_id BIGINT COMMENT '销售项ID（POS系统ID）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN order_item_id BIGINT COMMENT '订单项ID'",
                    "ALTER TABLE " + tableName + " ADD COLUMN item_name VARCHAR(200) COMMENT '商品名称'",
                    "ALTER TABLE " + tableName + " ADD COLUMN product_image VARCHAR(500) COMMENT '商品图片'",
                    "ALTER TABLE " + tableName + " ADD COLUMN item_price DECIMAL(10,2) COMMENT '商品单价'",
                    "ALTER TABLE " + tableName + " ADD COLUMN item_price_total DECIMAL(10,2) COMMENT '小计金额'",
                    "ALTER TABLE " + tableName + " ADD COLUMN display_price DECIMAL(10,2) COMMENT '显示价格'",
                    "ALTER TABLE " + tableName + " ADD COLUMN detail_price_id BIGINT COMMENT '详细价格ID'",
                    "ALTER TABLE " + tableName + " ADD COLUMN detail_price_info TEXT COMMENT '详细价格信息（JSON）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN size_id BIGINT COMMENT '尺寸ID'",
                    "ALTER TABLE " + tableName + " ADD COLUMN options TEXT COMMENT '选项（JSON数组）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN combo_detail TEXT COMMENT '套餐详情（JSON）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN discount_name VARCHAR(200) COMMENT '折扣名称'",
                    "ALTER TABLE " + tableName + " ADD COLUMN charge_name VARCHAR(200) COMMENT '费用名称'",
                    "ALTER TABLE " + tableName + " ADD COLUMN course_number VARCHAR(50) COMMENT '课程编号'",
                    "ALTER TABLE " + tableName + " ADD COLUMN item_type VARCHAR(50) COMMENT '商品类型'",
                    "ALTER TABLE " + tableName + " ADD COLUMN item_number VARCHAR(50) COMMENT '商品编号'",
                    "ALTER TABLE " + tableName + " ADD COLUMN kitchen_index INT COMMENT '厨房索引'",
                    "ALTER TABLE " + tableName + " ADD COLUMN category_id BIGINT COMMENT '分类ID'",
                    "ALTER TABLE " + tableName + " ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）'"
                };
                
                for (String sql : alterStatements) {
                    try {
                        stmt.executeUpdate(sql);
                    } catch (java.sql.SQLException e) {
                        // 列已存在或其他错误，忽略
                        if (!e.getMessage().contains("Duplicate column name") && !e.getMessage().contains("already exists")) {
                            log.debug("更新表 {} 结构时出错（可忽略）: {}", tableName, e.getMessage());
                        }
                    }
                }
                
                // 重命名旧列（如果存在）
                try {
                    stmt.executeUpdate("ALTER TABLE " + tableName + " CHANGE COLUMN product_name item_name VARCHAR(200) COMMENT '商品名称'");
                } catch (java.sql.SQLException e) {
                    // 列不存在或已重命名，忽略
                }
                try {
                    stmt.executeUpdate("ALTER TABLE " + tableName + " CHANGE COLUMN unit_price item_price DECIMAL(10,2) COMMENT '商品单价'");
                } catch (java.sql.SQLException e) {
                    // 列不存在或已重命名，忽略
                }
                try {
                    stmt.executeUpdate("ALTER TABLE " + tableName + " CHANGE COLUMN subtotal item_price_total DECIMAL(10,2) COMMENT '小计金额'");
                } catch (java.sql.SQLException e) {
                    // 列不存在或已重命名，忽略
                }
                
                // 创建索引（如果不存在会报错，忽略）
                try {
                    stmt.executeUpdate("CREATE INDEX idx_product_id ON " + tableName + "(product_id)");
                } catch (java.sql.SQLException e) {
                    // 索引已存在，忽略
                    if (!e.getMessage().contains("Duplicate key name") && !e.getMessage().contains("already exists")) {
                        log.debug("创建索引 idx_product_id 时出错（可忽略）: {}", e.getMessage());
                    }
                }
                try {
                    stmt.executeUpdate("CREATE INDEX idx_sku_id ON " + tableName + "(sku_id)");
                } catch (java.sql.SQLException e) {
                    // 索引已存在，忽略
                    if (!e.getMessage().contains("Duplicate key name") && !e.getMessage().contains("already exists")) {
                        log.debug("创建索引 idx_sku_id 时出错（可忽略）: {}", e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.warn("更新表 {} 结构时出错: {}", tableName, e.getMessage());
            }
        }
        log.info("  ✓ 订单项表结构更新完成（3个分片表）");
    }
    
    /**
     * 创建订单优惠券关联表分片（32张表/库：order_coupons_00..order_coupons_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createOrderCouponsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "order_coupons_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "store_id BIGINT NOT NULL DEFAULT 0 COMMENT '门店ID（用于分片）', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于storeId计算）', " +
                    "coupon_id BIGINT NOT NULL COMMENT '优惠券ID', " +
                    "coupon_code VARCHAR(50) NOT NULL COMMENT '优惠券代码', " +
                    "applied_amount DECIMAL(10,2) NOT NULL COMMENT '该优惠券实际抵扣金额', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_store_id (store_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_coupon_id (coupon_id), " +
                    "INDEX idx_coupon_code (coupon_code)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单优惠券关联表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单优惠券关联表分片创建完成（32个分片表：order_coupons_00..order_coupons_31）");
    }
    
    /**
     * 创建支付记录表分片（32张表/库：payments_00..payments_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createPaymentsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "payments_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '支付ID', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）', " +
                    "transaction_id BIGINT COMMENT '交易ID（用于关联交易记录）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "status INT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付，100-成功，-1-失败', " +
                    "type INT NOT NULL DEFAULT 1 COMMENT '支付类型：1-扣款，2-退款', " +
                    "category INT NOT NULL COMMENT '支付方式类别：1-支付宝，2-微信支付，3-信用卡，4-现金', " +
                    "payment_service VARCHAR(50) NOT NULL COMMENT '支付服务：ALIPAY, WECHAT_PAY, CASH, STRIPE', " +
                    "payment_no VARCHAR(100) NOT NULL UNIQUE COMMENT '支付流水号', " +
                    "third_party_trade_no VARCHAR(100) COMMENT '第三方支付平台交易号', " +
                    "amount DECIMAL(10,2) NOT NULL COMMENT '支付金额', " +
                    "tip_amount DECIMAL(10,2) DEFAULT 0 COMMENT '小费金额', " +
                    "order_price TEXT COMMENT '订单价格信息（JSON）', " +
                    "card_info TEXT COMMENT '卡片信息（JSON，用于信用卡支付）', " +
                    "extra TEXT COMMENT '额外信息（JSON，存储第三方支付平台的完整响应）', " +
                    "stripe_payment_intent_id VARCHAR(100) COMMENT 'Stripe Payment Intent ID（用于异步支付）', " +
                    "version INT NOT NULL DEFAULT 1 COMMENT '版本号（用于乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_payment_no (payment_no), " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_type (type), " +
                    "INDEX idx_payment_service (payment_service), " +
                    "INDEX idx_third_party_trade_no (third_party_trade_no), " +
                    "INDEX idx_stripe_payment_intent_id (stripe_payment_intent_id), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 支付记录表分片创建完成（32个分片表：payments_00..payments_31）");
    }
    
    /**
     * 创建退款单表分片（32张表/库：refunds_00..refunds_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createRefundsTables(Statement stmt, int dbIndex) throws Exception {
        // 创建退款单表分片
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "refunds_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "refund_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '退款ID', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "payment_id BIGINT COMMENT '关联的支付记录ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "request_no VARCHAR(100) NOT NULL COMMENT '退款请求号（幂等键）', " +
                    "refund_amount DECIMAL(10,2) NOT NULL COMMENT '退款总金额', " +
                    "reason VARCHAR(500) COMMENT '退款原因', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '退款状态：CREATED, PROCESSING, SUCCEEDED, FAILED, CANCELED', " +
                    "third_party_refund_id VARCHAR(100) COMMENT '第三方退款ID（Stripe refund_id 或支付宝退款单号）', " +
                    "error_message TEXT COMMENT '失败原因', " +
                    "commission_reversal DECIMAL(10,2) DEFAULT 0 COMMENT '抽成回补金额（平台手续费回补）', " +
                    "version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "processed_at DATETIME COMMENT '处理完成时间', " +
                    "UNIQUE KEY uk_request_no (merchant_id, request_no), " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_payment_id (payment_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_created_at (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 退款单表分片创建完成（32个分片表：refunds_00..refunds_31）");
        
        // 创建退款明细表分片（32张表/库）
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "refund_items_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "refund_item_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "refund_id BIGINT NOT NULL COMMENT '退款单ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "order_item_id BIGINT COMMENT '订单项ID（如果按商品退款）', " +
                    "subject VARCHAR(50) NOT NULL COMMENT '退款科目：ITEM, TAX, DELIVERY_FEE, TIPS, CHARGE, DISCOUNT', " +
                    "refund_qty INT COMMENT '退款数量（仅商品退款时有效）', " +
                    "refund_amount DECIMAL(10,2) NOT NULL COMMENT '退款金额', " +
                    "tax_refund DECIMAL(10,2) DEFAULT 0 COMMENT '税费退款（仅商品退款时有效）', " +
                    "discount_refund DECIMAL(10,2) DEFAULT 0 COMMENT '折扣退款（仅商品退款时有效）', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "INDEX idx_refund_id (refund_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_order_item_id (order_item_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款明细表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 退款明细表分片创建完成（32个分片表：refund_items_00..refund_items_31）");
        
        // 创建退款幂等性日志表分片
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "refund_idempotency_logs_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "request_no VARCHAR(100) NOT NULL COMMENT '退款请求号（幂等键）', " +
                    "refund_id BIGINT COMMENT '退款ID（关联到 refunds 表）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）', " +
                    "fingerprint VARCHAR(64) COMMENT '请求指纹（MD5）', " +
                    "request_params TEXT COMMENT '请求参数（JSON格式）', " +
                    "result VARCHAR(20) COMMENT '处理结果（SUCCESS/FAILED）', " +
                    "error_message TEXT COMMENT '错误信息', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_request_no (merchant_id, order_id, request_no), " +
                    "INDEX idx_fingerprint (fingerprint), " +
                    "INDEX idx_refund_id (refund_id), " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_created_at (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款幂等性日志表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 退款幂等性日志表分片创建完成（3个分片表）");
    }
    
    private void createMerchantStripeConfigTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "merchant_stripe_config_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）', " +
                    "stripe_account_id VARCHAR(100) COMMENT 'Stripe Connected Account ID（acct_xxx）', " +
                    "enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 Stripe Connect', " +
                    "currency VARCHAR(10) DEFAULT 'USD' COMMENT '货币代码（USD, CAD 等）', " +
                    "application_fee_percentage DECIMAL(5,2) DEFAULT 2.50 COMMENT '平台手续费率（百分比）', " +
                    "application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '平台固定手续费（元）', " +
                    "amex_application_fee_percentage DECIMAL(5,2) DEFAULT 3.50 COMMENT '美国运通手续费率（百分比）', " +
                    "amex_application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '美国运通固定手续费（元）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_merchant_id (merchant_id), " +
                    "INDEX idx_stripe_account_id (stripe_account_id), " +
                    "INDEX idx_enabled (enabled)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户Stripe配置表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 商户Stripe配置表分片创建完成（3个分片表）");
    }
    
    private void createMerchantFeeConfigTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "merchant_fee_config_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                    "merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID（分片键）', " +
                    "delivery_fee_type VARCHAR(20) DEFAULT 'FLAT_RATE' COMMENT '配送费类型：FLAT_RATE-固定费率，VARIABLE_RATE-按距离可变费率，ZONE_RATE-按邮编区域费率', " +
                    "delivery_fee_fixed DECIMAL(10,2) DEFAULT 5.00 COMMENT '配送费固定金额（元，FLAT_RATE 时使用）', " +
                    "delivery_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '配送费百分比（已废弃，保留兼容）', " +
                    "delivery_fee_min DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最低金额（元）', " +
                    "delivery_fee_max DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最高金额（元）', " +
                    "delivery_fee_free_threshold DECIMAL(10,2) DEFAULT 0.00 COMMENT '免配送费门槛（订单金额达到此金额免配送费，元）', " +
                    "delivery_variable_rate TEXT COMMENT '按距离的可变费率（JSON格式，VARIABLE_RATE 时使用）', " +
                    "delivery_zone_rate TEXT COMMENT '按邮编区域的费率（JSON格式，ZONE_RATE 时使用）', " +
                    "delivery_maximum_distance DECIMAL(10,2) DEFAULT 0.00 COMMENT '最大配送距离（英里，mile）', " +
                    "merchant_latitude DECIMAL(10,7) COMMENT '商户纬度（用于计算配送距离）', " +
                    "merchant_longitude DECIMAL(10,7) COMMENT '商户经度（用于计算配送距离）', " +
                    "delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）', " +
                    "tax_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '税率（百分比，如 8.0 表示 8%）', " +
                    "tax_exempt TINYINT(1) DEFAULT 0 COMMENT '是否免税：1-免税，0-不免税', " +
                    "online_service_fee_type VARCHAR(20) DEFAULT 'NONE' COMMENT '在线服务费类型：FIXED-固定费用，PERCENTAGE-百分比，NONE-无', " +
                    "online_service_fee_fixed DECIMAL(10,2) DEFAULT 0.00 COMMENT '在线服务费固定金额（元）', " +
                    "online_service_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '在线服务费百分比', " +
                    "online_service_fee_strategy TEXT COMMENT '在线服务费策略（JSON格式，存储阶梯费率配置）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_merchant_id (merchant_id), " +
                    "INDEX idx_delivery_fee_type (delivery_fee_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户费用配置表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 商户费用配置表分片创建完成（3个分片表）");
    }
    
    private void createMerchantCapabilityConfigTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "merchant_capability_config_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）', " +
                    "enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用', " +
                    "qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）', " +
                    "time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）', " +
                    "closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）', " +
                    "next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）', " +
                    "re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）', " +
                    "operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动', " +
                    "operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动', " +
                    "operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动', " +
                    "operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "UNIQUE KEY uk_merchant_id (merchant_id), " +
                    "INDEX idx_enable (enable), " +
                    "INDEX idx_next_open_at (next_open_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 商户能力配置表分片创建完成（3个分片表）");
    }
    
    /**
     * 创建支付回调日志表（用于幂等性去重）
     * 基于 thirdPartyTradeNo 去重，记录每次支付回调
     */
    private void createPaymentCallbackLogTable(Connection conn, DatabaseMetaData metaData) {
        try (Statement stmt = conn.createStatement()) {
            // 检查表是否存在
            ResultSet rs = metaData.getTables(null, null, "payment_callback_log", null);
            if (rs.next()) {
                rs.close();
                log.info("payment_callback_log表已存在，跳过创建");
                return;
            }
            rs.close();
            
            String createTableSql = "CREATE TABLE payment_callback_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "payment_id BIGINT COMMENT '支付记录ID', " +
                    "third_party_trade_no VARCHAR(100) NOT NULL UNIQUE COMMENT '第三方交易号（唯一键，用于去重）', " +
                    "payment_service VARCHAR(50) NOT NULL COMMENT '支付服务：STRIPE, ALIPAY, WECHAT_PAY', " +
                    "callback_data TEXT COMMENT '回调数据（JSON，存储完整的回调数据）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '处理状态：SUCCESS, FAILED, PROCESSING', " +
                    "result TEXT COMMENT '处理结果（JSON，存储处理结果）', " +
                    "error_message TEXT COMMENT '错误信息（如果处理失败）', " +
                    "processed_at DATETIME COMMENT '处理时间', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_payment_id (payment_id), " +
                    "INDEX idx_payment_service (payment_service), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付回调日志表（用于幂等性去重）'";
            
            stmt.executeUpdate(createTableSql);
            log.info("✓ payment_callback_log 表创建成功");
        } catch (Exception e) {
            log.error("创建 payment_callback_log 表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建 Webhook 事件日志表（用于事件幂等）
     */
    private void createWebhookEventLogTable(Connection conn, DatabaseMetaData metaData) {
        try (Statement stmt = conn.createStatement()) {
            // 检查表是否存在
            ResultSet rs = metaData.getTables(null, null, "webhook_event_log", null);
            if (rs.next()) {
                rs.close();
                log.info("webhook_event_log表已存在，跳过创建");
                return;
            }
            rs.close();
            
            String createTableSql = "CREATE TABLE webhook_event_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "event_id VARCHAR(255) NOT NULL COMMENT '第三方事件ID（Stripe event.id 或支付宝交易号）', " +
                    "event_type VARCHAR(100) NOT NULL COMMENT '事件类型（payment_intent.succeeded, TRADE_SUCCESS 等）', " +
                    "payment_intent_id VARCHAR(255) COMMENT 'Stripe Payment Intent ID', " +
                    "third_party_trade_no VARCHAR(255) COMMENT '第三方交易号（支付宝 trade_no 或 Stripe charge.id）', " +
                    "order_id BIGINT COMMENT '订单ID', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT '状态：RECEIVED-已接收，PROCESSED-已处理，FAILED-处理失败', " +
                    "error_message TEXT COMMENT '错误信息（处理失败时记录）', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "processed_at DATETIME COMMENT '处理完成时间', " +
                    "UNIQUE KEY uk_event_id (event_id), " +
                    "INDEX idx_payment_intent_id (payment_intent_id), " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_created_at (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook事件日志表（事件幂等）'";
            
            stmt.executeUpdate(createTableSql);
            log.info("✓ webhook_event_log 表创建成功");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("already exists")) {
                log.info("webhook_event_log表已存在，跳过创建");
            } else {
                log.error("创建webhook_event_log表失败: {}", errorMsg, e);
            }
        }
    }
    
    /**
     * 创建 MQ 消费日志表（用于消费幂等）
     */
    private void createConsumerLogTable(Connection conn, DatabaseMetaData metaData) {
        try (Statement stmt = conn.createStatement()) {
            // 检查表是否存在
            ResultSet rs = metaData.getTables(null, null, "consumer_log", null);
            if (rs.next()) {
                rs.close();
                log.info("consumer_log表已存在，跳过创建");
                return;
            }
            rs.close();
            
            String createTableSql = "CREATE TABLE consumer_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "consumer_group VARCHAR(100) NOT NULL COMMENT '消费者组（RocketMQ Consumer Group）', " +
                    "topic VARCHAR(100) NOT NULL COMMENT 'Topic', " +
                    "tag VARCHAR(50) COMMENT 'Tag', " +
                    "message_key VARCHAR(255) NOT NULL COMMENT '消息Key（eventId 或 idempotencyKey）', " +
                    "message_id VARCHAR(100) COMMENT 'RocketMQ MessageId（用于追踪）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败', " +
                    "error_message TEXT COMMENT '错误信息（处理失败时记录）', " +
                    "retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数', " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "processed_at DATETIME COMMENT '处理完成时间', " +
                    "UNIQUE KEY uk_consumer_message (consumer_group, message_key), " +
                    "INDEX idx_topic (topic), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_created_at (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费日志表（消费幂等）'";
            
            stmt.executeUpdate(createTableSql);
            log.info("✓ consumer_log 表创建成功");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("already exists")) {
                log.info("consumer_log表已存在，跳过创建");
            } else {
                log.error("创建consumer_log表失败: {}", errorMsg, e);
            }
        }
    }
    
    /**
     * 创建配送表分片（32张表/库：deliveries_00..deliveries_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createDeliveriesTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "deliveries_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id VARCHAR(100) NOT NULL PRIMARY KEY COMMENT '配送ID（DoorDash 返回的 delivery_id）', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "external_delivery_id VARCHAR(100) COMMENT '外部订单ID（external_delivery_id，格式：order_123）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT 'DoorDash 配送状态：CREATED, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED, FAILED', " +
                    "delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash 报价费用', " +
                    "delivery_fee_quoted_at DATETIME COMMENT 'DoorDash 报价时间（用于检查报价是否过期）', " +
                    "delivery_fee_quote_id VARCHAR(100) COMMENT 'DoorDash 报价ID（quote_id，用于接受报价）', " +
                    "delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费', " +
                    "delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash 账单费用', " +
                    "delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）', " +
                    "tracking_url VARCHAR(500) COMMENT 'DoorDash 配送跟踪 URL', " +
                    "distance_miles DECIMAL(10,2) COMMENT '配送距离（英里）', " +
                    "eta_minutes INT COMMENT '预计送达时间（分钟）', " +
                    "driver_name VARCHAR(100) COMMENT '骑手姓名', " +
                    "driver_phone VARCHAR(20) COMMENT '骑手电话', " +
                    "additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_external_delivery_id (external_delivery_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("✓ 配送表分片创建完成（32个分片表：deliveries_00..deliveries_31）");
    }
    
    /**
     * 创建 DoorDash 重试任务表分片（32张表/库：doordash_retry_task_00..doordash_retry_task_31）
     * 使用 shard_id 作为分片键，与 orders 表保持一致
     */
    private void createDoorDashRetryTaskTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableName = "doordash_retry_task_" + String.format("%02d", tableIndex);
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT NOT NULL PRIMARY KEY COMMENT '任务ID（雪花算法生成）', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID', " +
                    "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算）', " +
                    "payment_id BIGINT COMMENT '支付ID（关联payments.id）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待重试，RETRYING-重试中，SUCCESS-成功，FAILED-失败，MANUAL-需要人工介入', " +
                    "retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数', " +
                    "max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数', " +
                    "error_message TEXT COMMENT '错误信息（最后一次失败的错误信息）', " +
                    "error_stack TEXT COMMENT '错误堆栈（最后一次失败的错误堆栈）', " +
                    "next_retry_time DATETIME COMMENT '下次重试时间（用于延迟重试）', " +
                    "last_retry_time DATETIME COMMENT '最后重试时间', " +
                    "success_time DATETIME COMMENT '成功时间（创建成功时记录）', " +
                    "manual_intervention_time DATETIME COMMENT '人工介入时间（标记为需要人工介入时记录）', " +
                    "manual_intervention_note TEXT COMMENT '人工介入备注', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_shard_id (shard_id), " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_next_retry_time (next_retry_time), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DoorDash重试任务表_库" + dbIndex + "_分片" + String.format("%02d", tableIndex) + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("✓ DoorDash重试任务表分片创建完成（32个分片表：doordash_retry_task_00..doordash_retry_task_31）");
    }
    
    /**
     * 创建 outbox 表（分库+分表，每个库32张表：outbox_00..outbox_31）
     * 用于可靠事件发布（Outbox Pattern）
     * 注意：表名统一为 outbox（不再使用 order_outbox），通过数据库隔离不同服务
     */
    private void createOutboxTable(Statement stmt, int dbIndex) throws Exception {
        // 创建32张分表：outbox_00..outbox_31
        for (int tableIndex = 0; tableIndex < 32; tableIndex++) {
            String tableSuffix = String.format("%02d", tableIndex);
            String tableName = "outbox_" + tableSuffix;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID', " +
                "type VARCHAR(100) NOT NULL COMMENT '任务类型（如：DEDUCT_STOCK_HTTP、PAYMENT_SUCCEEDED_MQ）', " +
                "biz_key VARCHAR(255) NOT NULL COMMENT '业务键（如：orderId，用于唯一约束和幂等）', " +
                "sharding_key VARCHAR(100) COMMENT '通用分片键（业务方可以存 merchantId, storeId, userId 等）', " +
                "shard_id INT NOT NULL COMMENT '分片ID（0-1023，基于brandId计算，用于分库分表路由）', " +
                "event_id VARCHAR(255) COMMENT '事件ID（用于唯一约束和幂等，可选）', " +
                "topic VARCHAR(100) COMMENT 'RocketMQ Topic（MQ 类型任务使用）', " +
                "tag VARCHAR(50) COMMENT 'RocketMQ Tag（MQ 类型任务使用）', " +
                "message_key VARCHAR(255) COMMENT '消息Key（用于消息追踪，MQ 类型任务使用）', " +
                "payload TEXT NOT NULL COMMENT '任务负载（JSON格式）', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW-新建，PROCESSING-处理中，SUCCESS-成功，FAILED-失败，DEAD-死信', " +
                "retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数', " +
                "next_retry_time DATETIME COMMENT '下次重试时间', " +
                "lock_owner VARCHAR(100) COMMENT '锁持有者（实例ID，用于多实例抢锁）', " +
                "lock_time DATETIME COMMENT '锁定时间', " +
                "lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）', " +
                "last_error TEXT COMMENT '最后错误信息', " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                "completed_at DATETIME COMMENT '完成时间（SUCCESS 时记录）', " +
                "message_body TEXT COMMENT '消息体（兼容旧字段，新版本使用 payload）', " +
                "error_message TEXT COMMENT '错误信息（兼容旧字段）', " +
                "sent_at DATETIME COMMENT '发送时间（兼容旧字段）', " +
                "UNIQUE KEY uk_type_biz (type, biz_key), " +
                "UNIQUE KEY uk_event_id (event_id), " +
                "INDEX idx_status (status), " +
                "INDEX idx_created_at (created_at), " +
                "INDEX idx_next_retry_time (next_retry_time), " +
                "INDEX idx_sharding_key (sharding_key), " +
                "INDEX idx_shard_id (shard_id), " +
                "INDEX idx_shard_id_status (shard_id, status), " +
                "INDEX idx_lock_owner (lock_owner), " +
                "INDEX idx_status_next_retry (status, next_retry_time), " +
                "INDEX idx_claim (shard_id, status, next_retry_time, lock_until, id), " +
                "INDEX idx_cleanup (shard_id, status, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠任务表（Outbox Pattern）_库" + dbIndex + "_表" + tableSuffix + "'";
            stmt.executeUpdate(createTableSql);
            
            // 检查并添加缺失的列（如果表已存在但缺少这些列）
            try {
                Connection conn = stmt.getConnection();
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog(); // 获取当前数据库名称
                
                // 检查并添加 sharding_key 列（通用分片键字段）
                ResultSet shardingKeyColumns = metaData.getColumns(catalog, null, tableName, "sharding_key");
                if (!shardingKeyColumns.next()) {
                    log.info("为 {} 表添加 sharding_key 列", tableName);
                    stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN sharding_key VARCHAR(100) COMMENT '分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等，用于分库路由）'");
                    stmt.executeUpdate("ALTER TABLE " + tableName + " ADD INDEX idx_sharding_key (sharding_key)");
                    stmt.executeUpdate("ALTER TABLE " + tableName + " ADD INDEX idx_sharding_key_status (sharding_key, status)");
                }
                shardingKeyColumns.close();
                
                // 检查并添加 lock_until 列
                ResultSet columns = metaData.getColumns(catalog, null, tableName, "lock_until");
                if (!columns.next()) {
                    // lock_until 列不存在，添加它
                    String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN lock_until DATETIME COMMENT '锁过期时间（用于抢占式 claim）'";
                    stmt.executeUpdate(alterSql);
                    log.info("  ✓ {} 表添加 lock_until 列完成（数据库 jiaoyi_order_{}）", tableName, dbIndex);
                }
                columns.close();
            } catch (Exception e) {
                log.warn("检查/添加 lock_until 列时出错（数据库 jiaoyi_order_{}, 表 {}）: {}", dbIndex, tableName, e.getMessage());
                // 不抛出异常，因为表可能已经存在且已有该列
            }
            
            // 添加优化索引（用于两段式 claim，降低扫表成本）
            // 索引覆盖：shard_id, status, next_retry_time, lock_until, id
            // 用于 SELECT id ... FOR UPDATE SKIP LOCKED 查询
            try {
                // 检查索引是否存在
                ResultSet indexRs = stmt.executeQuery("SHOW INDEX FROM " + tableName + " WHERE Key_name = 'idx_claim'");
                if (!indexRs.next()) {
                    // 索引不存在，创建它
                    String createIndexSql = "CREATE INDEX idx_claim ON " + tableName + 
                            "(shard_id, status, next_retry_time, lock_until, id)";
                    stmt.executeUpdate(createIndexSql);
                    log.info("  ✓ {} 表添加 idx_claim 索引完成（数据库 jiaoyi_order_{}）", tableName, dbIndex);
                }
                indexRs.close();
            } catch (Exception e) {
                if (!e.getMessage().contains("Duplicate key name") && 
                    !e.getMessage().contains("already exists")) {
                    log.warn("检查/添加索引时出错（数据库 jiaoyi_order_{}, 表 {}）: {}", dbIndex, tableName, e.getMessage());
                }
            }
        }
        
        log.info("  ✓ outbox 表创建完成（数据库 jiaoyi_order_{}，共32张表：outbox_00..outbox_31）", dbIndex);
    }
    
    private void createDoorDashWebhookLogTable(Connection conn, DatabaseMetaData metaData) {
        try (Statement stmt = conn.createStatement()) {
            // 检查表是否存在
            ResultSet rs = metaData.getTables(null, null, "doordash_webhook_log", null);
            if (rs.next()) {
                rs.close();
                log.info("doordash_webhook_log表已存在，跳过创建");
                return;
            }
            rs.close();
            
            String createTableSql = "CREATE TABLE doordash_webhook_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID', " +
                    "event_id VARCHAR(100) NOT NULL UNIQUE COMMENT '事件ID（DoorDash 返回的唯一事件ID，用于去重）', " +
                    "order_id BIGINT COMMENT '订单ID', " +
                    "delivery_id VARCHAR(100) COMMENT '配送ID（delivery_id）', " +
                    "external_delivery_id VARCHAR(100) COMMENT '外部订单ID（external_delivery_id）', " +
                    "event_type VARCHAR(50) NOT NULL COMMENT '事件类型：delivery.created, delivery.assigned, delivery.picked_up, delivery.delivered, delivery.cancelled, delivery.failed', " +
                    "payload TEXT COMMENT 'Webhook 数据（JSON，存储完整的 webhook payload）', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '处理状态：SUCCESS, FAILED, PROCESSING', " +
                    "result TEXT COMMENT '处理结果（JSON，存储处理结果）', " +
                    "error_message TEXT COMMENT '错误信息（如果处理失败）', " +
                    "retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数', " +
                    "processed_at DATETIME COMMENT '处理时间', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "INDEX idx_order_id (order_id), " +
                    "INDEX idx_delivery_id (delivery_id), " +
                    "INDEX idx_external_delivery_id (external_delivery_id), " +
                    "INDEX idx_event_type (event_type), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DoorDash Webhook 事件日志表（用于幂等性去重）'";
            
            stmt.executeUpdate(createTableSql);
            log.info("✓ doordash_webhook_log 表创建成功");
        } catch (Exception e) {
            log.error("创建 doordash_webhook_log 表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建商户配置表（在基础库 jiaoyi 中，单表）
     * 注意：商户配置表已从分片表迁移到基础库单表
     */
    private void createMerchantConfigTables(Connection conn, DatabaseMetaData metaData) {
        try (Statement stmt = conn.createStatement()) {
            // 创建 merchant_stripe_config 单表
            ResultSet rs = metaData.getTables(null, null, "merchant_stripe_config", null);
            if (!rs.next()) {
                String createTableSql = "CREATE TABLE merchant_stripe_config (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                        "merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID', " +
                        "stripe_account_id VARCHAR(100) COMMENT 'Stripe Connected Account ID（acct_xxx）', " +
                        "enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 Stripe Connect', " +
                        "currency VARCHAR(10) DEFAULT 'USD' COMMENT '货币代码（USD, CAD 等）', " +
                        "application_fee_percentage DECIMAL(5,2) DEFAULT 2.50 COMMENT '平台手续费率（百分比）', " +
                        "application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '平台固定手续费（元）', " +
                        "amex_application_fee_percentage DECIMAL(5,2) DEFAULT 3.50 COMMENT '美国运通手续费率（百分比）', " +
                        "amex_application_fee_fixed DECIMAL(10,2) DEFAULT 0.30 COMMENT '美国运通固定手续费（元）', " +
                        "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                        "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                        "INDEX idx_stripe_account_id (stripe_account_id), " +
                        "INDEX idx_enabled (enabled)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户Stripe配置表（单表）'";
                stmt.executeUpdate(createTableSql);
                log.info("✓ merchant_stripe_config 表创建成功（基础库单表）");
            } else {
                rs.close();
                log.info("merchant_stripe_config表已存在，跳过创建");
            }
            rs.close();
            
            // 创建 merchant_fee_config 单表
            rs = metaData.getTables(null, null, "merchant_fee_config", null);
            if (!rs.next()) {
                String createTableSql = "CREATE TABLE merchant_fee_config (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                        "merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID', " +
                        "delivery_fee_type VARCHAR(20) DEFAULT 'FLAT_RATE' COMMENT '配送费类型：FLAT_RATE-固定费率，VARIABLE_RATE-按距离可变费率，ZONE_RATE-按邮编区域费率', " +
                        "delivery_fee_fixed DECIMAL(10,2) DEFAULT 5.00 COMMENT '配送费固定金额（元，FLAT_RATE 时使用）', " +
                        "delivery_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '配送费百分比（已废弃，保留兼容）', " +
                        "delivery_fee_min DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最低金额（元）', " +
                        "delivery_fee_max DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费最高金额（元）', " +
                        "delivery_fee_free_threshold DECIMAL(10,2) DEFAULT 0.00 COMMENT '免配送费门槛（订单金额达到此金额免配送费，元）', " +
                        "delivery_variable_rate TEXT COMMENT '按距离的可变费率（JSON格式，VARIABLE_RATE 时使用）', " +
                        "delivery_zone_rate TEXT COMMENT '按邮编区域的费率（JSON格式，ZONE_RATE 时使用）', " +
                        "delivery_maximum_distance DECIMAL(10,2) DEFAULT 0.00 COMMENT '最大配送距离（英里，mile）', " +
                        "merchant_latitude DECIMAL(10,7) COMMENT '商户纬度（用于计算配送距离）', " +
                        "merchant_longitude DECIMAL(10,7) COMMENT '商户经度（用于计算配送距离）', " +
                        "delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）', " +
                        "tax_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '税率（百分比，如 8.0 表示 8%）', " +
                        "tax_exempt TINYINT(1) DEFAULT 0 COMMENT '是否免税：1-免税，0-不免税', " +
                        "online_service_fee_type VARCHAR(20) DEFAULT 'NONE' COMMENT '在线服务费类型：FIXED-固定费用，PERCENTAGE-百分比，NONE-无', " +
                        "online_service_fee_fixed DECIMAL(10,2) DEFAULT 0.00 COMMENT '在线服务费固定金额（元）', " +
                        "online_service_fee_percentage DECIMAL(5,2) DEFAULT 0.00 COMMENT '在线服务费百分比', " +
                        "online_service_fee_strategy TEXT COMMENT '在线服务费策略（JSON格式，存储阶梯费率配置）', " +
                        "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）', " +
                        "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                        "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                        "INDEX idx_delivery_fee_type (delivery_fee_type)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户费用配置表（单表）'";
                stmt.executeUpdate(createTableSql);
                log.info("✓ merchant_fee_config 表创建成功（基础库单表）");
            } else {
                rs.close();
                log.info("merchant_fee_config表已存在，跳过创建");
            }
            rs.close();
            
            // 创建 merchant_capability_config 单表
            rs = metaData.getTables(null, null, "merchant_capability_config", null);
            if (!rs.next()) {
                String createTableSql = "CREATE TABLE merchant_capability_config (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID', " +
                        "merchant_id VARCHAR(50) NOT NULL UNIQUE COMMENT '商户ID', " +
                        "enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流：1-启用，0-禁用', " +
                        "qty_of_orders INT NOT NULL DEFAULT 10 COMMENT '订单数量阈值（触发限流的订单数）', " +
                        "time_interval INT NOT NULL DEFAULT 10 COMMENT '时间窗口（分钟）', " +
                        "closing_duration INT NOT NULL DEFAULT 30 COMMENT '关闭持续时间（分钟）', " +
                        "next_open_at BIGINT DEFAULT NULL COMMENT '下次开放时间（时间戳，毫秒）', " +
                        "re_open_all_at BIGINT DEFAULT NULL COMMENT '重新开放所有服务的时间（时间戳，毫秒）', " +
                        "operate_pick_up VARCHAR(10) DEFAULT 'manual' COMMENT 'Pickup 服务操作类型：manual-手动，system-系统自动', " +
                        "operate_delivery VARCHAR(10) DEFAULT 'manual' COMMENT 'Delivery 服务操作类型：manual-手动，system-系统自动', " +
                        "operate_togo VARCHAR(10) DEFAULT 'manual' COMMENT 'Togo 服务操作类型：manual-手动，system-系统自动', " +
                        "operate_self_dine_in VARCHAR(10) DEFAULT 'manual' COMMENT 'SelfDineIn 服务操作类型：manual-手动，system-系统自动', " +
                        "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（用于乐观锁）', " +
                        "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                        "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                        "INDEX idx_enable (enable), " +
                        "INDEX idx_next_open_at (next_open_at)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户高峰拒单配置表（单表）'";
                stmt.executeUpdate(createTableSql);
                log.info("✓ merchant_capability_config 表创建成功（基础库单表）");
            } else {
                rs.close();
                log.info("merchant_capability_config表已存在，跳过创建");
            }
            rs.close();
        } catch (Exception e) {
            log.error("创建商户配置表失败: {}", e.getMessage(), e);
        }
    }
}
