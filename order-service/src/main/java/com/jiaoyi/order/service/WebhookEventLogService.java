package com.jiaoyi.order.service;

import com.jiaoyi.order.entity.WebhookEventLog;
import com.jiaoyi.order.mapper.WebhookEventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Webhook 事件日志服务
 * 用于事件幂等，确保同一事件只处理一次
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookEventLogService {
    
    private final WebhookEventLogMapper webhookEventLogMapper;
    
    /**
     * 尝试插入事件（幂等：如果 eventId 已存在则返回 false）
     * 
     * @param eventId 事件ID
     * @param eventType 事件类型
     * @param paymentIntentId Payment Intent ID（可选）
     * @param thirdPartyTradeNo 第三方交易号（可选）
     * @param orderId 订单ID（可选）
     * @return true 如果插入成功（首次接收），false 如果已存在（重复事件）
     */
    @Transactional
    public boolean tryInsert(String eventId, String eventType, String paymentIntentId, 
                             String thirdPartyTradeNo, Long orderId) {
        try {
            WebhookEventLog eventLog = WebhookEventLog.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .paymentIntentId(paymentIntentId)
                    .thirdPartyTradeNo(thirdPartyTradeNo)
                    .orderId(orderId)
                    .status(WebhookEventLog.EventStatus.RECEIVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            int inserted = webhookEventLogMapper.tryInsert(eventLog);
            
            if (inserted > 0) {
                log.info("事件首次接收，eventId: {}, eventType: {}", eventId, eventType);
                return true;
            } else {
                log.info("重复事件，已存在，eventId: {}, eventType: {}", eventId, eventType);
                return false;
            }
        } catch (Exception e) {
            // 如果是唯一键冲突，说明是重复事件
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.info("重复事件（唯一键冲突），eventId: {}", eventId);
                return false;
            }
            log.error("插入事件日志失败，eventId: {}", eventId, e);
            throw new RuntimeException("插入事件日志失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 更新事件状态为已处理
     */
    @Transactional
    public void markProcessed(String eventId) {
        webhookEventLogMapper.updateStatusToProcessed(eventId, LocalDateTime.now());
        log.debug("事件标记为已处理，eventId: {}", eventId);
    }
    
    /**
     * 更新事件状态为失败
     */
    @Transactional
    public void markFailed(String eventId, String errorMessage) {
        webhookEventLogMapper.updateStatusToFailed(eventId, errorMessage, LocalDateTime.now());
        log.warn("事件标记为失败，eventId: {}, error: {}", eventId, errorMessage);
    }
    
    /**
     * 检查事件是否已处理（PROCESSED 状态）
     * 
     * @param eventId 事件ID
     * @return true 如果事件已处理，false 如果未处理或不存在
     */
    public boolean isProcessed(String eventId) {
        WebhookEventLog eventLog = webhookEventLogMapper.selectByEventId(eventId);
        if (eventLog == null) {
            return false;
        }
        // 检查状态是否为已处理（PROCESSED）
        return eventLog.getStatus() == WebhookEventLog.EventStatus.PROCESSED;
    }
}

