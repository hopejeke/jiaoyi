package com.jiaoyi.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox节点实体
 * 用于节点注册和分片分配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxNode {
    
    /**
     * 主键ID（自增）
     */
    private Long id;
    
    /**
     * 节点IP地址
     */
    private String ip;
    
    /**
     * 节点端口
     */
    private Integer port;
    
    /**
     * 节点唯一标识（IP:PORT）
     */
    private String nodeId;
    
    /**
     * 是否启用：1-启用，0-禁用
     */
    private Integer enabled;
    
    /**
     * 心跳过期时间
     */
    private LocalDateTime expiredTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

