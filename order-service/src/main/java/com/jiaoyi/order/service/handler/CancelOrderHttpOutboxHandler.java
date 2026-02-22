package com.jiaoyi.order.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.client.CouponServiceClient;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.dto.CancelOrderCommand;
import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.service.OutboxHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 取消订单 HTTP 调用 Handler
 * 处理 CANCEL_ORDER_HTTP 类型任务，执行优惠券退还和库存解锁
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CancelOrderHttpOutboxHandler implements OutboxHandler {
    
    private final CouponServiceClient couponServiceClient;
    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public boolean supports(String type) {
        return "CANCEL_ORDER_HTTP".equals(type);
    }
    
    @Override
    public void handle(Outbox outbox) throws Exception {
        // 解析 payload
        CancelOrderCommand command = objectMapper.readValue(outbox.getPayload(), CancelOrderCommand.class);
        
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("取消订单命令缺少 orderId");
        }
        
        Long orderId = command.getOrderId();
        
        log.info("【CancelOrderHandler】开始处理取消订单任务，outboxId: {}, orderId: {}", outbox.getId(), orderId);
        
        // 1. 退还优惠券
        try {
            com.jiaoyi.common.ApiResponse<com.jiaoyi.common.OperationResult> couponResponse = couponServiceClient.refundCouponByOrderId(orderId);
            if (couponResponse.getCode() != 200 || couponResponse.getData() == null) {
                throw new RuntimeException("优惠券退还失败: " + (couponResponse.getMessage() != null ? couponResponse.getMessage() : "未知错误"));
            }
            
            com.jiaoyi.common.OperationResult couponResult = couponResponse.getData();
            if (com.jiaoyi.common.OperationResult.ResultStatus.FAILED.equals(couponResult.getStatus())) {
                throw new RuntimeException("优惠券退还失败: " + couponResult.getMessage());
            }
            
            // SUCCESS 或 IDEMPOTENT_SUCCESS 都视为成功
            log.info("【CancelOrderHandler】优惠券退还{}，outboxId: {}, orderId: {}, 状态: {}", 
                    com.jiaoyi.common.OperationResult.ResultStatus.IDEMPOTENT_SUCCESS.equals(couponResult.getStatus()) ? "（幂等成功）" : "成功",
                    outbox.getId(), orderId, couponResult.getStatus());
        } catch (Exception e) {
            log.error("【CancelOrderHandler】优惠券退还失败，outboxId: {}, orderId: {}", outbox.getId(), orderId, e);
            throw new RuntimeException("优惠券退还失败: " + e.getMessage(), e);
        }
        
        // 2. 按订单归还库存
        if (orderId != null && !orderId.isEmpty()) {
            try {
                productServiceClient.returnByOrder(orderId);
                log.info("【CancelOrderHandler】按订单归还库存成功，outboxId: {}, orderId: {}", outbox.getId(), orderId);
            } catch (Exception e) {
                log.error("【CancelOrderHandler】按订单归还库存失败，outboxId: {}, orderId: {}", outbox.getId(), orderId, e);
                throw new RuntimeException("库存归还失败: " + e.getMessage(), e);
            }
        }
        
        log.info("【CancelOrderHandler】取消订单任务处理成功，outboxId: {}, orderId: {}", outbox.getId(), orderId);
    }
}

