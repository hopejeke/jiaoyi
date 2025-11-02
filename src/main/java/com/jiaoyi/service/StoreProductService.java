package com.jiaoyi.service;

import com.jiaoyi.entity.StoreProduct;
import com.jiaoyi.mapper.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 店铺商品服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreProductService {

    private final StoreProductMapper storeProductMapper;
    private final InventoryService inventoryService;
    private final RedissonClient redissonClient;
    private final StoreProductCacheService storeProductCacheService;

    // 分布式锁key前缀（与消息更新缓存使用相同的key格式，便于协调）
    private static final String CREATE_PRODUCT_LOCK_PREFIX = "store:product:create:lock:";
    private static final String CACHE_LOCK_PREFIX = "store:product:cache:lock:"; // 与 StoreProductCacheUpdateMessageService 保持一致
    private static final int LOCK_WAIT_TIME = 3; // 等待锁的时间（秒）
    private static final int LOCK_LEASE_TIME = 30; // 锁持有时间（秒）
    private static final int INIT_CACHE_LOCK_LEASE_TIME = 60; // 初始化缓存的锁持有时间（秒）

    /**
     * 为店铺创建商品（内部方法，用于executeLocalTransaction中执行数据库操作）
     * 这个方法会在事务监听器的executeLocalTransaction中被调用
     *
     * @param storeId      店铺ID
     * @param storeProduct 店铺商品对象
     * @return 插入后的店铺商品对象（包含生成的ID）
     * @throws RuntimeException 如果插入失败
     */
    @Transactional
    public StoreProduct createStoreProductInternal(Long storeId, StoreProduct storeProduct) {
        log.info("执行数据库INSERT操作，店铺ID: {}, 商品名称: {}", storeId, storeProduct.getProductName());

        // 使用 storeId + productName 作为锁key，防止同一店铺同时创建同名商品
        String productName = storeProduct.getProductName();


        try {
            // 检查同一店铺中是否已存在同名商品
            Optional<StoreProduct> existing = storeProductMapper.selectByStoreIdAndProductName(storeId, productName);
            if (existing.isPresent()) {
                log.warn("店铺中已存在同名商品，店铺ID: {}, 商品名称: {}", storeId, productName);
                throw new RuntimeException("该店铺中已存在同名商品，请使用更新方法或使用不同的商品名称");
            }

            storeProduct.setStoreId(storeId);
            if (storeProduct.getStatus() == null) {
                storeProduct.setStatus(StoreProduct.StoreProductStatus.ACTIVE);
            }

            // 注意：create_time 和 update_time 由数据库自动生成，不需要手动设置
            storeProductMapper.insert(storeProduct);

            Long productId = storeProduct.getId();
            if (productId == null) {
                throw new RuntimeException("店铺商品创建失败：未获取到productId，可能数据库INSERT失败");
            }

            // INSERT后需要重新查询以获取数据库生成的时间戳
            Optional<StoreProduct> insertedProduct = storeProductMapper.selectById(productId);
            if (insertedProduct.isPresent()) {
                // 更新storeProduct对象的时间字段，以便后续使用update_time作为版本号
                storeProduct.setCreateTime(insertedProduct.get().getCreateTime());
                storeProduct.setUpdateTime(insertedProduct.get().getUpdateTime());
            }

            // 创建商品后，调用库存服务创建库存记录
            inventoryService.createInventory(storeId, productId, productName);
            log.info("库存记录创建成功，店铺ID: {}, 商品ID: {}", storeId, productId);

            log.info("数据库INSERT成功，店铺商品ID: {}", productId);
            return storeProduct;

        } catch (Exception e) {
            log.error("创建店铺商品失败，店铺ID: {}, 商品名称: {}", storeId, productName, e);
            throw new RuntimeException("创建店铺商品失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为店铺创建商品（公共方法，用于Controller直接调用，不使用事务消息）
     *
     * @deprecated 建议使用事务消息方式创建商品，通过 StoreProductMsgTransactionService
     */
    @Deprecated
    @Transactional
    public StoreProduct createStoreProduct(Long storeId, StoreProduct storeProduct) {
        return createStoreProductInternal(storeId, storeProduct);
    }

    /**
     * 更新店铺商品（内部方法，用于executeLocalTransaction中执行数据库操作）
     * 这个方法会在事务监听器的executeLocalTransaction中被调用
     *
     * @param storeProduct 店铺商品对象
     * @throws RuntimeException 如果更新失败或商品不存在
     */
    public void updateStoreProductInternal(StoreProduct storeProduct) {
        if (storeProduct == null || storeProduct.getId() == null) {
            throw new RuntimeException("更新店铺商品失败：StoreProduct数据或productId为空");
        }

        Long productId = storeProduct.getId();
        log.info("执行数据库UPDATE操作，店铺商品ID: {}", productId);

        // 先查询商品是否存在
        Optional<StoreProduct> existingProductOpt = storeProductMapper.selectById(productId);
        if (!existingProductOpt.isPresent()) {
            throw new RuntimeException("更新店铺商品失败：商品不存在，商品ID: " + productId);
        }

        // 如果修改了商品名称，检查同一店铺中是否有同名商品
        StoreProduct existing = existingProductOpt.get();
        if (storeProduct.getProductName() != null &&
                !storeProduct.getProductName().equals(existing.getProductName())) {
            Optional<StoreProduct> duplicate = storeProductMapper.selectByStoreIdAndProductName(
                    existing.getStoreId(), storeProduct.getProductName());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(productId)) {
                throw new RuntimeException("该店铺中已存在同名商品");
            }
        }

        // 注意：update_time 由数据库自动更新（ON UPDATE CURRENT_TIMESTAMP），不需要手动设置
        // 执行更新
        storeProductMapper.update(storeProduct);

        // UPDATE后重新查询以获取数据库更新的update_time（用于版本号）
        Optional<StoreProduct> updatedProduct = storeProductMapper.selectById(productId);
        if (updatedProduct.isPresent()) {
            storeProduct.setUpdateTime(updatedProduct.get().getUpdateTime());
        }
        log.info("数据库UPDATE成功，店铺商品ID: {}", productId);
    }

    /**
     * 更新店铺商品（公共方法，用于Controller直接调用，不使用事务消息）
     *
     * @deprecated 建议使用事务消息方式更新商品，通过 StoreProductMsgTransactionService
     */
    @Deprecated
    @Transactional
    public void updateStoreProduct(StoreProduct storeProduct) {
        updateStoreProductInternal(storeProduct);
    }

    /**
     * 删除店铺商品（内部方法，用于executeLocalTransaction中执行数据库操作）
     * 这个方法会在事务监听器的executeLocalTransaction中被调用
     *
     * @param storeProductId 店铺商品ID
     * @throws RuntimeException 如果删除失败或商品不存在
     */
    public void deleteStoreProductInternal(Long storeProductId) {
        log.info("执行数据库DELETE操作，店铺商品ID: {}", storeProductId);
        int deleted = storeProductMapper.deleteById(storeProductId);
        if (deleted == 0) {
            throw new RuntimeException("删除店铺商品失败：商品不存在，商品ID: " + storeProductId);
        }
        log.info("数据库DELETE成功，店铺商品ID: {}", storeProductId);
    }

    /**
     * 删除店铺商品（公共方法，用于Controller直接调用，不使用事务消息）
     *
     * @deprecated 建议使用事务消息方式删除商品，通过 StoreProductMsgTransactionService
     */
    @Deprecated
    @Transactional
    public void deleteStoreProduct(Long storeProductId) {
        deleteStoreProductInternal(storeProductId);
    }

    /**
     * 获取店铺的所有商品
     * 优先从关系缓存获取商品ID列表，然后批量获取商品详情
     */
    public List<StoreProduct> getStoreProducts(Long storeId) {
        log.info("查询店铺商品，店铺ID: {}", storeId);
        
        // 1. 优先从关系缓存获取商品列表
        List<StoreProduct> cachedProducts = storeProductCacheService.getStoreProductsFromCache(storeId);
        if (!cachedProducts.isEmpty()) {
            log.debug("从缓存获取店铺商品列表，店铺ID: {}, 商品数量: {}", storeId, cachedProducts.size());
            return cachedProducts;
        }
        
        // 2. 缓存未命中，使用分布式锁保护"查询数据库并初始化缓存"的逻辑
        // 使用与消息更新缓存相同的锁key，便于协调两者操作
        String lockKey = CACHE_LOCK_PREFIX + storeId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取分布式锁，最多等待3秒，锁持有时间不超过60秒
            // 如果消息正在更新缓存，这里会等待消息更新完成
            boolean lockAcquired = lock.tryLock(LOCK_WAIT_TIME, INIT_CACHE_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取缓存锁失败，可能消息正在更新缓存或存在并发初始化，店铺ID: {}", storeId);
                // 如果获取锁失败，再次尝试从缓存获取（可能消息更新已完成）
                List<StoreProduct> retryCachedProducts = storeProductCacheService.getStoreProductsFromCache(storeId);
                if (!retryCachedProducts.isEmpty()) {
                    log.debug("重试从缓存获取店铺商品列表成功，店铺ID: {}, 商品数量: {}", storeId, retryCachedProducts.size());
                    return retryCachedProducts;
                }
                // 如果仍然没有缓存，降级到数据库查询（不加锁，因为可能已经有其他线程在初始化）
                log.warn("获取锁失败且重试缓存仍为空，降级到数据库查询，店铺ID: {}", storeId);
                return storeProductMapper.selectByStoreId(storeId);
            }
            
            log.info("成功获取缓存锁，开始查询数据库并初始化缓存，店铺ID: {}", storeId);
            
            try {
                // 双重检查：获取锁后再次检查缓存（可能消息更新已完成）
                List<StoreProduct> doubleCheckCachedProducts = storeProductCacheService.getStoreProductsFromCache(storeId);
                if (!doubleCheckCachedProducts.isEmpty()) {
                    log.debug("双重检查：缓存已存在，店铺ID: {}, 商品数量: {}", storeId, doubleCheckCachedProducts.size());
                    return doubleCheckCachedProducts;
                }
                
                // 3. 缓存确实不存在，从数据库查询
                log.debug("缓存未命中，从数据库查询店铺商品列表，店铺ID: {}", storeId);
                List<StoreProduct> products = storeProductMapper.selectByStoreId(storeId);
                
                // 4. 初始化关系缓存和单个商品缓存（会进行时间戳比较，只缓存更新的数据）
                if (products != null && !products.isEmpty()) {
                    // 初始化关系缓存
                    storeProductCacheService.initStoreProductIdsCache(storeId, products);
                    // 缓存每个商品的详情（会自动比较时间戳，只更新更新的数据）
                    for (StoreProduct product : products) {
                        storeProductCacheService.cacheStoreProduct(product);
                    }
                    log.debug("初始化店铺商品缓存完成，店铺ID: {}, 商品数量: {}", storeId, products.size());
                }
                
                return products;
                
            } finally {
                // 释放锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("释放初始化缓存锁，店铺ID: {}", storeId);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取初始化缓存锁被中断，店铺ID: {}", storeId, e);
            // 中断时降级到数据库查询
            return storeProductMapper.selectByStoreId(storeId);
        } catch (Exception e) {
            log.error("初始化店铺商品缓存失败，店铺ID: {}", storeId, e);
            // 异常时降级到数据库查询
            return storeProductMapper.selectByStoreId(storeId);
        }
    }

    /**
     * 根据ID获取店铺商品
     */
    public Optional<StoreProduct> getStoreProductById(Long storeProductId) {
        log.info("查询店铺商品，商品ID: {}", storeProductId);
        return storeProductMapper.selectById(storeProductId);
    }

    /**
     * 根据店铺ID和状态获取商品列表
     */
    public List<StoreProduct> getStoreProductsByStatus(Long storeId, StoreProduct.StoreProductStatus status) {
        log.info("根据状态查询店铺商品，店铺ID: {}, 状态: {}", storeId, status);
        return storeProductMapper.selectByStoreIdAndStatus(storeId, status);
    }

    /**
     * 根据商品名称模糊搜索（跨店铺）
     */
    public List<StoreProduct> searchStoreProductsByName(String productName) {
        log.info("跨店铺搜索商品，商品名称: {}", productName);
        return storeProductMapper.selectByProductName(productName);
    }

    /**
     * 获取所有店铺商品（跨店铺）
     * 注意：这个方法可能会有性能问题，如果数据量大建议使用分页
     */
    public List<StoreProduct> getAllStoreProducts() {
        log.info("查询所有店铺商品（跨店铺）");
        // 这个方法需要在Mapper中实现，或者通过其他方式实现
        // 暂时返回空列表，或者可以通过遍历所有店铺来实现
        return List.of();
    }

    /**
     * 获取店铺商品总数
     */
    public int getStoreProductCount(Long storeId) {
        log.info("查询店铺商品总数，店铺ID: {}", storeId);
        List<StoreProduct> products = storeProductMapper.selectByStoreId(storeId);
        return products.size();
    }
}

