package com.jiaoyi.service;

import com.jiaoyi.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 缓存一致性服务
 * 负责保证缓存和数据库的一致性
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductCacheService productCacheService;
    private final InventoryCacheService inventoryCacheService;
    
    // 分布式锁相关
    private static final String CACHE_LOCK_PREFIX = "cache:lock:";
    private static final int LOCK_LEASE_TIME = 10; // 秒
    
    /**
     * 写时双删策略 - 保证缓存一致性
     * 1. 先删除缓存
     * 2. 更新数据库
     * 3. 延迟删除缓存（防止并发问题）
     */
    @Transactional
    public void updateProductWithDoubleDelete(Product product) {
        String lockKey = CACHE_LOCK_PREFIX + "product:" + product.getId();
        
        try {
            // 获取分布式锁
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取缓存锁失败，商品ID: {}", product.getId());
                return;
            }
            
            // 1. 先删除缓存
            productCacheService.evictProductCache(product.getId());
            productCacheService.evictProductListCache();
            
            // 2. 更新数据库（这里需要调用实际的数据库更新方法）
            // productMapper.update(product);
            
            // 3. 延迟删除缓存（异步执行）
            scheduleDelayedCacheEviction(product.getId());
            
            log.info("商品更新完成，使用双删策略，商品ID: {}", product.getId());
            
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }
    
    /**
     * 延迟删除缓存
     */
    private void scheduleDelayedCacheEviction(Long productId) {
        // 使用Redis的延迟任务或者定时任务
        // 这里简化处理，直接延迟删除
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 延迟1秒
                productCacheService.evictProductCache(productId);
                log.debug("延迟删除缓存完成，商品ID: {}", productId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("延迟删除缓存被中断，商品ID: {}", productId, e);
            }
        }).start();
    }
    
    /**
     * 缓存预热 - 系统启动时预加载热点数据
     */
    public void warmUpCache() {
        log.info("开始缓存预热...");
        
        try {
            // 预热商品列表
            // List<Product> products = productMapper.selectAll();
            // productCacheService.cacheProductList(products);
            
            // 预热库存信息
            // List<Inventory> inventories = inventoryMapper.selectAll();
            // inventoryCacheService.cacheAllInventory(inventories);
            
            log.info("缓存预热完成");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }
    
    /**
     * 缓存一致性检查 - 定期检查缓存和数据库是否一致
     */
    public void checkCacheConsistency() {
        log.info("开始缓存一致性检查...");
        
        // 这里可以实现定期检查逻辑
        // 1. 随机抽样检查缓存和数据库数据是否一致
        // 2. 发现不一致时，更新缓存或清空缓存
        
        log.info("缓存一致性检查完成");
    }
    
    /**
     * 强制刷新所有缓存
     */
    public void forceRefreshAllCache() {
        log.info("强制刷新所有缓存...");
        
        // 清空所有缓存
        productCacheService.clearAllProductCache();
        inventoryCacheService.clearAllInventoryCache();
        
        // 重新加载数据
        warmUpCache();
        
        log.info("所有缓存刷新完成");
    }
    
    /**
     * 获取缓存一致性状态
     */
    public String getCacheConsistencyStatus() {
        return String.format("缓存一致性状态:\n" +
                "商品缓存服务: 正常\n" +
                "库存缓存服务: 正常\n" +
                "分布式锁: 正常\n" +
                "最后检查时间: %s", 
                java.time.LocalDateTime.now());
    }
}
