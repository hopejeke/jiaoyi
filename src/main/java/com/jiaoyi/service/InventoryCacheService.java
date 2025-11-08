package com.jiaoyi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.entity.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 库存缓存服务
 * 提供库存数据的Redis缓存功能
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // 缓存键前缀
    private static final String INVENTORY_KEY_PREFIX = "inventory:product:";
    private static final String LOW_STOCK_KEY = "inventory:low_stock";
    private static final String INVENTORY_LIST_KEY = "inventory:all";
    
    // 缓存过期时间
    private static final Duration CACHE_EXPIRE_TIME = Duration.ofMinutes(30);
    private static final Duration LOW_STOCK_CACHE_EXPIRE_TIME = Duration.ofMinutes(10);
    
    /**
     * 根据商品ID获取库存信息（优先从缓存）
     */
    public Optional<Inventory> getInventoryByProductId(Long productId) {
        String cacheKey = INVENTORY_KEY_PREFIX + productId;
        
        try {
            // 先从缓存获取
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取库存信息，商品ID: {}", productId);
                Inventory inventory = objectMapper.readValue(cachedData, Inventory.class);
                return Optional.of(inventory);
            }
            
            log.debug("缓存未命中，商品ID: {}", productId);
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化库存信息失败，商品ID: {}", productId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 缓存库存信息
     */
    public void cacheInventory(Inventory inventory) {
        if (inventory == null || inventory.getProductId() == null) {
            return;
        }
        
        String cacheKey = INVENTORY_KEY_PREFIX + inventory.getProductId();
        
        try {
            String jsonData = objectMapper.writeValueAsString(inventory);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_EXPIRE_TIME);
            log.debug("缓存库存信息成功，商品ID: {}", inventory.getProductId());
            
        } catch (JsonProcessingException e) {
            log.error("序列化库存信息失败，商品ID: {}", inventory.getProductId(), e);
        }
    }
    
    /**
     * 更新缓存中的库存信息
     */
    public void updateInventoryCache(Inventory inventory) {
        if (inventory == null || inventory.getProductId() == null) {
            return;
        }
        
        String cacheKey = INVENTORY_KEY_PREFIX + inventory.getProductId();
        
        try {
            String jsonData = objectMapper.writeValueAsString(inventory);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_EXPIRE_TIME);
            log.debug("更新库存缓存成功，商品ID: {}", inventory.getProductId());
            
        } catch (JsonProcessingException e) {
            log.error("更新库存缓存失败，商品ID: {}", inventory.getProductId(), e);
        }
    }
    
    /**
     * 删除缓存中的库存信息
     */
    public void evictInventoryCache(Long productId) {
        if (productId == null) {
            return;
        }
        
        String cacheKey = INVENTORY_KEY_PREFIX + productId;
        redisTemplate.delete(cacheKey);
        log.debug("删除库存缓存，商品ID: {}", productId);
    }
    
    /**
     * 批量删除库存缓存
     */
    public void evictInventoryCacheBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        
        for (Long productId : productIds) {
            evictInventoryCache(productId);
        }
        
        log.debug("批量删除库存缓存，商品数量: {}", productIds.size());
    }
    
    /**
     * 缓存库存不足商品列表
     */
    public void cacheLowStockItems(List<Inventory> lowStockItems) {
        if (lowStockItems == null || lowStockItems.isEmpty()) {
            return;
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(lowStockItems);
            redisTemplate.opsForValue().set(LOW_STOCK_KEY, jsonData, LOW_STOCK_CACHE_EXPIRE_TIME);
            log.debug("缓存库存不足商品列表成功，数量: {}", lowStockItems.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化库存不足商品列表失败", e);
        }
    }
    
    /**
     * 获取库存不足商品列表（优先从缓存）
     */
    public Optional<List<Inventory>> getLowStockItems() {
        try {
            String cachedData = redisTemplate.opsForValue().get(LOW_STOCK_KEY);
            if (cachedData != null) {
                log.debug("从缓存获取库存不足商品列表");
                List<Inventory> lowStockItems = objectMapper.readValue(cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Inventory.class));
                return Optional.of(lowStockItems);
            }
            
            log.debug("库存不足商品列表缓存未命中");
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化库存不足商品列表失败", e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除库存不足商品列表缓存
     */
    public void evictLowStockItemsCache() {
        redisTemplate.delete(LOW_STOCK_KEY);
        log.debug("删除库存不足商品列表缓存");
    }
    
    /**
     * 缓存所有库存信息
     */
    public void cacheAllInventories(List<Inventory> inventories) {
        if (inventories == null || inventories.isEmpty()) {
            return;
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(inventories);
            redisTemplate.opsForValue().set(INVENTORY_LIST_KEY, jsonData, CACHE_EXPIRE_TIME);
            log.debug("缓存所有库存信息成功，数量: {}", inventories.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化所有库存信息失败", e);
        }
    }
    
    /**
     * 获取所有库存信息（优先从缓存）
     */
    public Optional<List<Inventory>> getAllInventories() {
        try {
            String cachedData = redisTemplate.opsForValue().get(INVENTORY_LIST_KEY);
            if (cachedData != null) {
                log.debug("从缓存获取所有库存信息");
                List<Inventory> inventories = objectMapper.readValue(cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Inventory.class));
                return Optional.of(inventories);
            }
            
            log.debug("所有库存信息缓存未命中");
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化所有库存信息失败", e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除所有库存信息缓存
     */
    public void evictAllInventoriesCache() {
        redisTemplate.delete(INVENTORY_LIST_KEY);
        log.debug("删除所有库存信息缓存");
    }
    
    /**
     * 清空所有库存相关缓存
     */
    public void clearAllInventoryCache() {
        // 删除所有以inventory:开头的key
        redisTemplate.delete(redisTemplate.keys("inventory:*"));
        log.info("清空所有库存相关缓存");
    }
    
    /**
     * 检查缓存是否存在
     */
    public boolean hasInventoryCache(Long productId) {
        if (productId == null) {
            return false;
        }
        
        String cacheKey = INVENTORY_KEY_PREFIX + productId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }
    
    /**
     * 获取缓存剩余过期时间（秒）
     */
    public Long getCacheExpireTime(Long productId) {
        if (productId == null) {
            return -1L;
        }
        
        String cacheKey = INVENTORY_KEY_PREFIX + productId;
        return redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
    }
}


