package com.jiaoyi.order.service;

import com.jiaoyi.order.entity.ConsumerLog;
import com.jiaoyi.order.mapper.ConsumerLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * MQ 消费日志服务
 * 用于消费幂等，确保同一消息只处理一次
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerLogService {
    
    private final ConsumerLogMapper consumerLogMapper;
    
    /**
     * 尝试插入消费记录（幂等：如果 consumer_group + message_key 已存在则返回 false）
     * 
     * @param consumerGroup 消费者组
     * @param topic Topic
     * @param tag Tag
     * @param messageKey 消息Key（eventId 或 idempotencyKey）
     * @param messageId RocketMQ MessageId
     * @return true 如果插入成功（首次消费），false 如果已存在（重复消息）
     */
    @Transactional
    public boolean tryInsert(String consumerGroup, String topic, String tag, 
                             String messageKey, String messageId) {
        try {
            ConsumerLog consumerLog = ConsumerLog.builder()
                    .consumerGroup(consumerGroup)
                    .topic(topic)
                    .tag(tag)
                    .messageKey(messageKey)
                    .messageId(messageId)
                    .status(ConsumerLog.ConsumerStatus.PROCESSING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            int inserted = consumerLogMapper.tryInsert(consumerLog);
            
            if (inserted > 0) {
                log.debug("消息首次消费，consumerGroup: {}, messageKey: {}", consumerGroup, messageKey);
                return true;
            } else {
                log.info("重复消息，已存在，consumerGroup: {}, messageKey: {}", consumerGroup, messageKey);
                return false;
            }
        } catch (Exception e) {
            // 如果是唯一键冲突，说明是重复消息
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.info("重复消息（唯一键冲突），consumerGroup: {}, messageKey: {}", consumerGroup, messageKey);
                return false;
            }
            log.error("插入消费日志失败，consumerGroup: {}, messageKey: {}", consumerGroup, messageKey, e);
            throw new RuntimeException("插入消费日志失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 更新消费状态为成功
     */
    @Transactional
    public void markSuccess(String consumerGroup, String messageKey) {
        consumerLogMapper.updateStatusToSuccess(consumerGroup, messageKey, LocalDateTime.now());
        log.debug("消息消费标记为成功，consumerGroup: {}, messageKey: {}", consumerGroup, messageKey);
    }
    
    /**
     * 更新消费状态为失败
     */
    @Transactional
    public void markFailed(String consumerGroup, String messageKey, String errorMessage) {
        // 先增加重试次数
        consumerLogMapper.incrementRetryCount(consumerGroup, messageKey);
        
        // 查询当前重试次数
        ConsumerLog consumerLog = consumerLogMapper.selectByConsumerGroupAndMessageKey(consumerGroup, messageKey);
        int retryCount = consumerLog != null && consumerLog.getRetryCount() != null ? consumerLog.getRetryCount() : 1;
        
        // 更新状态为失败
        consumerLogMapper.updateStatusToFailed(
                consumerGroup, 
                messageKey, 
                errorMessage, 
                retryCount, 
                LocalDateTime.now()
        );
        log.warn("消息消费标记为失败，consumerGroup: {}, messageKey: {}, retryCount: {}, error: {}", 
                consumerGroup, messageKey, retryCount, errorMessage);
    }
}

