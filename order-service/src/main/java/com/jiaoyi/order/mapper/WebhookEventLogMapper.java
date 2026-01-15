package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.WebhookEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Webhook 事件日志 Mapper
 */
@Mapper
public interface WebhookEventLogMapper {
    
    /**
     * 尝试插入事件（幂等：如果 eventId 已存在则返回 0）
     * 使用 INSERT IGNORE 或 ON DUPLICATE KEY UPDATE 实现
     */
    int tryInsert(WebhookEventLog log);
    
    /**
     * 根据 eventId 查询
     */
    WebhookEventLog selectByEventId(@Param("eventId") String eventId);
    
    /**
     * 更新状态为已处理
     */
    int updateStatusToProcessed(@Param("eventId") String eventId, @Param("processedAt") LocalDateTime processedAt);
    
    /**
     * 更新状态为失败，并记录错误信息
     */
    int updateStatusToFailed(@Param("eventId") String eventId, 
                             @Param("errorMessage") String errorMessage,
                             @Param("processedAt") LocalDateTime processedAt);
}



