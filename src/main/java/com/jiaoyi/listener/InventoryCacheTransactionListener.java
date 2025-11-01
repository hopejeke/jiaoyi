package com.jiaoyi.listener;

import com.jiaoyi.dto.InventoryCacheUpdateMessage;
import com.jiaoyi.dto.InventoryTransactionArg;
import com.jiaoyi.entity.Inventory;
import com.jiaoyi.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 库存缓存更新事务消息监听器
 * 用于处理RocketMQ事务消息的本地事务和回查
 * 
 * 注意：@RocketMQTransactionListener会自动注册事务监听器
 * 事务监听器会与sendMessageInTransaction中指定的producerGroup关联
 */
@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQTransactionListener(corePoolSize = 5, maximumPoolSize = 10)
public class InventoryCacheTransactionListener implements RocketMQLocalTransactionListener {
    
    private final InventoryMapper inventoryMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 执行本地事务
     * 
     * 【RocketMQ事务消息流程 - 关键点】
     * 步骤2: 半消息已发送到Broker后，立即调用此方法执行本地事务（数据库操作）
     * 
     * 【为什么在这里执行数据库操作】
     * - 先发送半消息，再执行数据库操作
     * - 如果数据库操作成功，提交半消息；如果失败，回滚半消息
     * - 这样即使宕机，如果半消息已发送但数据库操作未执行，半消息会被回滚，保证数据一致性
     * 
     * 此时半消息状态：
     * - 消息已存储到Broker，但对消费者不可见
     * - 等待本地事务执行结果来决定提交或回滚
     * 
     * @param msg 半消息内容（包含InventoryCacheUpdateMessage数据）
     * @param arg 发送消息时传递的参数（InventoryTransactionArg）
     * @return 事务状态：
     *         - COMMIT: 提交半消息，消息变为可见，消费者可以消费
     *         - ROLLBACK: 回滚半消息，删除消息，不投递给消费者
     *         - UNKNOWN: 未知状态，触发回查机制
     */
    @Override
    @Transactional
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            // 解析消息
            byte[] body = (byte[]) msg.getPayload();
            InventoryCacheUpdateMessage message = objectMapper.readValue(body, InventoryCacheUpdateMessage.class);
            
            log.info("步骤2: 执行本地事务，商品ID: {}, 操作类型: {}, 数量: {}", 
                     message.getProductId(), message.getOperationType(), message.getQuantity());
            
            Long productId = message.getProductId();
            InventoryCacheUpdateMessage.OperationType operationType = message.getOperationType();
            Integer quantity = message.getQuantity();
            
            // 从arg中获取事务参数
            InventoryTransactionArg transactionArg = null;
            if (arg != null && arg instanceof InventoryTransactionArg) {
                transactionArg = (InventoryTransactionArg) arg;
                // 使用arg中的quantity和orderId（如果message中没有）
                if (quantity == null && transactionArg.getQuantity() != null) {
                    quantity = transactionArg.getQuantity();
                }
            }
            
            // 根据操作类型执行数据库操作
            int updatedRows = 0;
            switch (operationType) {
                case LOCK:
                    // 锁定库存
                    updatedRows = inventoryMapper.lockStock(productId, quantity);
                    if (updatedRows > 0) {
                        log.info("库存锁定成功，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("库存锁定失败，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case DEDUCT:
                    // 扣减库存
                    updatedRows = inventoryMapper.deductStock(productId, quantity);
                    if (updatedRows > 0) {
                        log.info("库存扣减成功，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("库存扣减失败，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case UNLOCK:
                    // 解锁库存
                    updatedRows = inventoryMapper.unlockStock(productId, quantity);
                    if (updatedRows > 0) {
                        log.info("库存解锁成功，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("库存解锁失败，商品ID: {}, 数量: {}", productId, quantity);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case REFRESH:
                    // 刷新缓存：验证库存是否存在
                    Inventory inventory = inventoryMapper.selectByProductId(productId);
                    if (inventory != null) {
                        log.info("库存存在，准备刷新缓存，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("库存不存在，取消刷新缓存，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case DELETE:
                    // 删除缓存：验证库存是否存在
                    inventory = inventoryMapper.selectByProductId(productId);
                    if (inventory == null) {
                        log.info("库存不存在，准备删除缓存，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("库存存在，取消删除缓存，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                default:
                    log.error("未知的操作类型: {}, 商品ID: {}", operationType, productId);
                    return RocketMQLocalTransactionState.ROLLBACK;
            }
            
        } catch (Exception e) {
            log.error("执行本地事务异常", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    /**
     * 检查本地事务状态（回查机制）
     * 
     * 当executeLocalTransaction返回UNKNOWN，或者长时间未收到事务状态时，
     * RocketMQ会定期调用此方法回查本地事务状态
     * 
     * 触发场景：
     * - executeLocalTransaction返回UNKNOWN
     * - 网络异常导致Broker未收到事务状态
     * - 应用重启后需要恢复事务状态
     * 
     * @param msg 半消息内容
     * @return 事务状态：COMMIT、ROLLBACK或UNKNOWN
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            // 解析消息
            byte[] body = (byte[]) msg.getPayload();
            InventoryCacheUpdateMessage message = objectMapper.readValue(body, InventoryCacheUpdateMessage.class);
            
            log.info("步骤3: 回查本地事务状态，商品ID: {}, 操作类型: {}", 
                     message.getProductId(), message.getOperationType());
            
            Long productId = message.getProductId();
            InventoryCacheUpdateMessage.OperationType operationType = message.getOperationType();
            
            // 从数据库查询库存，判断操作是否成功
            Inventory inventory = inventoryMapper.selectByProductId(productId);
            
            switch (operationType) {
                case LOCK:
                case DEDUCT:
                case UNLOCK:
                case REFRESH:
                    // 操作成功：库存应该存在
                    if (inventory != null) {
                        log.info("回查结果：库存存在，提交事务，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("回查结果：库存不存在，回滚事务，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case DELETE:
                    // 删除操作成功：库存应该不存在
                    if (inventory == null) {
                        log.info("回查结果：库存不存在，提交事务，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.COMMIT;
                    } else {
                        log.warn("回查结果：库存存在，回滚事务，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                default:
                    log.error("未知的操作类型: {}, 商品ID: {}", operationType, productId);
                    return RocketMQLocalTransactionState.ROLLBACK;
            }
            
        } catch (Exception e) {
            log.error("回查本地事务状态异常", e);
            return RocketMQLocalTransactionState.UNKNOWN; // 发生异常，继续回查
        }
    }
}

