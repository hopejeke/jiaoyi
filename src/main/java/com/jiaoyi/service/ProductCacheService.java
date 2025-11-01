package com.jiaoyi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 商品缓存服务
 * 提供商品数据的Redis缓存功能
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // 缓存键前缀
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY = "product:all";
    private static final String PRODUCT_CATEGORY_KEY_PREFIX = "product:category:";
    private static final String PRODUCT_STATUS_KEY_PREFIX = "product:status:";
    private static final String PRODUCT_VERSION_KEY = "product:version:";
    
    // 缓存过期时间
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofMinutes(30); // 单个商品缓存30分钟
    private static final Duration PRODUCT_LIST_CACHE_TTL = Duration.ofMinutes(10); // 商品列表缓存10分钟
    
    /**
     * 根据商品ID获取商品信息（优先从缓存）
     */
    public Optional<Product> getProductById(Long productId) {
        String cacheKey = PRODUCT_KEY_PREFIX + productId;
        
        try {
            // 先从缓存获取
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取商品信息，商品ID: {}", productId);
                Product product = objectMapper.readValue(cachedData, Product.class);
                return Optional.of(product);
            }
            
            log.debug("缓存未命中，商品ID: {}", productId);
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化商品信息失败，商品ID: {}", productId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 缓存商品信息
     */
    public void cacheProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        
        String cacheKey = PRODUCT_KEY_PREFIX + product.getId();
        
        try {
            String jsonData = objectMapper.writeValueAsString(product);
            redisTemplate.opsForValue().set(cacheKey, jsonData, PRODUCT_CACHE_TTL);
            log.debug("缓存商品信息成功，商品ID: {}", product.getId());
            
        } catch (JsonProcessingException e) {
            log.error("序列化商品信息失败，商品ID: {}", product.getId(), e);
        }
    }
    
    /**
     * 更新缓存中的商品信息
     */
    public void updateProductCache(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        
        String cacheKey = PRODUCT_KEY_PREFIX + product.getId();
        
        try {
            String jsonData = objectMapper.writeValueAsString(product);
            redisTemplate.opsForValue().set(cacheKey, jsonData, PRODUCT_CACHE_TTL);
            log.debug("更新商品缓存成功，商品ID: {}", product.getId());
            
        } catch (JsonProcessingException e) {
            log.error("更新商品缓存失败，商品ID: {}", product.getId(), e);
        }
    }
    
    /**
     * 删除缓存中的商品信息
     */
    public void evictProductCache(Long productId) {
        if (productId == null) {
            return;
        }
        
        String cacheKey = PRODUCT_KEY_PREFIX + productId;
        redisTemplate.delete(cacheKey);
        log.debug("删除商品缓存，商品ID: {}", productId);
    }
    
    /**
     * 批量删除商品缓存
     */
    public void evictProductCacheBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        
        for (Long productId : productIds) {
            evictProductCache(productId);
        }
        
        log.debug("批量删除商品缓存，商品数量: {}", productIds.size());
    }
    
    /**
     * 缓存商品列表
     */
    public void cacheProductList(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(PRODUCT_LIST_KEY, jsonData, PRODUCT_LIST_CACHE_TTL);
            log.debug("缓存商品列表成功，数量: {}", products.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化商品列表失败", e);
        }
    }
    
    /**
     * 获取缓存的商品列表
     */
    public Optional<List<Product>> getCachedProductList() {
        try {
            String cachedData = redisTemplate.opsForValue().get(PRODUCT_LIST_KEY);
            if (cachedData != null) {
                log.debug("从缓存获取商品列表");
                List<Product> products = objectMapper.readValue(cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Product.class));
                return Optional.of(products);
            }
            
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化商品列表失败", e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除商品列表缓存
     */
    public void evictProductListCache() {
        redisTemplate.delete(PRODUCT_LIST_KEY);
        log.debug("删除商品列表缓存");
    }
    
    /**
     * 根据分类缓存商品列表
     */
    public void cacheProductsByCategory(String category, List<Product> products) {
        if (category == null || products == null || products.isEmpty()) {
            return;
        }
        
        String cacheKey = PRODUCT_CATEGORY_KEY_PREFIX + category;
        
        try {
            String jsonData = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(cacheKey, jsonData, PRODUCT_LIST_CACHE_TTL);
            log.debug("缓存分类商品列表成功，分类: {}, 数量: {}", category, products.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化分类商品列表失败，分类: {}", category, e);
        }
    }
    
    /**
     * 获取缓存的分类商品列表
     */
    public Optional<List<Product>> getCachedProductsByCategory(String category) {
        if (category == null) {
            return Optional.empty();
        }
        
        String cacheKey = PRODUCT_CATEGORY_KEY_PREFIX + category;
        
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取分类商品列表，分类: {}", category);
                List<Product> products = objectMapper.readValue(cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Product.class));
                return Optional.of(products);
            }
            
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化分类商品列表失败，分类: {}", category, e);
            return Optional.empty();
        }
    }
    
    /**
     * 根据状态缓存商品列表
     */
    public void cacheProductsByStatus(String status, List<Product> products) {
        if (status == null || products == null || products.isEmpty()) {
            return;
        }
        
        String cacheKey = PRODUCT_STATUS_KEY_PREFIX + status;
        
        try {
            String jsonData = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(cacheKey, jsonData, PRODUCT_LIST_CACHE_TTL);
            log.debug("缓存状态商品列表成功，状态: {}, 数量: {}", status, products.size());
            
        } catch (JsonProcessingException e) {
            log.error("序列化状态商品列表失败，状态: {}", status, e);
        }
    }
    
    /**
     * 获取缓存的状态商品列表
     */
    public Optional<List<Product>> getCachedProductsByStatus(String status) {
        if (status == null) {
            return Optional.empty();
        }
        
        String cacheKey = PRODUCT_STATUS_KEY_PREFIX + status;
        
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取状态商品列表，状态: {}", status);
                List<Product> products = objectMapper.readValue(cachedData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Product.class));
                return Optional.of(products);
            }
            
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("反序列化状态商品列表失败，状态: {}", status, e);
            return Optional.empty();
        }
    }
    
    /**
     * 清空所有商品相关缓存
     */
    public void clearAllProductCache() {
        // 删除所有商品相关缓存键
        redisTemplate.delete(PRODUCT_LIST_KEY);
        
        // 删除所有单个商品缓存（这里简化处理，实际生产环境可能需要更精确的删除）
        log.info("清空所有商品缓存");
    }
    
    /**
     * 检查商品缓存是否存在
     */
    public boolean hasProductCache(Long productId) {
        if (productId == null) {
            return false;
        }
        
        String cacheKey = PRODUCT_KEY_PREFIX + productId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }
    
    /**
     * 获取商品缓存剩余时间（秒）
     */
    public Long getProductCacheExpireTime(Long productId) {
        if (productId == null) {
            return -1L;
        }
        
        String cacheKey = PRODUCT_KEY_PREFIX + productId;
        return redisTemplate.getExpire(cacheKey);
    }
    
    // ==================== 版本控制方法 ====================
    
    /**
     * 设置商品版本号
     */
    public void setProductVersion(Long productId, Long version) {
        if (productId == null || version == null) {
            return;
        }
        
        String versionKey = PRODUCT_VERSION_KEY + productId;
        redisTemplate.opsForValue().set(versionKey, version.toString(), PRODUCT_CACHE_TTL);
        log.debug("设置商品版本号，商品ID: {}, 版本: {}", productId, version);
    }
    
    /**
     * 获取商品版本号
     */
    public Long getProductVersion(Long productId) {
        if (productId == null) {
            return null;
        }
        
        String versionKey = PRODUCT_VERSION_KEY + productId;
        String versionStr = redisTemplate.opsForValue().get(versionKey);
        
        if (versionStr != null) {
            try {
                return Long.parseLong(versionStr);
            } catch (NumberFormatException e) {
                log.warn("解析商品版本号失败，商品ID: {}, 版本字符串: {}", productId, versionStr);
            }
        }
        
        return null;
    }
    
    /**
     * 检查缓存版本是否有效
     */
    public boolean isCacheVersionValid(Long productId, Long expectedVersion) {
        if (productId == null || expectedVersion == null) {
            return false;
        }
        
        Long cachedVersion = getProductVersion(productId);
        return cachedVersion != null && cachedVersion.equals(expectedVersion);
    }
    
    /**
     * 带版本控制的缓存商品
     */
    public void cacheProductWithVersion(Product product, Long version) {
        if (product == null || product.getId() == null || version == null) {
            return;
        }
        
        // 缓存商品数据
        cacheProduct(product);
        
        // 设置版本号
        setProductVersion(product.getId(), version);
        
        log.debug("缓存商品及版本号，商品ID: {}, 版本: {}", product.getId(), version);
    }
    
    /**
     * 带版本控制的获取商品
     */
    public Optional<Product> getProductByIdWithVersion(Long productId, Long expectedVersion) {
        if (productId == null) {
            return Optional.empty();
        }
        
        // 检查版本是否有效
        if (expectedVersion != null && !isCacheVersionValid(productId, expectedVersion)) {
            log.debug("缓存版本无效，商品ID: {}, 期望版本: {}", productId, expectedVersion);
            return Optional.empty();
        }
        
        return getProductById(productId);
    }
}
