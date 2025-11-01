package com.jiaoyi.service;

import com.jiaoyi.config.RocketMQConfig;
import com.jiaoyi.dto.ProductCacheUpdateMessage;
import com.jiaoyi.dto.ProductTransactionArg;
import com.jiaoyi.entity.Product;
import com.jiaoyi.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品缓存更新消息服务
 * 使用RocketMQ事务消息实现缓存更新的异步处理
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
    topic = RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
    consumerGroup = RocketMQConfig.PRODUCT_CACHE_UPDATE_CONSUMER_GROUP,
    selectorExpression = RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG
)
public class ProductCacheUpdateMessageService implements RocketMQListener<ProductCacheUpdateMessage> {
    
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    
    @Autowired
    @Lazy
    private ProductService productService;
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    /**
     * 发送商品缓存更新事务消息
     * 
     * 【关键点】：先发送半消息，数据库操作在executeLocalTransaction中执行
     * 这样即使宕机，如果半消息已发送但数据库操作未执行，半消息会被回滚，保证一致性
     * 
     * @param productId 商品ID（CREATE操作时可能为null）
     * @param operationType 操作类型
     * @param enrichInventory 是否聚合库存信息
     * @param product Product对象（用于CREATE和UPDATE操作，在executeLocalTransaction中执行数据库操作）
     * @param productIdRef 用于CREATE操作传递insert后的ID（可选，CREATE时使用）
     */
    public void sendCacheUpdateMessage(Long productId, ProductCacheUpdateMessage.OperationType operationType, 
                                       boolean enrichInventory, Product product,
                                       java.util.concurrent.atomic.AtomicReference<Long> productIdRef) {
        try {
            if (rocketMQTemplate == null) {
                log.warn("RocketMQ不可用，跳过发送缓存更新消息，商品ID: {}", productId);
                // RocketMQ不可用时，直接同步更新缓存
                if (productId != null) {
                    updateCacheDirectly(productId, operationType, enrichInventory);
                }
                return;
            }
            
            ProductCacheUpdateMessage message = new ProductCacheUpdateMessage();
            message.setProductId(productId);
            message.setOperationType(operationType);
            message.setTimestamp(System.currentTimeMillis());
            message.setMessageVersion((long) UUID.randomUUID().toString().hashCode());
            message.setEnrichInventory(enrichInventory);
            message.setProduct(product); // 传递Product数据
            
            log.info("准备发送商品缓存更新事务消息（半消息），商品ID: {}, 操作类型: {}", productId, operationType);
            
            String destination = RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC + ":" + RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG;
            Message<ProductCacheUpdateMessage> springMessage = MessageBuilder
                .withPayload(message)
                .build();
            
            // ==================== RocketMQ事务消息流程 ====================
            // 1. 发送半消息（Half Message）到Broker
            //    - 此时消息对消费者不可见
            //    - Broker会存储半消息，等待本地事务结果
            //
            // 2. executeLocalTransaction执行本地事务
            //    - 在ProductCacheTransactionListener中执行
            //    - 检查商品是否存在等业务逻辑
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
            
            // 传递productIdRef给executeLocalTransaction（通过arg参数）
            // 注意：RocketMQ的arg参数只能传递一个对象，我们需要传递一个包含productIdRef的容器
            ProductTransactionArg transactionArg = null;
            if (productIdRef != null) {
                transactionArg = new ProductTransactionArg();
                transactionArg.setProductIdRef(productIdRef);
            }
            
            // ==================== sendMessageInTransaction 是同步阻塞调用 ====================
            // 执行流程（同步执行，会阻塞直到完成）：
            //   1) 发送半消息到Broker（同步，消息已存储但对消费者不可见）
            //   2) 立即同步调用 executeLocalTransaction()（在当前线程执行）
            //   3) 根据返回值决定 COMMIT/ROLLBACK 半消息（同步）
            //   4) 方法返回（此时半消息已经变为正式消息或被删除）
            //
            // 【重要】当这个方法返回时：
            // - 半消息已经发送到Broker（物理上已存储）
            // - executeLocalTransaction 已经执行完成
            // - 半消息已经根据结果变为正式消息（COMMIT）或被删除（ROLLBACK）
            // - 如果是COMMIT，消费者已经可以看到消息了（或者很快就会看到）
            // ============================================================================
            log.info("准备调用sendMessageInTransaction（同步阻塞调用）");
            
            rocketMQTemplate.sendMessageInTransaction(
                destination,
                springMessage,
                transactionArg // 传递包含productIdRef的容器对象（可能为null）
            );
            
            // 执行到这里时，说明：
            // 1. 半消息已发送到Broker
            // 2. executeLocalTransaction已执行完成
            // 3. 半消息已COMMIT（变为正式消息）或ROLLBACK（已删除）
            log.info("商品缓存更新事务消息发送完成，商品ID: {} (半消息已处理完成，如果COMMIT则消费者可以看到消息)", productId);
            
        } catch (Exception e) {
            log.error("发送商品缓存更新消息失败，商品ID: {}", productId, e);
            // 发送失败时，直接同步更新缓存作为降级方案
            updateCacheDirectly(productId, operationType, enrichInventory);
        }
    }
    
    /**
     * 直接更新缓存（降级方案）
     */
    private void updateCacheDirectly(Long productId, ProductCacheUpdateMessage.OperationType operationType, boolean enrichInventory) {
        try {
            switch (operationType) {
                case CREATE:
                case UPDATE:
                case REFRESH:
                    // 刷新缓存
                    productService.refreshProductCache(productId);
                    break;
                case DELETE:
                    // 删除缓存
                    productCacheService.evictProductCache(productId);
                    productCacheService.evictProductListCache();
                    break;
            }
            log.info("直接更新缓存完成，商品ID: {}, 操作类型: {}", productId, operationType);
        } catch (Exception e) {
            log.error("直接更新缓存失败，商品ID: {}", productId, e);
        }
    }
    
    /**
     * RocketMQ消息监听器 - 消费消息并更新缓存
     * 
     * 【RocketMQ事务消息流程】
     * 步骤4: 消费正式消息（半消息已提交后变为正式消息）
     * 
     * 此时消息状态：
     * - 半消息已通过executeLocalTransaction验证并COMMIT
     * - 消息已变为正式消息，对消费者可见
     * - 可以安全地执行缓存更新操作
     */
    @Override
    @Transactional
    public void onMessage(ProductCacheUpdateMessage message) {
        log.info("步骤4: 接收到商品缓存更新正式消息（半消息已提交），商品ID: {}, 操作类型: {}", 
                message.getProductId(), message.getOperationType());
        
        try {
            handleCacheUpdate(message);
            log.info("商品缓存更新消息处理成功，商品ID: {}", message.getProductId());
        } catch (Exception e) {
            log.error("处理商品缓存更新消息失败，商品ID: {}", message.getProductId(), e);
            // 可以在这里实现重试逻辑或者死信队列
            throw e; // 抛出异常让RocketMQ重试
        }
    }
    
    /**
     * 处理缓存更新
     */
    private void handleCacheUpdate(ProductCacheUpdateMessage message) {
        Long productId = message.getProductId();
        ProductCacheUpdateMessage.OperationType operationType = message.getOperationType();
        
        switch (operationType) {
            case CREATE:
            case UPDATE:
                // 从数据库重新加载并更新缓存
                Optional<Product> productOpt = productMapper.selectById(productId);
                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    // 如果需要聚合库存信息，调用ProductService的同步方法
                    if (Boolean.TRUE.equals(message.getEnrichInventory())) {
                        // 聚合库存信息并缓存（使用同步方法避免循环调用）
                        productService.refreshProductCacheSync(productId);
                    } else {
                        // 只缓存商品信息
                        productCacheService.cacheProduct(product);
                    }
                    log.info("商品缓存更新成功，商品ID: {}", productId);
                } else {
                    log.warn("商品不存在，无法更新缓存，商品ID: {}", productId);
                }
                break;
                
            case DELETE:
                // 删除缓存
                productCacheService.evictProductCache(productId);
                productCacheService.evictProductListCache();
                log.info("商品缓存删除成功，商品ID: {}", productId);
                break;
                
            case REFRESH:
                // 刷新缓存（使用同步方法避免循环调用）
                productService.refreshProductCacheSync(productId);
                log.info("商品缓存刷新成功，商品ID: {}", productId);
                break;
        }
    }
}
