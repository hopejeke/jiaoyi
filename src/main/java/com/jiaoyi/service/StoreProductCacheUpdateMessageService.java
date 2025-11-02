package com.jiaoyi.service;

import com.jiaoyi.config.RocketMQConfig;
import com.jiaoyi.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.entity.StoreProduct;
import com.jiaoyi.mapper.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 店铺商品缓存更新消息服务
 * 消费RocketMQ事务消息，在数据库操作完成后更新缓存
 * 
 * 【关键点】：只有当半消息COMMIT后，此消费者才会收到消息
 * 此时数据库操作已经完成，只需更新缓存即可
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
    topic = RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
    consumerGroup = RocketMQConfig.PRODUCT_CACHE_UPDATE_CONSUMER_GROUP,
    selectorExpression = RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG
)
public class StoreProductCacheUpdateMessageService implements RocketMQListener<StoreProductCacheUpdateMessage> {
    
    private final StoreProductMapper storeProductMapper;
    private final StoreProductCacheService storeProductCacheService;
    private final RedissonClient redissonClient;
    
    // 分布式锁key前缀（与初始化缓存使用相同的key格式，便于协调）
    private static final String CACHE_UPDATE_LOCK_PREFIX = "store:product:cache:lock:"; // 统一使用 storeId 作为key
    private static final int LOCK_WAIT_TIME = 3; // 等待锁的时间（秒）
    private static final int LOCK_LEASE_TIME = 30; // 锁持有时间（秒）
    
    /**
     * 消费店铺商品缓存更新消息（仅当半消息COMMIT后才会收到此消息）
     * 
     * 【关键点】：此时数据库操作已经完成，只需更新缓存即可
     */
    @Override
    @Transactional
    public void onMessage(StoreProductCacheUpdateMessage message) {
        log.info("步骤4: 接收到店铺商品缓存更新正式消息（半消息已提交），店铺ID: {}, 商品ID: {}, 操作类型: {}", 
                 message.getStoreId(), message.getProductId(), message.getOperationType());
        
        Long productId = message.getProductId();
        Long storeId = message.getStoreId();
        
        // 统一使用storeId作为锁key，与初始化缓存使用相同的锁，便于协调
        // 这样消息更新缓存和初始化缓存可以相互等待，避免冲突
        String lockKey;
        if (storeId != null) {
            lockKey = CACHE_UPDATE_LOCK_PREFIX + storeId;
        } else {
            log.warn("消息缺少storeId，无法获取锁，店铺ID: {}, 商品ID: {}", storeId, productId);
            return;
        }
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取分布式锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取分布式锁失败，可能存在并发消费，店铺ID: {}, 商品ID: {}, 操作类型: {}", 
                        storeId, productId, message.getOperationType());
                return;
            }
            
            log.info("成功获取分布式锁，开始处理缓存更新，店铺ID: {}, 商品ID: {}, 操作类型: {}", 
                    storeId, productId, message.getOperationType());
            
            try {
                processCacheUpdate(message);
            } finally {
                // 释放锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("释放分布式锁，店铺ID: {}, 商品ID: {}", storeId, productId);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断，店铺ID: {}, 商品ID: {}", storeId, productId, e);
        } catch (Exception e) {
            log.error("处理店铺商品缓存更新消息失败，店铺ID: {}, 商品ID: {}, 操作类型: {}", 
                     storeId, productId, message.getOperationType(), e);
        }
    }
    
    /**
     * 处理缓存更新的核心逻辑
     */
    private void processCacheUpdate(StoreProductCacheUpdateMessage message) {
        Long productId = message.getProductId();
        Long storeId = message.getStoreId();
        StoreProductCacheUpdateMessage.OperationType operationType = message.getOperationType();
        
        switch (operationType) {
                case CREATE: {
                    // CREATE操作：从数据库重新加载店铺商品信息（确保数据最新）
                    Optional<StoreProduct> storeProductOpt = Optional.empty();
                    
                    if (productId != null) {
                        // 如果有productId，直接查询
                        storeProductOpt = storeProductMapper.selectById(productId);
                    } else if (storeId != null && message.getStoreProduct() != null) {
                        // 如果没有productId，通过storeId和productName查询（CREATE操作的降级方案）
                        String productName = message.getStoreProduct().getProductName();
                        if (productName != null) {
                            storeProductOpt = storeProductMapper.selectByStoreIdAndProductName(storeId, productName);
                            log.info("CREATE操作缺少productId，通过storeId和productName查询，店铺ID: {}, 商品名称: {}", storeId, productName);
                        }
                    }
                    
                    if (storeProductOpt.isPresent()) {
                        StoreProduct storeProduct = storeProductOpt.get();
                        Long insertedProductId = storeProduct.getId();
                        storeProductCacheService.cacheStoreProduct(storeProduct);
                        // 将商品ID添加到店铺的关系缓存中
                        storeProductCacheService.addProductIdToStore(storeId, insertedProductId);
                        // 删除店铺商品列表缓存（因为新增了商品）
                        storeProductCacheService.evictStoreProductListCache(storeId);
                        log.info("店铺商品缓存更新成功（CREATE），店铺ID: {}, 商品ID: {}", storeId, insertedProductId);
                    } else {
                        if (productId == null && storeId != null && message.getStoreProduct() != null) {
                            log.warn("CREATE操作：无法通过storeId和productName找到商品，店铺ID: {}, 商品名称: {}", 
                                    storeId, message.getStoreProduct().getProductName());
                        } else {
                            log.warn("CREATE操作：店铺商品不存在，店铺ID: {}, 商品ID: {}", storeId, productId);
                        }
                    }
                    break;
                }
                    
                case UPDATE: {
                    // UPDATE操作：从数据库重新加载店铺商品信息（确保数据最新）
                    if (productId != null) {
                        Optional<StoreProduct> storeProductOpt = storeProductMapper.selectById(productId);
                        if (storeProductOpt.isPresent()) {
                            StoreProduct storeProduct = storeProductOpt.get();
                            Long newStoreId = storeProduct.getStoreId();
                            Long oldStoreId = storeId; // 消息中可能包含旧的storeId
                            
                            storeProductCacheService.updateStoreProductCache(storeProduct);
                            
                            // 如果店铺ID发生变化，需要更新关系缓存
                            if (oldStoreId != null && newStoreId != null && !oldStoreId.equals(newStoreId)) {
                                // 从旧店铺的关系缓存中移除
                                storeProductCacheService.removeProductIdFromStore(oldStoreId, productId);
                                // 添加到新店铺的关系缓存中
                                storeProductCacheService.addProductIdToStore(newStoreId, productId);
                                // 删除两个店铺的商品列表缓存
                                storeProductCacheService.evictStoreProductListCache(oldStoreId);
                                storeProductCacheService.evictStoreProductListCache(newStoreId);
                                log.info("商品店铺变更，更新关系缓存（UPDATE），旧店铺ID: {}, 新店铺ID: {}, 商品ID: {}", 
                                        oldStoreId, newStoreId, productId);
                            } else {
                                // 店铺ID未变化，只删除列表缓存
                                storeProductCacheService.evictStoreProductListCache(newStoreId != null ? newStoreId : storeId);
                            }
                            
                            log.info("店铺商品缓存更新成功（UPDATE），店铺ID: {}, 商品ID: {}", 
                                    newStoreId != null ? newStoreId : storeId, productId);
                        } else {
                            log.warn("店铺商品不存在，删除缓存，商品ID: {}", productId);
                            storeProductCacheService.evictStoreProductCache(productId);
                            // 从关系缓存中移除
                            if (storeId != null) {
                                storeProductCacheService.removeProductIdFromStore(storeId, productId);
                            }
                        }
                    } else {
                        log.warn("UPDATE操作缺少productId");
                    }
                    break;
                }
                    
                case DELETE: {
                    // DELETE操作：删除缓存
                    if (productId != null) {
                        storeProductCacheService.evictStoreProductCache(productId);
                        // 从店铺关系缓存中移除商品ID
                        storeProductCacheService.removeProductIdFromStore(storeId, productId);
                        // 删除店铺商品列表缓存（因为删除了商品）
                        storeProductCacheService.evictStoreProductListCache(storeId);
                        log.info("店铺商品缓存删除成功（DELETE），店铺ID: {}, 商品ID: {}", storeId, productId);
                    } else {
                        log.warn("DELETE操作缺少productId");
                    }
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
                    } else {
                        log.warn("REFRESH操作缺少productId");
                    }
                    break;
                }
                    
                default:
                    log.warn("未知的操作类型: {}, 店铺ID: {}, 商品ID: {}", operationType, storeId, productId);
            }
    }
}

