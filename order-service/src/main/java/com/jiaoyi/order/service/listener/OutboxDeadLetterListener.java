package com.jiaoyi.order.service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.dto.DeductStockCommand;
import com.jiaoyi.outbox.event.OutboxDeadLetterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Outbox 死信监听器
 * 当库存扣减任务变成 DEAD 时，记录日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDeadLetterListener {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 监听死信事件，更新订单状态
     */
    @Async
    @EventListener
    public void handleDeadLetter(OutboxDeadLetterEvent event) {
        try {
            com.jiaoyi.outbox.entity.Outbox outbox = event.outbox();
            
            // 只处理库存扣减任务
            if (!"DEDUCT_STOCK_HTTP".equals(outbox.getType())) {
                return;
            }
            
            // 解析 payload 获取 orderId
            DeductStockCommand command = objectMapper.readValue(outbox.getPayload(), DeductStockCommand.class);
            Long orderId = command.getOrderId();
            
            if (orderId == null) {
                log.warn("死信任务缺少 orderId，无法更新订单状态，outboxId: {}", outbox.getId());
                return;
            }
            
            // 记录死信事件（不再更新订单状态）
            log.warn("【订单死信】订单库存扣减任务已变成死信，orderId: {}, outboxId: {}, retryCount: {}, 错误: {}", 
                    orderId, outbox.getId(), event.retryCount(), outbox.getLastError());
            
        } catch (Exception e) {
            log.error("处理死信事件失败，outboxId: {}", event.outbox().getId(), e);
        }
    }
}

