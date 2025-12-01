package com.jiaoyi.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.mapper.primary.StoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 店铺商品缓存服务
 * 提供店铺商品数据的Redis缓存功能
 */
@Slf4j
@Service
public class StoreProductCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StoreMapper storeMapper;
    
    // Lua脚本：根据版本号比较并更新缓存（原子操作）
    // 使用Redis Hash结构：{data: "商品JSON数据", ver: "版本号"}
    private static final String CACHE_UPDATE_LUA_SCRIPT = 
        "local cacheKey = KEYS[1]\n" +
        "local newData = ARGV[1]\n" +
        "local newVersion = ARGV[2]\n" +
        "local expireSeconds = tonumber(ARGV[3])\n" +
        "\n" +
        "-- 从Hash中获取现有的版本号（ver字段）\n" +
        "local existingVer = redis.call('HGET', cacheKey, 'ver')\n" +
        "\n" +
        "-- 如果存在现有版本号，比较版本号（转换为数字比较）\n" +
        "if existingVer then\n" +
        "    local existingVersion = tonumber(existingVer)\n" +
        "    local newVer = tonumber(newVersion)\n" +
        "    \n" +
        "    -- 如果新版本不高于现有版本，不更新（防止脏写）\n" +
        "    if newVer <= existingVersion then\n" +
        "        return 0  -- 返回0表示未更新（因为版本号不够新）\n" +
        "    end\n" +
        "end\n" +
        "\n" +
        "-- 版本号更新，执行更新操作（使用Hash结构）\n" +
        "-- 分别设置data和ver字段，避免多参数HSET的问题\n" +
        "redis.call('HSET', cacheKey, 'data', newData)\n" +
        "redis.call('HSET', cacheKey, 'ver', newVersion)\n" +
        "redis.call('EXPIRE', cacheKey, expireSeconds)\n" +
        "\n" +
        "return 1  -- 返回1表示更新成功\n";
    
    // Lua脚本：根据版本号比较并更新店铺商品ID列表缓存（Hash结构）
    // 使用Redis Hash结构：shop:{storeId}:products -> {data: "[123,456,789]", ver: "版本号"}
    private static final String STORE_PRODUCTS_LIST_UPDATE_LUA_SCRIPT = 
        "local cacheKey = KEYS[1]\n" +
        "local newData = ARGV[1]\n" +
        "local newVersion = ARGV[2]\n" +
        "local expireSeconds = tonumber(ARGV[3])\n" +
        "\n" +
        "-- 从Hash中获取现有的版本号（ver字段）\n" +
        "local existingVer = redis.call('HGET', cacheKey, 'ver')\n" +
        "\n" +
        "-- 如果存在现有版本号，比较版本号（转换为数字比较）\n" +
        "if existingVer then\n" +
        "    local existingVersion = tonumber(existingVer)\n" +
        "    local newVer = tonumber(newVersion)\n" +
        "    \n" +
        "    -- 如果新版本不高于现有版本，不更新（防止脏写）\n" +
        "    if newVer <= existingVersion then\n" +
        "        return 0  -- 返回0表示未更新（因为版本号不够新）\n" +
        "    end\n" +
        "end\n" +
        "\n" +
        "-- 版本号更新，执行更新操作（使用Hash结构）\n" +
        "-- 分别设置data和ver字段，避免多参数HSET的问题\n" +
        "redis.call('HSET', cacheKey, 'data', newData)\n" +
        "redis.call('HSET', cacheKey, 'ver', newVersion)\n" +
        "redis.call('EXPIRE', cacheKey, expireSeconds)\n" +
        "\n" +
        "return 1  -- 返回1表示更新成功\n";
    
    private final DefaultRedisScript<Long> cacheUpdateScript;
    private final DefaultRedisScript<Long> storeProductsListUpdateScript;
    
    public StoreProductCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, 
                                    StoreMapper storeMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.storeMapper = storeMapper;
        
        // 初始化 Lua 脚本
        this.cacheUpdateScript = new DefaultRedisScript<>();
        this.cacheUpdateScript.setScriptText(CACHE_UPDATE_LUA_SCRIPT);
        this.cacheUpdateScript.setResultType(Long.class);
        
        // 初始化店铺商品列表更新的 Lua 脚本
        this.storeProductsListUpdateScript = new DefaultRedisScript<>();
        this.storeProductsListUpdateScript.setScriptText(STORE_PRODUCTS_LIST_UPDATE_LUA_SCRIPT);
        this.storeProductsListUpdateScript.setResultType(Long.class);
    }
    
    // 缓存键前缀
    private static final String STORE_PRODUCT_KEY_PREFIX = "product:"; // 使用Hash结构：product:{productId} -> {data: "...", ver: "..."}
    private static final String STORE_PRODUCT_IDS_KEY_PREFIX = "shop:"; // 店铺商品ID关系缓存：shop:{storeId}:products -> {data: "[...]", ver: "..."}
    private static final String TEMP_ID_MAPPING_KEY_PREFIX = "product:temp:"; // 临时ID映射：product:temp:{tempId} -> {productId}
    
    // 缓存过期时间
    private static final Duration CACHE_EXPIRE_TIME = Duration.ofMinutes(30);
    private static final Duration RELATION_CACHE_EXPIRE_TIME = Duration.ofSeconds(1800); // 关系缓存过期时间：1800秒（30分钟）
    private static final Duration TEMP_ID_MAPPING_EXPIRE_TIME = Duration.ofMinutes(5); // 临时ID映射过期时间（5分钟足够消费者消费）
    
    /**
     * 根据商品ID获取店铺商品信息（优先从缓存）
     * 从Redis Hash结构中读取：HGETALL product:{productId} -> {data: "...", ver: "..."}
     */
    public Optional<StoreProduct> getStoreProductById(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + productId;
        
        try {
            // 从Hash中获取data字段
            String cachedData = (String) redisTemplate.opsForHash().get(cacheKey, "data");
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
     * 缓存店铺商品信息（使用Lua脚本根据版本号原子更新）
     * 使用Redis Hash结构：HGETALL product:{productId} -> {data: "商品JSON数据", ver: "版本号"}
     * 
     * 注意：此方法会从商品的version字段获取版本号，如果version为null则使用update_time
     */
    public void cacheStoreProduct(StoreProduct storeProduct) {
        if (storeProduct == null || storeProduct.getId() == null) {
            return;
        }
        
        // 优先使用version字段，如果为null则使用update_time（兼容旧逻辑）
        Long version = storeProduct.getVersion();
        if (version == null && storeProduct.getUpdateTime() != null) {
            version = storeProduct.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        
        if (version == null) {
            version = 0L;
        }
        
        cacheStoreProductWithVersion(storeProduct, version);
    }
    
    /**
     * 使用指定的版本号缓存店铺商品信息（使用Lua脚本根据版本号原子更新）
     * 使用Redis Hash结构：HGETALL product:{productId} -> {data: "商品JSON数据", ver: "版本号"}
     * 
     * @param storeProduct 商品信息
     * @param version 版本号（从数据库读取，用于版本控制）
     */
    public void cacheStoreProductWithVersion(StoreProduct storeProduct, Long version) {
        if (storeProduct == null || storeProduct.getId() == null || version == null) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + storeProduct.getId();
        
        try {
            // 序列化商品数据
            String jsonData = objectMapper.writeValueAsString(storeProduct);
            
            // 使用 Lua 脚本原子性地比较版本号并更新缓存（Hash结构）
            List<String> keys = Collections.singletonList(cacheKey);
            Long result = redisTemplate.execute(
                    cacheUpdateScript, 
                    keys, 
                    jsonData,
                    String.valueOf(version),
                    String.valueOf(CACHE_EXPIRE_TIME.getSeconds())
            );
            
            if (result != null && result == 1) {
                log.debug("缓存店铺商品信息成功（Lua脚本更新Hash），商品ID: {}, 版本号: {}", 
                        storeProduct.getId(), version);
            } else if (result != null && result == 0) {
                log.debug("缓存中的商品版本号不够新，跳过更新（Lua脚本），商品ID: {}, 版本号: {}", 
                        storeProduct.getId(), version);
            } else {
                log.warn("Lua脚本返回未知结果，商品ID: {}, 返回值: {}", storeProduct.getId(), result);
            }
            
        } catch (JsonProcessingException e) {
            log.error("序列化店铺商品信息失败，商品ID: {}", storeProduct.getId(), e);
        } catch (Exception e) {
            log.error("缓存店铺商品信息失败，商品ID: {}", storeProduct.getId(), e);
        }
    }
    
    /**

    
    /**
     * 删除缓存中的店铺商品信息（删除整个Hash）
     * 注意：直接删除，不校验版本号，适用于明确需要删除缓存的场景
     */
    public void evictStoreProductCache(Long productId) {
        if (productId == null) {
            return;
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + productId;
        redisTemplate.delete(cacheKey);
        log.debug("删除店铺商品缓存（Hash），商品ID: {}", productId);
    }
    
    /**
     * 根据版本号删除缓存（使用乐观锁机制）
     * 只有当缓存中的版本号 <= 指定的版本号时，才删除缓存
     * 这样可以防止删除操作覆盖掉更高版本的更新
     * 
     * @param productId 商品ID
     * @param version 删除操作的版本号
     * @return true表示删除成功，false表示版本号不匹配或缓存不存在
     */
    public boolean evictStoreProductCacheWithVersion(Long productId, Long version) {
        if (productId == null || version == null) {
            return false;
        }
        
        String cacheKey = STORE_PRODUCT_KEY_PREFIX + productId;
        
        // Lua脚本：检查版本号，只有版本号匹配或更旧时才删除
        String deleteWithVersionScript = 
            "local cacheKey = KEYS[1]\n" +
            "local deleteVersion = tonumber(ARGV[1])\n" +
            "\n" +
            "-- 从Hash中获取现有的版本号（ver字段）\n" +
            "local existingVer = redis.call('HGET', cacheKey, 'ver')\n" +
            "\n" +
            "-- 如果缓存不存在，直接返回成功（已经删除）\n" +
            "if not existingVer then\n" +
            "    return 1\n" +
            "end\n" +
            "\n" +
            "-- 如果存在现有版本号，比较版本号\n" +
            "local existingVersion = tonumber(existingVer)\n" +
            "\n" +
            "-- 如果删除操作的版本号 >= 现有版本号，才删除缓存（防止覆盖更高版本的更新）\n" +
            "if deleteVersion >= existingVersion then\n" +
            "    redis.call('DEL', cacheKey)\n" +
            "    return 1  -- 返回1表示删除成功\n" +
            "else\n" +
            "    return 0  -- 返回0表示版本号不匹配（缓存中有更新版本）\n" +
            "end\n";
        
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(deleteWithVersionScript, Long.class);
            List<String> keys = Collections.singletonList(cacheKey);
            Long result = redisTemplate.execute(script, keys, String.valueOf(version));
            
            if (result != null && result == 1) {
                log.debug("删除店铺商品缓存成功（版本号控制），商品ID: {}, 版本号: {}", productId, version);
                return true;
            } else {
                log.debug("删除店铺商品缓存跳过（版本号不匹配，缓存中有更新版本），商品ID: {}, 删除版本号: {}", productId, version);
                return false;
            }
        } catch (Exception e) {
            log.error("删除店铺商品缓存失败（版本号控制），商品ID: {}, 版本号: {}", productId, version, e);
            return false;
        }
    }
    
    


    

    /**
     * 使用Lua脚本原子性地更新店铺商品ID列表缓存（带版本号，防止脏写）
     * 
     * @param storeId 店铺ID
     * @param productIds 新的商品ID列表
     * @param version 版本号（数据库中的product_list_version）
     */
    public void updateStoreProductIdsCacheWithVersion(Long storeId, Set<Long> productIds, long version) {
        if (storeId == null) {
            return;
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId + ":products";
        
        try {
            // 将商品ID列表转换为JSON数组字符串
            List<Long> sortedIds = new ArrayList<>(productIds);
            Collections.sort(sortedIds); // 排序以确保一致性
            String jsonData = objectMapper.writeValueAsString(sortedIds);
            
            // 使用 Lua 脚本原子性地比较版本号并更新缓存
            List<String> keys = Collections.singletonList(relationKey);
            Long result = redisTemplate.execute(
                    storeProductsListUpdateScript, 
                    keys, 
                    jsonData,
                    String.valueOf(version),
                    String.valueOf(RELATION_CACHE_EXPIRE_TIME.getSeconds())
            );
            
            if (result != null && result == 1) {
                log.debug("更新店铺商品ID列表缓存成功（Lua脚本），店铺ID: {}, 商品数量: {}, 版本号: {}", 
                        storeId, productIds.size(), version);
            } else if (result != null && result == 0) {
                log.debug("店铺商品ID列表缓存版本号不够新，跳过更新，店铺ID: {}, 版本号: {}", storeId, version);
            } else {
                log.warn("Lua脚本返回未知结果，店铺ID: {}, 返回值: {}", storeId, result);
            }
            
        } catch (JsonProcessingException e) {
            log.error("序列化店铺商品ID列表失败，店铺ID: {}", storeId, e);
        } catch (Exception e) {
            log.error("更新店铺商品ID列表缓存失败，店铺ID: {}", storeId, e);
        }
    }
    

    
    /**
     * 从店铺的商品ID集合中移除商品ID（关系缓存）
     * 使用版本号和Lua脚本防止脏写
     */
    public void removeProductIdFromStore(Long storeId, Long productId) {
        if (storeId == null || productId == null) {
            return;
        }
        
        // 获取当前的商品ID列表
        Set<Long> currentIds = getStoreProductIds(storeId);
        
        // 如果商品ID不存在，直接返回
        if (!currentIds.contains(productId)) {
            log.debug("商品ID不在关系缓存中，店铺ID: {}, 商品ID: {}", storeId, productId);
            return;
        }
        
        // 移除商品ID
        currentIds.remove(productId);
        
        // 从数据库获取店铺的商品列表版本号
        Long version = storeMapper.getProductListVersion(storeId);
        if (version == null) {
            version = 0L; // 如果查询失败，使用0作为默认值
            log.warn("无法获取店铺商品列表版本号，使用默认值0，店铺ID: {}", storeId);
        }
        updateStoreProductIdsCacheWithVersion(storeId, currentIds, version);
        
        log.debug("从店铺关系缓存移除商品ID，店铺ID: {}, 商品ID: {}", storeId, productId);
    }
    
    /**
     * 获取店铺的所有商品ID（从关系缓存）
     * 从Hash结构读取：shop:{storeId}:products -> {data: "[123,456,789]", ver: "..."}
     */
    public Set<Long> getStoreProductIds(Long storeId) {
        if (storeId == null) {
            return Collections.emptySet();
        }
        
        String relationKey = STORE_PRODUCT_IDS_KEY_PREFIX + storeId + ":products";
        
        try {
            // 从Hash中获取data字段（JSON数组）
            String cachedData = (String) redisTemplate.opsForHash().get(relationKey, "data");
            if (cachedData == null || cachedData.isEmpty()) {
                return Collections.emptySet();
            }
            
            // 反序列化JSON数组
            List<Long> productIds = objectMapper.readValue(
                cachedData, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class)
            );
            
            return new HashSet<>(productIds);
            
        } catch (JsonProcessingException e) {
            log.error("反序列化店铺商品ID列表失败，店铺ID: {}", storeId, e);
            return Collections.emptySet();
        }
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
     * 使用版本号和Lua脚本防止脏写
     */
    public void initStoreProductIdsCache(Long storeId, List<StoreProduct> storeProducts) {
        if (storeId == null) {
            return;
        }

        // 从数据库获取店铺的商品列表版本号，通过Lua脚本更新缓存
        // Lua脚本会检查版本号，如果缓存中的版本号更新，则不会覆盖
        Long version = storeMapper.getProductListVersion(storeId);
        if (version == null) {
            version = 0L; // 如果查询失败，使用0作为默认值
            log.warn("无法获取店铺商品列表版本号，使用默认值0，店铺ID: {}", storeId);
        }
        // 提取商品ID列表
        Set<Long> productIds = storeProducts.stream()
                .map(StoreProduct::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        updateStoreProductIdsCacheWithVersion(storeId, productIds, version);
        
        log.debug("初始化店铺商品ID关系缓存，店铺ID: {}, 商品数量: {}, 版本号: {}", 
                storeId, productIds.size(), version);
    }
    

    

}

