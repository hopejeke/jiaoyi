package com.jiaoyi.util;

import java.sql.*;

/**
 * 创建version触发器
 */
public class CreateVersionTrigger {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    
    public static void main(String[] args) {
        System.out.println("开始连接数据库并创建version触发器...");
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("✅ 数据库连接成功");
                
                // 检查触发器是否已存在
                String checkSql = "SELECT COUNT(*) FROM information_schema.TRIGGERS " +
                        "WHERE TRIGGER_SCHEMA = 'jiaoyi' " +
                        "AND TRIGGER_NAME = 'trg_store_products_before_insert'";
                
                boolean triggerExists = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(checkSql)) {
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        triggerExists = true;
                        System.out.println("ℹ️  触发器已存在，先删除");
                        
                        // 删除现有触发器
                        try (Statement dropStmt = conn.createStatement()) {
                            dropStmt.execute("DROP TRIGGER IF EXISTS trg_store_products_before_insert");
                            System.out.println("✅ 已删除旧触发器");
                        }
                    }
                }
                
                // 创建触发器
                String triggerSql = 
                    "CREATE TRIGGER trg_store_products_before_insert " +
                    "BEFORE INSERT ON store_products " +
                    "FOR EACH ROW " +
                    "BEGIN " +
                    "  IF NEW.version IS NULL OR NEW.version = 0 THEN " +
                    "    SET NEW.version = COALESCE( " +
                    "      (SELECT MAX(version) FROM store_products WHERE store_id = NEW.store_id), " +
                    "      0 " +
                    "    ) + 1; " +
                    "  END IF; " +
                    "END";
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(triggerSql);
                    System.out.println("✅ 成功创建 version 触发器");
                }
                
            }
            
            System.out.println("✅ SQL执行完成");
            
        } catch (Exception e) {
            System.err.println("❌ 执行失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}


