package com.jiaoyi.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.product.config.RocketMQConfig;
import com.jiaoyi.product.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.mapper.sharding.StoreProductMapper;
import com.jiaoyi.product.mapper.primary.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 店铺商品缓存更新消息服务
 * 消费Outbox模式发送的RocketMQ消息，在数据库操作完成后更新缓存
 * 
 * 【Outbox模式流程】：
 * 1. 业务操作在本地事务中：插入/更新/删除数据库 + 写入outbox表
 * 2. 事务提交后，定时任务扫描outbox表并发送消息到RocketMQ
 * 3. 消费者（本服务）接收消息并更新缓存
 * 
 * 【关键点】：此时数据库操作已经完成（事务已提交），只需更新缓存即可
 */
@Service
@RequiredArgsConstructor
@Slf4j
// 暂时注释掉 RocketMQ 监听器，避免启动错误
// TODO: 升级到兼容 Spring Boot 3.x 的 RocketMQ 版本后重新启用
// @RocketMQMessageListener(
//     topic = RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
//     consumerGroup = RocketMQConfig.PRODUCT_CACHE_UPDATE_CONSUMER_GROUP,
//     selectorExpression = RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG
// )
// public class StoreProductCacheUpdateMessageService implements RocketMQListener<MessageExt> {
public class StoreProductCacheUpdateMessageService {
    
    private final StoreProductMapper storeProductMapper;
    private final StoreProductCacheService storeProductCacheService;
    private final ObjectMapper objectMapper;
    private final StoreMapper storeMapper;
    
    /**
     * 消费店铺商品缓存更新消息（Outbox模式）
     * 
     * 【关键点】：
     * 1. 此时数据库操作已经完成（事务已提交），productId已经生成
     * 2. 消息中应该已经包含了productId（在写入outbox时已设置）
     * 3. 如果productId为null，可能是消息格式问题，需要降级处理
     */
    // @Override - 暂时注释掉，因为不再实现 RocketMQListener 接口
    @Transactional
    public void onMessage(MessageExt messageExt) {
        try {
            // 解析消息体
            StoreProductCacheUpdateMessage message;
            byte[] body = messageExt.getBody();
            if (body != null && body.length > 0) {
                message = objectMapper.readValue(body, StoreProductCacheUpdateMessage.class);
            } else {
                log.error("消息体为空，无法解析");
                return;
            }
            
            Long storeId = message.getStoreId();
            Long productId = message.getProductId();
            StoreProductCacheUpdateMessage.OperationType operationType = message.getOperationType();

            
            log.info("接收到店铺商品缓存更新消息（Outbox模式），店铺ID: {}, 商品ID: {}, 操作类型: {}", 
                     storeId, productId, operationType);
            
            // 处理缓存更新，使用Lua脚本保证原子性和版本控制
            processCacheUpdate(message);
            
        } catch (Exception e) {
            log.error("处理店铺商品缓存更新消息失败", e);
        }
    }
    
    /**
     * 处理缓存更新的核心逻辑
     * 
     * 【核心逻辑】：从数据库查询关系表，使用数据库版本号更新缓存，防止并发问题
     */
    private void processCacheUpdate(StoreProductCacheUpdateMessage message) {
        Long productId = message.getProductId();
        Long storeId = message.getStoreId();
        StoreProductCacheUpdateMessage.OperationType operationType = message.getOperationType();
        
        switch (operationType) {
                case CREATE:
                case UPDATE: {
                    // CREATE/UPDATE操作：使用消息中的商品信息和版本号，不再查数据库
                    StoreProduct storeProduct = message.getStoreProduct();
                    Long version = message.getVersion();
                    
                    if (storeProduct != null && version != null) {
                        // 使用消息中的商品信息和版本号更新缓存
                        storeProductCacheService.cacheStoreProductWithVersion(storeProduct, version);
                        log.info("店铺商品缓存更新成功（{}），店铺ID: {}, 商品ID: {}, 版本号: {}", 
                                operationType, storeId, productId, version);
                    } else {
                        log.warn("消息中缺少商品信息或版本号，操作类型: {}, 商品ID: {}", operationType, productId);
                    }
                    
                    // 从数据库查询店铺的所有商品ID列表（关系表）
                    if (storeId != null) {
                        refreshStoreProductRelationCache(storeId);
                    }
                    break;
                }
                    
                case DELETE: {
                    // DELETE操作：逻辑删除
                    Long version = message.getVersion();
                    
                    // 使用版本号控制删除缓存（防止删除操作覆盖掉更高版本的更新）
                    // 只有当缓存中的版本号 <= 删除操作的版本号时，才删除缓存
                    if (productId != null && version != null) {
                        boolean deleted = storeProductCacheService.evictStoreProductCacheWithVersion(productId, version);
                        if (!deleted) {
                            log.warn("删除商品缓存跳过（版本号不匹配，缓存中有更新版本），店铺ID: {}, 商品ID: {}, 删除版本号: {}", 
                                    storeId, productId, version);
                        }
                    }
                    
                    // 从数据库查询店铺的所有商品ID列表（关系表，自动过滤is_delete=1的商品）
                    if (storeId != null) {
                        refreshStoreProductRelationCache(storeId);
                    }
                    
                    log.info("店铺商品缓存删除处理完成（逻辑删除），店铺ID: {}, 商品ID: {}, 版本号: {}", 
                            storeId, productId, version);
                    break;
                }
                    
                case REFRESH: {
                    // REFRESH操作：刷新缓存，从数据库重新加载
                    if (productId != null) {
                        Optional<StoreProduct> storeProductOpt = storeProductMapper.selectById(productId);
                        if (storeProductOpt.isPresent()) {
                            StoreProduct storeProduct = storeProductOpt.get();
                            storeProductCacheService.cacheStoreProduct(storeProduct);
                            log.info("店铺商品缓存刷新成功（REFRESH），店铺ID: {}, 商品ID: {}", storeId, productId);
                        } else {
                            log.warn("店铺商品不存在，删除缓存，商品ID: {}", productId);
                            storeProductCacheService.evictStoreProductCache(productId);
                        }
                    }
                    
                    // 刷新关系缓存
                    if (storeId != null) {
                        refreshStoreProductRelationCache(storeId);
                    }
                    break;
                }
                    
                default:
                    log.warn("未知的操作类型: {}, 店铺ID: {}, 商品ID: {}", operationType, storeId, productId);
            }
    }
    
    /**
     * 从数据库查询关系表，使用版本号更新关系缓存
     * 
     * 【核心逻辑】：
     * 1. 从store_products表查询店铺的所有商品ID
     * 2. 从stores表读取product_list_version
     * 3. 使用Lua脚本和版本号原子更新缓存，防止并发脏写
     */
    private void refreshStoreProductRelationCache(Long storeId) {
        try {

            // 2. 从stores表读取版本号
            Long version = storeMapper.getProductListVersion(storeId);
            if (version == null) {
                version = 0L;
                log.warn("无法获取店铺商品列表版本号，使用默认值0，店铺ID: {}", storeId);
            }

            // 1. 从数据库查询店铺的所有商品ID列表
            List<StoreProduct> storeProducts = storeProductMapper.selectByStoreId(storeId);
            Set<Long> productIds = storeProducts.stream()
                    .map(StoreProduct::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 3. 使用版本号更新缓存（Lua脚本保证原子性，防止并发脏写）
            storeProductCacheService.updateStoreProductIdsCacheWithVersion(storeId, productIds, version);
            
            log.debug("从数据库刷新店铺商品关系缓存，店铺ID: {}, 商品数量: {}, 版本号: {}", 
                    storeId, productIds.size(), version);
                    
        } catch (Exception e) {
            log.error("刷新店铺商品关系缓存失败，店铺ID: {}", storeId, e);
        }
    }
}


