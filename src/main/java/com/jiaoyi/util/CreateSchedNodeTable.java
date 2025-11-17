package com.jiaoyi.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 创建sched_node表的工具类
 * 执行方式：直接运行main方法
 */
public class CreateSchedNodeTable {
    
    private static final String URL = "jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    
    public static void main(String[] args) {
        try {
            // 加载驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // 连接数据库
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();
            
            System.out.println("开始创建sched_node表...");
            
            // 检查表是否已存在
            try {
                stmt.executeQuery("SELECT 1 FROM sched_node LIMIT 1");
                System.out.println("⚠ sched_node表已存在，跳过创建");
                stmt.close();
                conn.close();
                return;
            } catch (Exception e) {
                // 表不存在，继续创建
            }
            
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
            
            stmt.executeUpdate(createTableSql);
            System.out.println("✓ sched_node表创建成功！");
            
            // 关闭连接
            stmt.close();
            conn.close();
            
            System.out.println("\n========== 完成 ==========");
            System.out.println("sched_node表已成功创建！");
            
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

