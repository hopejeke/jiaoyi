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
        
        // 构建请求
        ProductServiceClient.DeductStockBatchRequest request = new ProductServiceClient.DeductStockBatchRequest();
        request.setOrderId(command.getOrderId());
        request.setProductIds(command.getProductIds());
        request.setSkuIds(command.getSkuIds());
        request.setQuantities(command.getQuantities());
        
        // 幂等键：使用 orderId + "-DEDUCT"（或使用 command 中的 idempotencyKey）
        String idempotencyKey = command.getIdempotencyKey() != null 
                ? command.getIdempotencyKey() 
                : command.getOrderId() + "-DEDUCT";
        request.setIdempotencyKey(idempotencyKey);
        
        // 调用库存服务（幂等性由 product-service 保证）
        productServiceClient.deductStockBatch(request);
        
        log.info("Outbox HTTP 库存扣减成功，id: {}, orderId: {}, idempotencyKey: {}", 
                outbox.getId(), command.getOrderId(), 
                command.getIdempotencyKey() != null ? command.getIdempotencyKey() : command.getOrderId() + "-DEDUCT");
    }
}

