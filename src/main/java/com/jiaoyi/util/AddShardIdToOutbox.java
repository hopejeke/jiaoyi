package com.jiaoyi.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 为outbox表添加shard_id列的工具类
 * 执行方式：直接运行main方法
 */
public class AddShardIdToOutbox {
    
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
            
            System.out.println("开始为outbox表添加shard_id列...");
            
            // 1. 添加shard_id列（允许NULL，后续更新）
            System.out.println("1. 添加shard_id列...");
            stmt.executeUpdate("ALTER TABLE outbox ADD COLUMN shard_id INT NULL COMMENT '分片ID（用于分片处理）' AFTER id");
            System.out.println("   ✓ shard_id列已添加");
            
            // 2. 为现有数据计算并设置shard_id（假设shardCount=10）
            System.out.println("2. 为现有数据计算shard_id...");
            int updateCount = stmt.executeUpdate("UPDATE outbox SET shard_id = (id % 10) WHERE shard_id IS NULL");
            System.out.println("   ✓ 已更新 " + updateCount + " 条记录的shard_id");
            
            // 3. 将shard_id设置为NOT NULL
            System.out.println("3. 将shard_id设置为NOT NULL...");
            stmt.executeUpdate("ALTER TABLE outbox MODIFY COLUMN shard_id INT NOT NULL COMMENT '分片ID（用于分片处理）'");
            System.out.println("   ✓ shard_id已设置为NOT NULL");
            
            // 4. 添加索引
            System.out.println("4. 添加索引...");
            try {
                stmt.executeUpdate("ALTER TABLE outbox ADD INDEX idx_shard_id (shard_id)");
                System.out.println("   ✓ idx_shard_id索引已添加");
            } catch (Exception e) {
                System.out.println("   ⚠ idx_shard_id索引可能已存在: " + e.getMessage());
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE outbox ADD INDEX idx_shard_id_status (shard_id, status)");
                System.out.println("   ✓ idx_shard_id_status索引已添加");
            } catch (Exception e) {
                System.out.println("   ⚠ idx_shard_id_status索引可能已存在: " + e.getMessage());
            }
            
            // 关闭连接
            stmt.close();
            conn.close();
            
            System.out.println("\n========== 完成 ==========");
            System.out.println("outbox表已成功添加shard_id列和相关索引！");
            
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

