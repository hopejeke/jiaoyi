package com.jiaoyi.product.service;

import com.jiaoyi.product.config.RocketMQConfig;
import com.jiaoyi.product.dto.InventoryCacheUpdateMessage;
import com.jiaoyi.product.dto.InventoryTransactionArg;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.mapper.sharding.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 库存缓存更新消息服务
 * 使用RocketMQ事务消息（半消息）实现库存缓存更新的异步处理
 * 确保数据库操作与缓存更新的一致性
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
    topic = RocketMQConfig.INVENTORY_CACHE_UPDATE_TOPIC,
    consumerGroup = RocketMQConfig.INVENTORY_CACHE_UPDATE_CONSUMER_GROUP,
    selectorExpression = RocketMQConfig.INVENTORY_CACHE_UPDATE_TAG
)
public class InventoryCacheUpdateMessageService implements RocketMQListener<InventoryCacheUpdateMessage> {
    
    private final InventoryMapper inventoryMapper;
    private final InventoryCacheService inventoryCacheService;
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    /**
     * 发送库存缓存更新事务消息（半消息）
     * 
     * 【关键点】：先发送半消息，数据库操作在executeLocalTransaction中执行
     * 这样即使宕机，如果半消息已发送但数据库操作未执行，半消息会被回滚，保证一致性
     * 
     * @param productId 商品ID
     * @param operationType 操作类型
     * @param quantity 操作数量
     * @param orderId 订单ID（可选）
     * @param inventory 库存数据（用于LOCK/DEDUCT/UNLOCK操作，在executeLocalTransaction中执行数据库操作）
     * @param beforeInventory 操作前库存数据（用于记录变更前状态）
     */
    public void sendCacheUpdateMessage(Long productId, 
                                       InventoryCacheUpdateMessage.OperationType operationType,
                                       Integer quantity, 
                                       Long orderId,
                                       Inventory inventory,
                                       Inventory beforeInventory) {
        try {
            if (rocketMQTemplate == null) {
                log.warn("RocketMQ不可用，跳过发送库存缓存更新消息，商品ID: {}", productId);
                // RocketMQ不可用时，直接同步更新缓存
                if (productId != null) {
                    updateCacheDirectly(productId, operationType);
                }
                return;
            }
            
            InventoryCacheUpdateMessage message = new InventoryCacheUpdateMessage();
            message.setProductId(productId);
            message.setOperationType(operationType);
            message.setQuantity(quantity);
            message.setOrderId(orderId);
            message.setTimestamp(System.currentTimeMillis());
            message.setMessageVersion((long) UUID.randomUUID().toString().hashCode());
            message.setInventory(inventory);
            message.setBeforeInventory(beforeInventory);
            
            log.info("准备发送库存缓存更新事务消息（半消息），商品ID: {}, 操作类型: {}, 数量: {}", 
                     productId, operationType, quantity);
            
            String destination = RocketMQConfig.INVENTORY_CACHE_UPDATE_TOPIC + ":" + RocketMQConfig.INVENTORY_CACHE_UPDATE_TAG;
            Message<InventoryCacheUpdateMessage> springMessage = MessageBuilder
                .withPayload(message)
                .build();
            
            // ==================== RocketMQ事务消息流程 ====================
            // 1. 发送半消息（Half Message）到Broker
            //    - 此时消息对消费者不可见
            //    - Broker会存储半消息，等待本地事务结果
            //
            // 2. executeLocalTransaction执行本地事务
            //    - 在InventoryCacheTransactionListener中执行
            //    - 执行数据库操作（锁定/扣减/解锁库存）
            //    - 返回COMMIT或ROLLBACK
            //
            // 3. 根据本地事务结果处理半消息
            //    - COMMIT: 半消息变为正式消息，消费者可以消费
            //    - ROLLBACK: 删除半消息，消息不会投递
            //    - UNKNOWN: 触发回查机制（checkLocalTransaction）
            //
            // sendMessageInTransaction内部流程：
            //   1) 发送半消息 → 2) 调用executeLocalTransaction → 3) 提交/回滚半消息
            // =============================================================
            log.info("步骤1: 发送半消息到Broker，商品ID: {}", productId);
            
            // 传递事务参数
            InventoryTransactionArg transactionArg = new InventoryTransactionArg();
            transactionArg.setQuantity(quantity);
            transactionArg.setOrderId(orderId);
            
            rocketMQTemplate.sendMessageInTransaction(
                destination,
                springMessage,
                transactionArg
            );
            
            log.info("库存缓存更新事务消息发送完成，商品ID: {} (半消息已发送，等待本地事务结果)", productId);
            
        } catch (Exception e) {
            log.error("发送库存缓存更新消息失败，商品ID: {}, 操作类型: {}", productId, operationType, e);
            // 发送失败时，直接同步更新缓存，确保数据一致性
            if (productId != null) {
                updateCacheDirectly(productId, operationType);
            }
        }
    }
    
    /**
     * 消费库存缓存更新消息（仅当半消息COMMIT后才会收到此消息）
     * 
     * 【关键点】：此时数据库操作已经完成，只需更新缓存即可
     */
    @Override
    @Transactional
    public void onMessage(InventoryCacheUpdateMessage message) {
        log.info("步骤4: 接收到库存缓存更新正式消息（半消息已提交），商品ID: {}, 操作类型: {}, 数量: {}", 
                 message.getProductId(), message.getOperationType(), message.getQuantity());
        
        try {
            Long productId = message.getProductId();
            InventoryCacheUpdateMessage.OperationType operationType = message.getOperationType();
            
            switch (operationType) {
                case LOCK:
                case DEDUCT:
                case UNLOCK:
                    // 从数据库重新加载库存信息（确保数据最新）
                    Inventory inventory = inventoryMapper.selectByProductId(productId);
                    if (inventory != null) {
                        inventoryCacheService.updateInventoryCache(inventory);
                        log.info("库存缓存更新成功，商品ID: {}, 操作类型: {}", productId, operationType);
                    } else {
                        log.warn("库存不存在，删除缓存，商品ID: {}", productId);
                        inventoryCacheService.evictInventoryCache(productId);
                    }
                    break;
                    
                case REFRESH:
                    // 刷新缓存：删除旧缓存，从数据库重新加载
                    inventoryCacheService.evictInventoryCache(productId);
                    inventory = inventoryMapper.selectByProductId(productId);
                    if (inventory != null) {
                        inventoryCacheService.cacheInventory(inventory);
                        log.info("库存缓存刷新成功，商品ID: {}", productId);
                    }
                    break;
                    
                case DELETE:
                    // 删除缓存
                    inventoryCacheService.evictInventoryCache(productId);
                    log.info("库存缓存删除成功，商品ID: {}", productId);
                    break;
                    
                default:
                    log.warn("未知的操作类型: {}, 商品ID: {}", operationType, productId);
            }
            
        } catch (Exception e) {
            log.error("处理库存缓存更新消息失败，商品ID: {}, 操作类型: {}", 
                     message.getProductId(), message.getOperationType(), e);
        }
    }
    
    /**
     * 直接更新缓存（RocketMQ不可用时的降级方案）
     */
    private void updateCacheDirectly(Long productId, InventoryCacheUpdateMessage.OperationType operationType) {
        try {
            Inventory inventory = inventoryMapper.selectByProductId(productId);
            if (inventory != null) {
                inventoryCacheService.updateInventoryCache(inventory);
                log.info("直接更新库存缓存成功，商品ID: {}, 操作类型: {}", productId, operationType);
            } else {
                inventoryCacheService.evictInventoryCache(productId);
            }
        } catch (Exception e) {
            log.error("直接更新库存缓存失败，商品ID: {}, 操作类型: {}", productId, operationType, e);
        }
    }
}


