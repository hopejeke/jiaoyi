package com.jiaoyi.mapper;

import com.jiaoyi.entity.Outbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox表Mapper
 */
@Mapper
public interface OutboxMapper {
    
    /**
     * 插入outbox记录
     */
    int insert(Outbox outbox);
    
    /**
     * 根据ID查询
     */
    Outbox selectById(@Param("id") Long id);
    
    /**
     * 查询待发送的消息（状态为PENDING）
     * 
     * @param limit 查询数量限制
     * @return 待发送的消息列表
     */
    List<Outbox> selectPendingMessages(@Param("limit") int limit);
    
    /**
     * 更新outbox状态为已发送
     */
    int updateStatusToSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);
    
    /**
     * 更新outbox状态为失败，并记录错误信息
     */
    int updateStatusToFailed(@Param("id") Long id, 
                             @Param("errorMessage") String errorMessage, 
                             @Param("retryCount") Integer retryCount);
    
    /**
     * 增加重试次数
     */
    int incrementRetryCount(@Param("id") Long id);
}

