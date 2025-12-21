package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.DoorDashWebhookLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * DoorDash Webhook 事件日志 Mapper
 */
@Mapper
public interface DoorDashWebhookLogMapper {
    
    /**
     * 插入 Webhook 事件日志
     */
    int insert(DoorDashWebhookLog log);
    
    /**
     * 根据事件ID查询（用于幂等性检查）
     */
    DoorDashWebhookLog selectByEventId(@Param("eventId") String eventId);
    
    /**
     * 更新处理状态
     */
    int updateStatus(@Param("id") Long id, 
                     @Param("status") com.jiaoyi.order.enums.DoorDashWebhookLogStatusEnum status, 
                     @Param("result") String result, 
                     @Param("errorMessage") String errorMessage,
                     @Param("retryCount") Integer retryCount);
}

