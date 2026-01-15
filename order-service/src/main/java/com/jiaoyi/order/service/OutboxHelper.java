package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.dto.DeductStockCommand;
import com.jiaoyi.order.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Outbox 辅助类
 * 提供通用的 outbox 任务创建方法，避免重复代码
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxHelper {
    
    private final com.jiaoyi.outbox.OutboxService outboxService;
    private final ObjectMapper objectMapper;
    
    /**
     * 创建并写入库存扣减任务到 outbox
     * 
     * @param orderId 订单ID
     * @param orderItems 订单项列表（必须包含 merchantId）
     * @return 是否成功写入
     */
    public boolean enqueueDeductStockTask(Long orderId, List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            log.warn("订单项为空，无法写入库存扣减 outbox，orderId: {}", orderId);
            return false;
        }
        
        // 从订单项中获取 storeId（用于分库路由，ShardingSphere 使用 store_id 作为分片键）
        Long storeId = orderItems.stream()
                .filter(item -> item.getStoreId() != null)
                .map(OrderItem::getStoreId)
                .findFirst()
                .orElse(null);
        
        if (storeId == null) {
            log.warn("订单项缺少 storeId，无法写入库存扣减 outbox，orderId: {}", orderId);
            return false;
        }
        
        // 从订单项中获取 merchantId（用于日志记录）
        String merchantId = orderItems.stream()
                .filter(item -> item.getMerchantId() != null && !item.getMerchantId().isEmpty())
                .map(OrderItem::getMerchantId)
                .findFirst()
                .orElse(null);
        
        List<Long> productIds = orderItems.stream()
                .filter(item -> item.getProductId() != null)
                .map(OrderItem::getProductId)
                .collect(Collectors.toList());
        List<Long> skuIds = orderItems.stream()
                .filter(item -> item.getSkuId() != null)
                .map(OrderItem::getSkuId)
                .collect(Collectors.toList());
        List<Integer> quantities = orderItems.stream()
                .map(OrderItem::getQuantity)
                .collect(Collectors.toList());
        
        if (productIds.isEmpty() || skuIds.isEmpty() || productIds.size() != skuIds.size()) {
            log.warn("订单项缺少productId或skuId，无法写入库存扣减 outbox，orderId: {}", orderId);
            return false;
        }
        
        try {
            // 幂等键：orderId + "-DEDUCT"
            String idempotencyKey = orderId + "-DEDUCT";
            
            DeductStockCommand deductCommand = DeductStockCommand.builder()
                    .orderId(orderId)
                    .productIds(productIds)
                    .skuIds(skuIds)
                    .quantities(quantities)
                    .idempotencyKey(idempotencyKey)
                    .build();
            
            String payload = objectMapper.writeValueAsString(deductCommand);
            
            log.info("【OutboxHelper】准备写入库存扣减任务，orderId: {}, merchantId: {}, productIds: {}, skuIds: {}, quantities: {}", 
                    orderId, merchantId, productIds, skuIds, quantities);
            
            // 计算 shard_id（基于 storeId，与订单和商品服务保持一致）
            // 使用 ShardUtil 统一计算逻辑：hash(storeId) & 1023
            int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(storeId);
            
            // 传入 shardingKey（通用分片键字段，存 storeId 的字符串形式）和 shardId（用于扫描优化）
            // ShardingSphere 会根据 store_id 自动路由到正确的分片库和表
            com.jiaoyi.outbox.entity.Outbox outbox = outboxService.enqueue(
                    "DEDUCT_STOCK_HTTP",  // type
                    String.valueOf(orderId),  // bizKey（用于唯一约束）
                    payload,  // payload
                    null,  // topic（HTTP 类型不需要）
                    null,  // tag（HTTP 类型不需要）
                    null,  // messageKey（HTTP 类型不需要）
                    String.valueOf(storeId),  // shardingKey（通用分片键字段，存 storeId 的字符串形式）
                    shardId  // shardId（0-1023，用于扫描优化）
            );
            
            log.info("【OutboxHelper】✓ 库存扣减任务已写入 outbox，outboxId: {}, orderId: {}, merchantId: {}, idempotencyKey: {}, 商品数量: {}", 
                    outbox != null ? outbox.getId() : "null", orderId, merchantId, idempotencyKey, productIds.size());
            
            // 注意：kick 事件已由 OutboxService 内部自动触发（事务提交后），业务方无需手动触发
            // 这样业务方完全无感，只需要调用 outboxService.enqueue(...) 即可
            
            return true;
            
        } catch (Exception e) {
            log.error("【OutboxHelper】✗ 写入库存扣减 outbox 失败，orderId: {}, merchantId: {}, 错误: {}", 
                    orderId, merchantId, e.getMessage(), e);
            return false;
        }
    }
}

