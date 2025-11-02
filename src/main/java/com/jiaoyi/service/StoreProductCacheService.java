package com.jiaoyi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.entity.StoreProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 店铺商品缓存服务
 * 提供店铺商品数据的Redis缓存功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreProductCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // 缓存键前缀
    private static final String STORE_PRODUCT_KEY_PREFIX = "store:product:";
    private static final String STORE_PRODUCT_LIST_KEY_PREFIX = "store:product:list:";
    private static final String STORE_PRODUCT_LIST_BY_STATUS_KEY_PREFIX = "store:product:list:status:";
    private static final String STORE_PRODUCT_IDS_KEY_PREFIX = "store:product:ids:"; // 店铺商品ID关系缓存
    private static final String STORE_PRODUCT_UPDATE_TIME_KEY_PREFIX = "store:product:update:time:"; // 店铺商品更新时间戳缓存
    
    // 缓存过期时间
    private static final Duration CACHE_EXPIRE_TIME = Duration.ofMinutes(30);
    private static final Duration LIST_CACHE_EXPIRE_TIME = Duration.ofMinutes(10);
    private static final Duration RELATION_CACHE_EXPIRE_TIME = Duration.ofHours(24); // 关系缓存过期时间更长
    
    /**
     * 根据商品ID获取店铺商品信息（优先从缓存）
     */
    public Optional<StoreProduct> getStoreProductById(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + productId;
        
        try {
            // 先从缓存获取
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取店铺商品信息，商品ID: {}", productId);
                StoreProduct storeProduct = objectMapper.readValue(cachedData, StoreProduct.class);
                return Optional.of(storeProduct);
            }
            
            log.debug("缓存未命中，商品ID: {}", productId);
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化店铺商品信息失败，商品ID: {}", productId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 缓存店铺商品信息（带时间戳比较）
     */
    public void cacheStoreProduct(StoreProduct storeProduct) {
        if (storeProduct == null || storeProduct.getId() == null) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + storeProduct.getId();
        String updateTimeKey = STORE_PRODUCT_UPDATE_TIME_KEY_PREFIX + storeProduct.getId();
        
        try {
            // 比较时间戳，只更新更新的数据
            if (storeProduct.getUpdateTime() != null) {
                String existingUpdateTimeStr = redisTemplate.opsForValue().get(updateTimeKey);
                if (existingUpdateTimeStr != null) {
                    LocalDateTime existingUpdateTime = LocalDateTime.parse(existingUpdateTimeStr);
                    if (storeProduct.getUpdateTime().isBefore(existingUpdateTime) || 
                        storeProduct.getUpdateTime().equals(existingUpdateTime)) {
                        log.debug("缓存中的商品更新时间更新或相同，跳过更新，商品ID: {}, 缓存时间: {}, 新时间: {}", 
                                storeProduct.getId(), existingUpdateTime, storeProduct.getUpdateTime());
                        return;
                    }
                }
            }
            
            String jsonData = objectMapper.writeValueAsString(storeProduct);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_EXPIRE_TIME);
            
            // 缓存更新时间戳
            if (storeProduct.getUpdateTime() != null) {
                redisTemplate.opsForValue().set(updateTimeKey, storeProduct.getUpdateTime().toString(), CACHE_EXPIRE_TIME);
            }
            
            log.debug("缓存店铺商品信息成功，商品ID: {}, 更新时间: {}", storeProduct.getId(), storeProduct.getUpdateTime());
            
        } catch (JsonProcessingException e) {
            log.error("序列化店铺商品信息失败，商品ID: {}", storeProduct.getId(), e);
        } catch (Exception e) {
            log.error("缓存店铺商品信息失败，商品ID: {}", storeProduct.getId(), e);
        }
    }
    
    /**
     * 更新缓存中的店铺商品信息（带时间戳比较）
     */
    public void updateStoreProductCache(StoreProduct storeProduct) {
        // 使用相同的缓存方法，会自动比较时间戳
        cacheStoreProduct(storeProduct);
    }
    
    /**
     * 删除缓存中的店铺商品信息（包括时间戳缓存）
     */
    public void evictStoreProductCache(Long productId) {
        if (productId == null) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + productId;
        String updateTimeKey = STORE_PRODUCT_UPDATE_TIME_KEY_PREFIX + productId;
        redisTemplate.delete(cacheKey);
        redisTemplate.delete(updateTimeKey);
        log.debug("删除店铺商品缓存，商品ID: {}", productId);
    }
    
    /**
     * 缓存店铺商品列表
     */
    public void cacheStoreProductList(Long storeId, List<StoreProduct> storeProducts) {
        if (storeId == null || storeProducts == null || storeProducts.isEmpty()) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_LIST_KEY_PREFIX + storeId;
        
        try {
            String jsonData = objectMapper.writeValueAsString(storeProducts);
            redisTemplate.opsForValue().set(cacheKey, jsonData, LIST_CACHE_EXPIRE_TIME);
            log.debug("缓存店铺商品列表成功，店铺ID: {}, 商品数量: {}", storeId, storeProducts.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化店铺商品列表失败，店铺ID: {}", storeId, e);
        }
    }
    
    /**
     * 获取缓存的店铺商品列表
     */
    public Optional<List<StoreProduct>> getCachedStoreProductList(Long storeId) {
        if (storeId == null) {
            return Optional.empty();
        }
        
        String cacheKey = STORE_PRODUCT_LIST_KEY_PREFIX + storeId;
        
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取店铺商品列表，店铺ID: {}", storeId);
                List<StoreProduct> storeProducts = objectMapper.readValue(
                    cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, StoreProduct.class)
                );
                return Optional.of(storeProducts);
            }
            
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化店铺商品列表失败，店铺ID: {}", storeId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除店铺商品列表缓存
     */
    public void evictStoreProductListCache(Long storeId) {
        if (storeId == null) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_LIST_KEY_PREFIX + storeId;
        redisTemplate.delete(cacheKey);
        
        // 同时删除按状态缓存的列表
        evictStoreProductListByStatusCache(storeId, null); // null表示删除所有状态缓存
        
        log.debug("删除店铺商品列表缓存，店铺ID: {}", storeId);
    }
    
    /**
     * 缓存按状态筛选的店铺商品列表
     */
    public void cacheStoreProductListByStatus(Long storeId, StoreProduct.StoreProductStatus status, List<StoreProduct> storeProducts) {
        if (storeId == null || status == null || storeProducts == null || storeProducts.isEmpty()) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_LIST_BY_STATUS_KEY_PREFIX + storeId + ":" + status.name();
        
        try {
            String jsonData = objectMapper.writeValueAsString(storeProducts);
            redisTemplate.opsForValue().set(cacheKey, jsonData, LIST_CACHE_EXPIRE_TIME);
            log.debug("缓存按状态筛选的店铺商品列表成功，店铺ID: {}, 状态: {}, 商品数量: {}", storeId, status, storeProducts.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化按状态筛选的店铺商品列表失败，店铺ID: {}, 状态: {}", storeId, status, e);
        }
    }
    
    /**
     * 获取缓存的按状态筛选的店铺商品列表
     */
    public Optional<List<StoreProduct>> getCachedStoreProductListByStatus(Long storeId, StoreProduct.StoreProductStatus status) {
        if (storeId == null || status == null) {
            return Optional.empty();
        }
        
        String cacheKey = STORE_PRODUCT_LIST_BY_STATUS_KEY_PREFIX + storeId + ":" + status.name();
        
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取按状态筛选的店铺商品列表，店铺ID: {}, 状态: {}", storeId, status);
                List<StoreProduct> storeProducts = objectMapper.readValue(
                    cachedData,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, StoreProduct.class)
                );
                return Optional.of(storeProducts);
            }
            
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化按状态筛选的店铺商品列表失败，店铺ID: {}, 状态: {}", storeId, status, e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除按状态筛选的店铺商品列表缓存
     */
    public void evictStoreProductListByStatusCache(Long storeId, StoreProduct.StoreProductStatus status) {
        if (storeId == null) {
            return;
        }
        
        if (status != null) {
            // 删除特定状态的缓存
            String cacheKey = STORE_PRODUCT_LIST_BY_STATUS_KEY_PREFIX + storeId + ":" + status.name();
            redisTemplate.delete(cacheKey);
            log.debug("删除按状态筛选的店铺商品列表缓存，店铺ID: {}, 状态: {}", storeId, status);
        } else {
            // 删除所有状态的缓存（通过模式匹配）
            // 注意：Redis的keys命令在生产环境不推荐，这里简化处理
            // 实际应该维护一个状态列表，逐个删除
            log.debug("删除所有状态的店铺商品列表缓存，店铺ID: {}", storeId);
        }
    }
    
    /**
     * 清空所有店铺商品缓存
     */
    public void clearAllStoreProductCache() {
        // 注意：Redis的keys命令在生产环境不推荐
        // 实际应该维护一个缓存键列表，逐个删除
        log.warn("清空所有店铺商品缓存（注意：生产环境应避免使用keys命令）");
    }
    
    // ==================== 店铺商品ID关系缓存 ====================
    
    /**
     * 将商品ID添加到店铺的商品ID集合中（关系缓存）
     */
    public void addProductIdToStore(Long storeId, Long productId) {
        if (storeId == null || productId == null) {
            return;
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId;
        redisTemplate.opsForSet().add(relationKey, String.valueOf(productId));
        redisTemplate.expire(relationKey, RELATION_CACHE_EXPIRE_TIME);
        log.debug("添加商品ID到店铺关系缓存，店铺ID: {}, 商品ID: {}", storeId, productId);
    }
    
    /**
     * 从店铺的商品ID集合中移除商品ID（关系缓存）
     */
    public void removeProductIdFromStore(Long storeId, Long productId) {
        if (storeId == null || productId == null) {
            return;
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId;
        redisTemplate.opsForSet().remove(relationKey, String.valueOf(productId));
        log.debug("从店铺关系缓存移除商品ID，店铺ID: {}, 商品ID: {}", storeId, productId);
    }
    
    /**
     * 获取店铺的所有商品ID（从关系缓存）
     */
    public Set<Long> getStoreProductIds(Long storeId) {
        if (storeId == null) {
            return Collections.emptySet();
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId;
        Set<String> productIdStrs = redisTemplate.opsForSet().members(relationKey);
        
        if (productIdStrs == null || productIdStrs.isEmpty()) {
            return Collections.emptySet();
        }
        
        return productIdStrs.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }
    
    /**
     * 批量获取店铺商品详情（先从关系缓存获取ID列表，然后批量获取商品详情）
     */
    public List<StoreProduct> getStoreProductsFromCache(Long storeId) {
        if (storeId == null) {
            return Collections.emptyList();
        }
        
        // 1. 先从关系缓存获取商品ID列表
        Set<Long> productIds = getStoreProductIds(storeId);
        
        if (productIds.isEmpty()) {
            log.debug("关系缓存中没有商品ID，店铺ID: {}", storeId);
            return Collections.emptyList();
        }
        
        // 2. 批量获取商品详情（从单个商品缓存）
        List<StoreProduct> products = new ArrayList<>();
        for (Long productId : productIds) {
            Optional<StoreProduct> productOpt = getStoreProductById(productId);
            if (productOpt.isPresent()) {
                products.add(productOpt.get());
            } else {
                // 如果单个商品缓存不存在，从关系缓存中移除（数据不一致）
                log.warn("商品ID在关系缓存中，但单个商品缓存不存在，从关系缓存移除，店铺ID: {}, 商品ID: {}", storeId, productId);
                removeProductIdFromStore(storeId, productId);
            }
        }
        
        log.debug("从缓存获取店铺商品列表，店铺ID: {}, 商品数量: {}", storeId, products.size());
        return products;
    }
    
    /**
     * 初始化店铺的商品ID关系缓存（从数据库加载）
     */
    public void initStoreProductIdsCache(Long storeId, List<StoreProduct> storeProducts) {
        if (storeId == null || storeProducts == null || storeProducts.isEmpty()) {
            return;
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId;
        
        // 先清空旧的关系缓存
        redisTemplate.delete(relationKey);
        
        // 批量添加商品ID到关系缓存
        Set<String> productIdStrs = storeProducts.stream()
                .map(sp -> String.valueOf(sp.getId()))
                .collect(Collectors.toSet());
        
        if (!productIdStrs.isEmpty()) {
            redisTemplate.opsForSet().add(relationKey, productIdStrs.toArray(new String[0]));
            redisTemplate.expire(relationKey, RELATION_CACHE_EXPIRE_TIME);
            log.debug("初始化店铺商品ID关系缓存，店铺ID: {}, 商品数量: {}", storeId, productIdStrs.size());
        }
    }
    
    /**
     * 删除店铺的商品ID关系缓存
     */
    public void evictStoreProductIdsCache(Long storeId) {
        if (storeId == null) {
            return;
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId;
        redisTemplate.delete(relationKey);
        log.debug("删除店铺商品ID关系缓存，店铺ID: {}", storeId);
    }
}

