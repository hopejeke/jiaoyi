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
            // 3. 创建支付回调日志表和 Webhook 日志表（在 jiaoyi 基础数据库中）
            String dbUrl = buildDatabaseUrl(actualJdbcUrl, "jiaoyi");
            log.info("连接基础数据库URL: {}", dbUrl);
            try (Connection conn = DriverManager.getConnection(dbUrl, actualUsername, actualPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();
                createOutboxNodeTable(conn, metaData);
                createOutboxTable(conn, metaData);
                createPaymentCallbackLogTable(conn, metaData);
                createDoorDashWebhookLogTable(conn, metaData);
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
            
            // 创建商户 Stripe 配置表分片
            createMerchantStripeConfigTables(stmt, dbIndex);
            
            // 创建商户费用配置表分片
            createMerchantFeeConfigTables(stmt, dbIndex);
            
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
                    "id BIGINT NOT NULL PRIMARY KEY COMMENT '订单ID（雪花算法生成）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（分片键）', " +
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
                    "delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID', " +
                    "delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用', " +
                    "delivery_fee_quoted_at DATETIME COMMENT 'DoorDash报价时间（用于检查报价是否过期）', " +
                    "delivery_fee_quote_id VARCHAR(100) COMMENT 'DoorDash报价ID（quote_id，用于接受报价）', " +
                    "delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费', " +
                    "delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用', " +
                    "delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）', " +
                    "additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）', " +
                    "version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）', " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', " +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', " +
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_user_id (user_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单表分片创建完成（3个分片表）");
    }
    
    /**
     * 更新订单表结构（如果表已存在，添加缺失的列，如 DoorDash 相关字段）
     * 用于从旧表结构迁移到新表结构
     */
    private void updateOrdersTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "orders_" + tableIndex;
            try {
                // 检查表是否存在
                java.sql.ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE '" + tableName + "'");
                if (!rs.next()) {
                    // 表不存在，跳过更新
                    rs.close();
                    continue;
                }
                rs.close();
                
                // 添加 DoorDash 相关字段（如果不存在）
                String[] alterStatements = {
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_quoted_at DATETIME COMMENT 'DoorDash报价时间（用于检查报价是否过期）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_quote_id VARCHAR(100) COMMENT 'DoorDash报价ID（quote_id，用于接受报价）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用'",
                    "ALTER TABLE " + tableName + " ADD COLUMN delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）'",
                    "ALTER TABLE " + tableName + " ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）'"
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
                try {
                    stmt.executeUpdate("ALTER TABLE " + tableName + " ADD INDEX idx_delivery_id (delivery_id)");
                } catch (java.sql.SQLException e) {
                    // 索引已存在，忽略
                    if (!e.getMessage().contains("Duplicate key name") && 
                        !e.getMessage().contains("already exists")) {
                        log.debug("为表 {} 添加索引时出错（可忽略）: {}", tableName, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.warn("更新表 {} 结构失败: {}", tableName, e.getMessage());
            }
        }
        log.info("  ✓ 订单表结构更新完成（3个分片表）");
    }
    
    private void createOrderItemsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "order_items_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT NOT NULL PRIMARY KEY COMMENT '订单项ID（雪花算法生成）', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '餐馆ID（分片键）', " +
                    "product_id BIGINT COMMENT '商品ID（用于库存锁定）', " +
                    "sale_item_id BIGINT COMMENT '销售项ID（POS系统ID）', " +
                    "order_item_id BIGINT COMMENT '订单项ID', " +
                    "item_name VARCHAR(200) NOT NULL COMMENT '商品名称', " +
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
                    "INDEX idx_sale_item_id (sale_item_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 订单项表分片创建完成（3个分片表）");
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
                
            } catch (Exception e) {
                log.warn("更新表 {} 结构时出错: {}", tableName, e.getMessage());
            }
        }
        log.info("  ✓ 订单项表结构更新完成（3个分片表）");
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
    
    private void createPaymentsTables(Statement stmt, int dbIndex) throws Exception {
        for (int tableIndex = 0; tableIndex < 3; tableIndex++) {
            String tableName = "payments_" + tableIndex;
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '支付ID', " +
                    "order_id BIGINT NOT NULL COMMENT '订单ID（关联orders.id）', " +
                    "transaction_id BIGINT COMMENT '交易ID（用于关联交易记录）', " +
                    "merchant_id VARCHAR(50) NOT NULL COMMENT '商户ID（分片键）', " +
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
                    "INDEX idx_merchant_id (merchant_id), " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_type (type), " +
                    "INDEX idx_payment_service (payment_service), " +
                    "INDEX idx_third_party_trade_no (third_party_trade_no), " +
                    "INDEX idx_stripe_payment_intent_id (stripe_payment_intent_id), " +
                    "INDEX idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表_库" + dbIndex + "_分片" + tableIndex + "'";
            stmt.executeUpdate(createTableSql);
        }
        log.info("  ✓ 支付记录表分片创建完成（3个分片表）");
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
     * 创建 DoorDash Webhook 事件日志表（用于幂等性去重）
     * 基于 event_id 去重，记录每次 Webhook 回调
     */
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
}
