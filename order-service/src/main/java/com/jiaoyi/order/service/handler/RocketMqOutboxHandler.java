package com.jiaoyi.order.service.handler;

import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消息发送 Handler
 * 处理以 _MQ 结尾的任务类型
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RocketMqOutboxHandler implements OutboxHandler {
    
    private final RocketMQTemplate rocketMQTemplate;
    
    @Override
    public boolean supports(String type) {
        return type != null && type.endsWith("_MQ");
    }
    
    @Override
    public void handle(Outbox outbox) throws Exception {
        if (outbox.getTopic() == null || outbox.getTag() == null) {
            throw new IllegalArgumentException("MQ 任务必须指定 topic 和 tag，type: " + outbox.getType());
        }
        
        String destination = outbox.getTopic() + ":" + outbox.getTag();
        String messageKey = outbox.getMessageKey() != null ? outbox.getMessageKey() : outbox.getBizKey();
        
        org.springframework.messaging.Message<String> message = MessageBuilder
                .withPayload(outbox.getPayload())
                .setHeader("KEYS", messageKey)
                .build();
        
        rocketMQTemplate.syncSend(destination, message);
        
        log.info("Outbox MQ 消息发送成功，id: {}, type: {}, topic: {}, tag: {}, messageKey: {}", 
                outbox.getId(), outbox.getType(), outbox.getTopic(), outbox.getTag(), messageKey);
    }
}

