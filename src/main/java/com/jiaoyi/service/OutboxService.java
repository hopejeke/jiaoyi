package com.jiaoyi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.entity.Outbox;
import com.jiaoyi.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox服务
 * 负责写入outbox表和发送消息到RocketMQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    
    private final OutboxMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;
    
    /**
     * 每次扫描的消息数量
     */
    private static final int BATCH_SIZE = 50;
    
    /**
     * 写入outbox表（在本地事务中调用）
     * 
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param messageKey 消息Key（可选）
     * @param message 消息对象（会被序列化为JSON）
     * @return Outbox记录
     */
    @Transactional
    public Outbox saveMessage(String topic, String tag, String messageKey, Object message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            
            Outbox outbox = Outbox.builder()
                    .topic(topic)
                    .tag(tag)
                    .messageKey(messageKey)
                    .messageBody(messageBody)
                    .status(Outbox.OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            outboxMapper.insert(outbox);
            log.info("已写入outbox表，ID: {}, Topic: {}, Tag: {}", outbox.getId(), topic, tag);
            
            return outbox;
        } catch (Exception e) {
            log.error("写入outbox表失败，Topic: {}, Tag: {}", topic, tag, e);
            throw new RuntimeException("写入outbox表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 定时任务：扫描outbox表并发送消息到RocketMQ
     * 每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    public void processOutboxMessages() {
        try {
            if (rocketMQTemplate == null) {
                log.warn("RocketMQ不可用，跳过处理outbox消息");
                return;
            }
            
            // 查询待发送的消息
            List<Outbox> pendingMessages = outboxMapper.selectPendingMessages(BATCH_SIZE);
            
            if (pendingMessages.isEmpty()) {
                return;
            }
            
            log.info("扫描到 {} 条待发送的outbox消息", pendingMessages.size());
            
            for (Outbox outbox : pendingMessages) {
                try {
                    // 发送消息到RocketMQ
                    sendMessageToRocketMQ(outbox);
                    
                    // 更新状态为已发送
                    outboxMapper.updateStatusToSent(outbox.getId(), LocalDateTime.now());
                    log.info("Outbox消息发送成功，ID: {}, Topic: {}, Tag: {}", 
                            outbox.getId(), outbox.getTopic(), outbox.getTag());
                    
                } catch (Exception e) {
                    log.error("Outbox消息发送失败，ID: {}, Topic: {}, Tag: {}", 
                            outbox.getId(), outbox.getTopic(), outbox.getTag(), e);
                    
                    // 增加重试次数
                    outboxMapper.incrementRetryCount(outbox.getId());
                    
                    // 获取更新后的outbox记录
                    Outbox updatedOutbox = outboxMapper.selectById(outbox.getId());
                    
                    // 如果超过最大重试次数，标记为失败
                    if (updatedOutbox != null && updatedOutbox.getRetryCount() >= MAX_RETRY_COUNT) {
                        outboxMapper.updateStatusToFailed(
                                outbox.getId(), 
                                "发送失败，已重试" + updatedOutbox.getRetryCount() + "次: " + e.getMessage(),
                                updatedOutbox.getRetryCount()
                        );
                        log.error("Outbox消息发送失败次数超过最大重试次数，标记为失败，ID: {}", outbox.getId());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("处理outbox消息时发生异常", e);
        }
    }
    
    /**
     * 发送消息到RocketMQ
     */
    private void sendMessageToRocketMQ(Outbox outbox) throws Exception {
        // 构建目标地址
        String destination = outbox.getTopic() + ":" + outbox.getTag();
        
        // 构建消息
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(outbox.getMessageBody());
        
        // 如果有messageKey，设置到消息的KEYS header
        if (outbox.getMessageKey() != null && !outbox.getMessageKey().isEmpty()) {
            messageBuilder.setHeader("KEYS", outbox.getMessageKey());
        }
        
        Message<String> springMessage = messageBuilder.build();
        
        // 发送消息
        rocketMQTemplate.syncSend(destination, springMessage);
        
        log.debug("已发送消息到RocketMQ，Topic: {}, Tag: {}, MessageKey: {}", 
                outbox.getTopic(), outbox.getTag(), outbox.getMessageKey());
    }
    
    /**
     * 手动触发处理outbox消息（用于测试或紧急情况）
     */
    public void processOutboxMessagesManually() {
        processOutboxMessages();
    }
}

