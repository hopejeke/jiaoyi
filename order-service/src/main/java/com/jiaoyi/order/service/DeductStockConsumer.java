package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.config.RocketMQConfig;
import com.jiaoyi.order.dto.DeductStockCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * 库存扣减消息消费者
 * 处理库存扣减命令：调用 product-service 扣减库存
 * 
 * 【幂等性】：使用 idempotencyKey（orderId + paymentIntentId）作为幂等键
 * 【注意】：product-service 的 deductStockBatch 接口必须支持幂等性
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
    topic = RocketMQConfig.DEDUCT_STOCK_TOPIC,
    consumerGroup = RocketMQConfig.DEDUCT_STOCK_CONSUMER_GROUP,
    selectorExpression = RocketMQConfig.DEDUCT_STOCK_TAG
)
public class DeductStockConsumer implements RocketMQListener<MessageExt> {
    
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productServiceClient;
    private final ConsumerLogService consumerLogService;
    
    @Override
    public void onMessage(MessageExt messageExt) {
        String messageId = messageExt.getMsgId();
        String messageKey = messageExt.getKeys(); // idempotencyKey
        
        log.info("收到库存扣减命令，messageId: {}, messageKey: {}", messageId, messageKey);
        
        try {
            // 1. 解析消息体
            DeductStockCommand command;
            try {
                String messageBody = new String(messageExt.getBody(), "UTF-8");
                command = objectMapper.readValue(messageBody, DeductStockCommand.class);
                log.info("解析库存扣减命令成功，orderId: {}, idempotencyKey: {}, 商品数量: {}", 
                        command.getOrderId(), command.getIdempotencyKey(), 
                        command.getProductIds() != null ? command.getProductIds().size() : 0);
            } catch (Exception e) {
                log.error("解析库存扣减消息失败，messageId: {}", messageId, e);
                return; // 解析失败，不重试（消息格式错误）
            }
            
            Long orderId = command.getOrderId();
            if (orderId == null) {
                log.error("库存扣减命令缺少 orderId，messageId: {}", messageId);
                return;
            }
            
            String idempotencyKey = command.getIdempotencyKey();
            if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                log.error("库存扣减命令缺少 idempotencyKey，orderId: {}, messageId: {}", orderId, messageId);
                return;
            }
            
            // 2. 【P0 消费幂等】：尝试插入消费记录（consumer_group + message_key 唯一约束）
            // 使用 idempotencyKey 作为 messageKey（orderId + "-DEDUCT"）
            boolean isFirstConsume = consumerLogService.tryInsert(
                    RocketMQConfig.DEDUCT_STOCK_CONSUMER_GROUP,
                    RocketMQConfig.DEDUCT_STOCK_TOPIC,
                    RocketMQConfig.DEDUCT_STOCK_TAG,
                    idempotencyKey, // messageKey 使用 idempotencyKey
                    messageId
            );
            
            if (!isFirstConsume) {
                log.info("重复消息，已消费过，直接返回（幂等），idempotencyKey: {}, messageId: {}", idempotencyKey, messageId);
                return; // 重复消息，直接返回（幂等成功）
            }
            
            // 3. 调用 product-service 扣减库存（幂等性由 product-service 保证，使用 idempotencyKey）
            ProductServiceClient.DeductStockBatchRequest deductRequest = new ProductServiceClient.DeductStockBatchRequest();
            deductRequest.setProductIds(command.getProductIds());
            deductRequest.setSkuIds(command.getSkuIds());
            deductRequest.setQuantities(command.getQuantities());
            deductRequest.setOrderId(orderId);
            
            try {
                productServiceClient.deductStockBatch(deductRequest);
                log.info("库存扣减成功，orderId: {}, idempotencyKey: {}, 商品数量: {}", 
                        orderId, idempotencyKey, 
                        command.getProductIds() != null ? command.getProductIds().size() : 0);
                
                // 标记消费为成功
                consumerLogService.markSuccess(
                        RocketMQConfig.DEDUCT_STOCK_CONSUMER_GROUP, 
                        idempotencyKey
                );
                
            } catch (Exception e) {
                log.error("调用 product-service 扣减库存失败，orderId: {}, idempotencyKey: {}", 
                        orderId, idempotencyKey, e);
                // 标记消费为失败
                consumerLogService.markFailed(
                        RocketMQConfig.DEDUCT_STOCK_CONSUMER_GROUP, 
                        idempotencyKey, 
                        "扣减库存失败: " + e.getMessage()
                );
                // 抛出异常，让 RocketMQ 重试
                throw new RuntimeException("扣减库存失败: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            log.error("处理库存扣减消息异常，messageId: {}, messageKey: {}", messageId, messageKey, e);
            // 标记消费为失败（如果 idempotencyKey 存在）
            if (messageKey != null) {
                try {
                    consumerLogService.markFailed(
                            RocketMQConfig.DEDUCT_STOCK_CONSUMER_GROUP, 
                            messageKey, 
                            "处理异常: " + e.getMessage()
                    );
                } catch (Exception ex) {
                    log.error("标记消费失败异常", ex);
                }
            }
            // 抛出异常，让 RocketMQ 重试
            throw new RuntimeException("处理库存扣减消息失败", e);
        }
    }
}

