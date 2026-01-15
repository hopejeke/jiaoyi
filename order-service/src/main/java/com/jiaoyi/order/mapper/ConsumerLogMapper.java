package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.ConsumerLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * MQ 消费日志 Mapper
 */
@Mapper
public interface ConsumerLogMapper {
    
    /**
     * 尝试插入消费记录（幂等：如果 consumer_group + message_key 已存在则返回 0）
     * 使用 INSERT IGNORE 实现
     */
    int tryInsert(ConsumerLog log);
    
    /**
     * 根据 consumerGroup 和 messageKey 查询
     */
    ConsumerLog selectByConsumerGroupAndMessageKey(
            @Param("consumerGroup") String consumerGroup,
            @Param("messageKey") String messageKey
    );
    
    /**
     * 更新状态为成功
     */
    int updateStatusToSuccess(
            @Param("consumerGroup") String consumerGroup,
            @Param("messageKey") String messageKey,
            @Param("processedAt") LocalDateTime processedAt
    );
    
    /**
     * 更新状态为失败，并记录错误信息和重试次数
     */
    int updateStatusToFailed(
            @Param("consumerGroup") String consumerGroup,
            @Param("messageKey") String messageKey,
            @Param("errorMessage") String errorMessage,
            @Param("retryCount") Integer retryCount,
            @Param("processedAt") LocalDateTime processedAt
    );
    
    /**
     * 增加重试次数
     */
    int incrementRetryCount(
            @Param("consumerGroup") String consumerGroup,
            @Param("messageKey") String messageKey
    );
}



