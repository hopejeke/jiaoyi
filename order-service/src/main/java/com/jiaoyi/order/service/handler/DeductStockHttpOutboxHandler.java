package com.jiaoyi.order.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.dto.DeductStockCommand;
import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 库存扣减 HTTP 调用 Handler
 * 处理 DEDUCT_STOCK_HTTP 类型任务，直接调用 product-service 扣减库存
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeductStockHttpOutboxHandler implements OutboxHandler {
    
    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public boolean supports(String type) {
        return "DEDUCT_STOCK_HTTP".equals(type);
    }
    
    @Override
    public void handle(Outbox outbox) throws Exception {
        // 解析 payload
        DeductStockCommand command = objectMapper.readValue(outbox.getPayload(), DeductStockCommand.class);
        
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("库存扣减命令缺少 orderId");
        }
        // 库存已改为创建订单时按渠道扣减，支付成功不再扣减，任务直接视为成功
        log.debug("扣减任务跳过（已改为下单时按渠道扣减），outboxId: {}, orderId: {}", outbox.getId(), command.getOrderId());
    }
}

