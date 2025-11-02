package com.jiaoyi.service;

import com.jiaoyi.config.RocketMQConfig;
import com.jiaoyi.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.dto.StoreProductTransactionArg;
import com.jiaoyi.entity.StoreProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 店铺商品事务消息服务
 * 专门负责发送RocketMQ事务消息（半消息）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreProductMsgTransactionService {
    
    private final RocketMQTemplate rocketMQTemplate;
    
    /**
     * 发送创建店铺商品的事务消息（半消息）
     * 
     * 【RocketMQ事务消息流程】
     * 1. 先发送半消息到Broker（消息已存储但对消费者不可见）
     * 2. 立即同步调用 executeLocalTransaction()（执行数据库INSERT）
     * 3. 根据返回值决定 COMMIT/ROLLBACK 半消息
     * 4. 方法返回
     * 
     * @param storeId 店铺ID
     * @param storeProduct StoreProduct对象（用于CREATE操作）
     * @param productIdRef 用于传递insert后的ID
     */
    public void createInMsgTransaction(Long storeId, StoreProduct storeProduct, AtomicReference<Long> productIdRef) {
        log.info("准备发送创建店铺商品的事务消息（半消息），店铺ID: {}, 商品名称: {}", storeId, storeProduct.getProductName());
        sendTransactionMessage(
            null, // CREATE操作时ID还未生成，会在executeLocalTransaction中设置
            StoreProductCacheUpdateMessage.OperationType.CREATE,
            true, // 聚合库存信息
            storeId,
            storeProduct,
            productIdRef
        );
    }
    
    /**
     * 发送更新店铺商品的事务消息（半消息）
     * 
     * @param storeProductId 店铺商品ID
     * @param storeProduct StoreProduct对象（用于UPDATE操作）
     */
    public void updateInMsgTransaction(Long storeProductId, StoreProduct storeProduct) {
        log.info("准备发送更新店铺商品的事务消息（半消息），商品ID: {}", storeProductId);
        
        // 获取storeId（从storeProduct对象中获取，或从数据库查询）
        Long storeId = storeProduct.getStoreId();
        if (storeId == null && storeProductId != null) {
            // 如果没有storeId，需要从数据库查询（这里暂时不处理，由调用方保证）
            log.warn("更新操作中storeId为空，商品ID: {}", storeProductId);
        }
        
        sendTransactionMessage(
            storeProductId,
            StoreProductCacheUpdateMessage.OperationType.UPDATE,
            true, // 聚合库存信息
            storeId,
            storeProduct,
            null // UPDATE操作不需要productIdRef
        );
    }
    
    /**
     * 发送删除店铺商品的事务消息（半消息）
     * 
     * @param storeProductId 店铺商品ID
     * @param storeId 店铺ID（可选，如果提供可以优化查询）
     */
    public void deleteInMsgTransaction(Long storeProductId, Long storeId) {
        log.info("准备发送删除店铺商品的事务消息（半消息），商品ID: {}", storeProductId);
        sendTransactionMessage(
            storeProductId,
            StoreProductCacheUpdateMessage.OperationType.DELETE,
            false, // 删除操作不需要库存信息
            storeId,
            null, // DELETE操作不需要StoreProduct对象
            null // DELETE操作不需要productIdRef
        );
    }

    /**
     * 发送事务消息的通用方法
     * 
     * @param productId 店铺商品ID（CREATE操作时可能为null）
     * @param operationType 操作类型
     * @param enrichInventory 是否聚合库存信息
     * @param storeId 店铺ID
     * @param storeProduct StoreProduct对象（用于CREATE和UPDATE操作）
     * @param productIdRef 用于CREATE操作传递insert后的ID（可选，CREATE时使用）
     */
    private void sendTransactionMessage(Long productId, 
                                        StoreProductCacheUpdateMessage.OperationType operationType,
                                        boolean enrichInventory, 
                                        Long storeId,
                                        StoreProduct storeProduct,
                                        AtomicReference<Long> productIdRef) {
        try {
            if (rocketMQTemplate == null) {
                log.warn("RocketMQ不可用，跳过发送事务消息，店铺商品ID: {}", productId);
                return;
            }
            
            // 构建消息
            StoreProductCacheUpdateMessage message = new StoreProductCacheUpdateMessage();
            message.setProductId(productId);
            message.setStoreId(storeId);
            message.setOperationType(operationType);
            message.setTimestamp(System.currentTimeMillis());
            message.setEnrichInventory(enrichInventory);
            message.setStoreProduct(storeProduct); // 直接设置StoreProduct对象
            
            log.info("准备发送店铺商品事务消息（半消息），店铺ID: {}, 商品ID: {}, 操作类型: {}", storeId, productId, operationType);
            
            // 使用Product的Topic（或者创建新的StoreProduct Topic）
            String destination = RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC + ":" + RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG;
            Message<StoreProductCacheUpdateMessage> springMessage = MessageBuilder
                .withPayload(message)
                .build();

            log.info("步骤1: 发送半消息到Broker，店铺ID: {}, 商品ID: {}, 操作类型: {}", storeId, productId, operationType);
            
            // 传递productIdRef和storeId给executeLocalTransaction（通过arg参数）
            StoreProductTransactionArg transactionArg = null;
            if (productIdRef != null || storeId != null || storeProduct != null) {
                transactionArg = new StoreProductTransactionArg();
                if (productIdRef != null) {
                    transactionArg.setProductIdRef(productIdRef);
                }
                if (storeId != null) {
                    transactionArg.setStoreId(storeId);
                }
                if (storeProduct != null) {
                    transactionArg.setStoreProduct(storeProduct);
                }
            }

            rocketMQTemplate.sendMessageInTransaction(
                destination,
                springMessage,
                transactionArg // 传递包含productIdRef的容器对象（可能为null）
            );

            log.info("店铺商品事务消息发送完成，店铺ID: {}, 商品ID: {}, 操作类型: {} (半消息已处理完成，如果COMMIT则消费者可以看到消息)", 
                    storeId, productId, operationType);
            
        } catch (Exception e) {
            log.error("发送店铺商品事务消息失败，店铺ID: {}, 商品ID: {}, 操作类型: {}", storeId, productId, operationType, e);
            throw new RuntimeException("发送店铺商品事务消息失败: " + e.getMessage(), e);
        }
    }
}

