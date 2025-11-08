package com.jiaoyi.util;

import java.sql.*;

/**
 * 添加version字段的工具类
 */
public class AddVersionField {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    
    public static void main(String[] args) {
        System.out.println("开始连接数据库并添加version字段...");
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("✅ 数据库连接成功");
                
                // 检查字段是否已存在
                String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = 'jiaoyi' " +
                        "AND TABLE_NAME = 'store_products' " +
                        "AND COLUMN_NAME = 'version'";
                
                boolean fieldExists = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(checkSql)) {
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        fieldExists = true;
                        System.out.println("ℹ️  字段 version 已存在，跳过添加");
                    }
                }
                
                if (!fieldExists) {
                    // 添加字段
                    String alterSql = "ALTER TABLE store_products " +
                            "ADD COLUMN version BIGINT NOT NULL DEFAULT 0 " +
                            "COMMENT '版本号（用于缓存一致性控制）'";
                    
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(alterSql);
                        System.out.println("✅ 成功添加 version 字段");
                    }
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


