package com.jiaoyi.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.dto.StoreProductTransactionArg;
import com.jiaoyi.entity.StoreProduct;
import com.jiaoyi.mapper.StoreProductMapper;
import com.jiaoyi.service.StoreProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 店铺商品缓存更新事务消息监听器
 * 用于处理RocketMQ事务消息的本地事务和回查
 * 
 * 注意：@RocketMQTransactionListener会自动注册事务监听器
 * 事务监听器会与sendMessageInTransaction中指定的producerGroup关联
 */
@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQTransactionListener(corePoolSize = 5, maximumPoolSize = 10)
public class StoreProductTransactionHandler implements RocketMQLocalTransactionListener {
    
    private final StoreProductMapper storeProductMapper;
    private final StoreProductService storeProductService;
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
     * @param msg 半消息内容（包含StoreProduct数据）
     * @param arg 发送消息时传递的参数（StoreProductTransactionArg）
     * @return 事务状态：
     *         - COMMIT: 提交半消息，消息变为可见，消费者可以消费
     *         - ROLLBACK: 回滚半消息，删除消息，不投递给消费者
     *         - UNKNOWN: 未知状态，触发回查机制
     */
    @Override
    @Transactional
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            // 解析消息：RocketMQ事务监听器中，getPayload()返回的是字节数组
            StoreProductCacheUpdateMessage message;
            Object payload = msg.getPayload();
            if (payload instanceof byte[]) {
                // 如果是字节数组，需要反序列化
                message = objectMapper.readValue((byte[]) payload, StoreProductCacheUpdateMessage.class);
            } else if (payload instanceof StoreProductCacheUpdateMessage) {
                // 如果已经是对象，直接使用
                message = (StoreProductCacheUpdateMessage) payload;
            } else {
                log.error("未知的消息格式: {}", payload.getClass());
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            
            StoreProduct storeProduct = message.getStoreProduct();
            Long productId = message.getProductId();
            Long storeId = message.getStoreId();
            
            // 从arg中获取StoreProductTransactionArg（如果存在）
            StoreProductTransactionArg transactionArg = null;
            if (arg instanceof StoreProductTransactionArg) {
                transactionArg = (StoreProductTransactionArg) arg;
                // 如果transactionArg中有storeId和storeProduct，优先使用
                if (transactionArg.getStoreId() != null) {
                    storeId = transactionArg.getStoreId();
                }
                if (transactionArg.getStoreProduct() != null) {
                    storeProduct = transactionArg.getStoreProduct();
                }
            }
            
            log.info("步骤2: 半消息已发送，开始执行本地事务（数据库操作），操作类型: {}, 店铺ID: {}, 商品ID: {}", 
                    message.getOperationType(), storeId, productId);
            
            // 根据操作类型执行数据库操作
            switch (message.getOperationType()) {
                case CREATE:
                    // CREATE操作：调用StoreProductService的内部方法执行数据库INSERT
                    if (storeProduct == null || storeId == null) {
                        log.error("CREATE操作缺少StoreProduct数据或storeId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    try {
                        // 调用StoreProductService的内部方法执行数据库操作
                        StoreProduct insertedProduct = storeProductService.createStoreProductInternal(storeId, storeProduct);
                        Long insertedProductId = insertedProduct.getId();
                        
                        // 将insert后的ID设置到productIdRef中，以便Controller获取
                        if (transactionArg != null && transactionArg.getProductIdRef() != null) {
                            transactionArg.getProductIdRef().set(insertedProductId);
                            log.info("已将productId设置到productIdRef，店铺商品ID: {}", insertedProductId);
                        }
                        
                        // 更新消息中的productId（用于后续缓存更新）
                        message.setProductId(insertedProductId);
                        log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                        return RocketMQLocalTransactionState.COMMIT;
                        
                    } catch (Exception e) {
                        log.error("CREATE操作：数据库INSERT失败，回滚半消息", e);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case UPDATE:
                    // UPDATE操作：调用StoreProductService的内部方法执行数据库UPDATE
                    if (storeProduct == null || productId == null) {
                        log.error("UPDATE操作缺少StoreProduct数据或productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    try {
                        // 调用StoreProductService的内部方法执行数据库操作
                        storeProductService.updateStoreProductInternal(storeProduct);
                        log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                        return RocketMQLocalTransactionState.COMMIT;
                    } catch (Exception e) {
                        log.error("UPDATE操作：数据库UPDATE失败，回滚半消息", e);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case DELETE:
                    // DELETE操作：调用StoreProductService的内部方法执行数据库DELETE
                    if (productId == null) {
                        log.error("DELETE操作缺少productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    try {
                        // 调用StoreProductService的内部方法执行数据库操作
                        storeProductService.deleteStoreProductInternal(productId);
                        log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                        return RocketMQLocalTransactionState.COMMIT;
                    } catch (Exception e) {
                        log.error("DELETE操作：数据库DELETE失败，回滚半消息", e);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                case REFRESH:
                    // REFRESH操作不修改数据库，只验证商品是否存在
                    if (productId == null) {
                        log.error("REFRESH操作缺少productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    Optional<StoreProduct> storeProductOpt = storeProductMapper.selectById(productId);
                    if (!storeProductOpt.isPresent()) {
                        log.warn("REFRESH操作：店铺商品不存在，回滚半消息，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    log.info("REFRESH操作：店铺商品存在，提交半消息，商品ID: {}", productId);
                    log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                    return RocketMQLocalTransactionState.COMMIT;
                    
                default:
                    log.error("未知的操作类型，回滚半消息: {}", message.getOperationType());
                    return RocketMQLocalTransactionState.ROLLBACK;
            }
            
        } catch (Exception e) {
            log.error("执行本地事务（数据库操作）失败，回滚半消息", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    /**
     * 本地事务回查
     * 
     * 【RocketMQ事务消息回查机制】
     * 当executeLocalTransaction返回UNKNOWN，或者长时间未收到事务状态时，
     * RocketMQ会定期调用此方法回查本地事务状态
     * 
     * 回查场景：
     * - 网络异常导致事务状态未及时返回
     * - 应用重启导致事务状态丢失
     * - executeLocalTransaction返回UNKNOWN
     * 
     * @param msg 半消息内容
     * @return 事务状态：
     *         - COMMIT: 提交半消息，消息变为可见
     *         - ROLLBACK: 回滚半消息，删除消息
     *         - UNKNOWN: 继续回查（稍后再次调用此方法）
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            // 解析消息：RocketMQ事务监听器中，getPayload()返回的是字节数组
            StoreProductCacheUpdateMessage message;
            Object payload = msg.getPayload();
            if (payload instanceof byte[]) {
                // 如果是字节数组，需要反序列化
                message = objectMapper.readValue((byte[]) payload, StoreProductCacheUpdateMessage.class);
            } else if (payload instanceof StoreProductCacheUpdateMessage) {
                // 如果已经是对象，直接使用
                message = (StoreProductCacheUpdateMessage) payload;
            } else {
                log.error("未知的消息格式: {}", payload.getClass());
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            Long productId = message.getProductId();
            
            log.info("【回查机制】回查半消息的本地事务状态，店铺商品ID: {}, 操作类型: {}", productId, message.getOperationType());
            
            // 检查店铺商品是否存在
            Optional<StoreProduct> storeProductOpt = storeProductMapper.selectById(productId);
            
            // 根据操作类型判断事务状态
            if (message.getOperationType() == StoreProductCacheUpdateMessage.OperationType.DELETE) {
                // 删除操作：如果商品不存在，说明删除成功，提交事务
                return storeProductOpt.isPresent() ? 
                    RocketMQLocalTransactionState.UNKNOWN : // 商品还存在，可能删除还未完成，继续回查
                    RocketMQLocalTransactionState.COMMIT;   // 商品不存在，删除成功，提交事务
            } else {
                // 其他操作：如果商品存在，说明操作成功，提交事务
                return storeProductOpt.isPresent() ? 
                    RocketMQLocalTransactionState.COMMIT :   // 商品存在，操作成功，提交事务
                    RocketMQLocalTransactionState.ROLLBACK;  // 商品不存在，操作失败，回滚事务
            }
            
        } catch (Exception e) {
            log.error("回查本地事务状态失败", e);
            return RocketMQLocalTransactionState.UNKNOWN; // 发生异常，继续回查
        }
    }
}

