package com.jiaoyi.service;

import com.jiaoyi.config.RocketMQConfig;
import com.jiaoyi.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.entity.StoreProduct;
import com.jiaoyi.mapper.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 店铺商品服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreProductService {

    private final StoreProductMapper storeProductMapper;
    private final InventoryService inventoryService;
    private final StoreProductCacheService storeProductCacheService;
    private final OutboxService outboxService;
    private final com.jiaoyi.mapper.StoreMapper storeMapper;

    /**
     * 为店铺创建商品（使用Outbox模式）
     * 
     * 【Outbox模式流程】：
     * 1. 在同一个本地事务中：
     *    - 插入商品到store_products表
     *    - 创建库存记录
     *    - 写入outbox表
     * 2. 事务提交后，定时任务扫描outbox表并发送消息到RocketMQ
     * 3. 消费者接收消息并更新缓存
     *
     * @param storeId      店铺ID
     * @param storeProduct 店铺商品对象
     * @return 插入后的店铺商品对象（包含生成的ID）
     * @throws RuntimeException 如果插入失败
     */
    @Transactional
    public StoreProduct createStoreProduct(Long storeId, StoreProduct storeProduct) {
        log.info("创建店铺商品（Outbox模式），店铺ID: {}, 商品名称: {}", storeId, storeProduct.getProductName());

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

            // 递增店铺的商品列表版本号（原子操作，在同一个事务中）
            storeMapper.incrementProductListVersion(storeId);
            log.info("店铺商品列表版本号已递增，店铺ID: {}", storeId);

            // 在同一个事务中写入outbox表
            StoreProductCacheUpdateMessage message = new StoreProductCacheUpdateMessage();
            message.setProductId(productId);
            message.setStoreId(storeId);
            message.setOperationType(StoreProductCacheUpdateMessage.OperationType.CREATE);
            message.setTimestamp(System.currentTimeMillis());
            message.setEnrichInventory(true);
            message.setStoreProduct(storeProduct);
            
            // 写入outbox表（在同一个事务中）
            String messageKey = String.valueOf(productId); // 使用productId作为messageKey
            outboxService.saveMessage(
                    RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
                    RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG,
                    messageKey,
                    message
            );
            log.info("已写入outbox表，商品ID: {}，定时任务将异步发送消息", productId);

            log.info("店铺商品创建成功（Outbox模式），商品ID: {}", productId);
            return storeProduct;

        } catch (Exception e) {
            log.error("创建店铺商品失败，店铺ID: {}, 商品名称: {}", storeId, productName, e);
            throw new RuntimeException("创建店铺商品失败: " + e.getMessage(), e);
        }
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
     * 更新店铺商品（使用Outbox模式）
     */
    @Transactional
    public void updateStoreProduct(StoreProduct storeProduct) {
        Long productId = storeProduct.getId();
        Long storeId = storeProduct.getStoreId();
        
        // 如果storeId为空，从数据库查询
        if (storeId == null && productId != null) {
            Optional<StoreProduct> existing = storeProductMapper.selectById(productId);
            if (existing.isPresent()) {
                storeId = existing.get().getStoreId();
                storeProduct.setStoreId(storeId);
            }
        }
        
        // 执行数据库更新
        updateStoreProductInternal(storeProduct);
        
        // 在同一个事务中写入outbox表
        StoreProductCacheUpdateMessage message = new StoreProductCacheUpdateMessage();
        message.setProductId(productId);
        message.setStoreId(storeId);
        message.setOperationType(StoreProductCacheUpdateMessage.OperationType.UPDATE);
        message.setTimestamp(System.currentTimeMillis());
        message.setEnrichInventory(true);
        message.setStoreProduct(storeProduct);
        
        // 写入outbox表（在同一个事务中）
        String messageKey = String.valueOf(productId);
        outboxService.saveMessage(
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG,
                messageKey,
                message
        );
        log.info("已写入outbox表（UPDATE），商品ID: {}，定时任务将异步发送消息", productId);
    }

    /**
     * 删除店铺商品（内部方法，逻辑删除）
     * 
     * @param storeProductId 店铺商品ID
     * @throws RuntimeException 如果删除失败或商品不存在
     */
    public void deleteStoreProductInternal(Long storeProductId) {
        log.info("执行数据库逻辑删除操作，店铺商品ID: {}", storeProductId);
        int updated = storeProductMapper.deleteById(storeProductId);
        if (updated == 0) {
            throw new RuntimeException("删除店铺商品失败：商品不存在或已删除，商品ID: " + storeProductId);
        }
        log.info("数据库逻辑删除成功，店铺商品ID: {}", storeProductId);
    }

    /**
     * 删除店铺商品（使用Outbox模式）
     */
    @Transactional
    public void deleteStoreProduct(Long storeProductId, Long storeId) {
        // 如果storeId为空，从数据库查询
        if (storeId == null && storeProductId != null) {
            Optional<StoreProduct> existing = storeProductMapper.selectById(storeProductId);
            if (existing.isPresent()) {
                storeId = existing.get().getStoreId();
            }
        }
        
        // 执行数据库删除
        deleteStoreProductInternal(storeProductId);
        
        // 递增店铺的商品列表版本号（原子操作，在同一个事务中）
        if (storeId != null) {
            storeMapper.incrementProductListVersion(storeId);
            log.info("店铺商品列表版本号已递增（DELETE），店铺ID: {}", storeId);
        }
        
        // 在同一个事务中写入outbox表
        StoreProductCacheUpdateMessage message = new StoreProductCacheUpdateMessage();
        message.setProductId(storeProductId);
        message.setStoreId(storeId);
        message.setOperationType(StoreProductCacheUpdateMessage.OperationType.DELETE);
        message.setTimestamp(System.currentTimeMillis());
        message.setEnrichInventory(false);
        
        // 写入outbox表（在同一个事务中）
        String messageKey = String.valueOf(storeProductId);
        outboxService.saveMessage(
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG,
                messageKey,
                message
        );
        log.info("已写入outbox表（DELETE），商品ID: {}，定时任务将异步发送消息", storeProductId);
    }

    /**
     * 获取店铺的所有商品
     * 优先从关系缓存获取商品ID列表，然后批量获取商品详情
     * 使用Lua脚本保证缓存更新的原子性，无需加锁
     */
    public List<StoreProduct> getStoreProducts(Long storeId) {
        log.info("查询店铺商品，店铺ID: {}", storeId);
        
        // 1. 优先从关系缓存获取商品列表
        List<StoreProduct> cachedProducts = storeProductCacheService.getStoreProductsFromCache(storeId);
        if (!cachedProducts.isEmpty()) {
            log.debug("从缓存获取店铺商品列表，店铺ID: {}, 商品数量: {}", storeId, cachedProducts.size());
            return cachedProducts;
        }
        
        // 2. 缓存未命中，从数据库查询并初始化缓存
        // Lua脚本会原子性地比较update_time，只更新更新的数据，无需加锁
        log.debug("缓存未命中，从数据库查询店铺商品列表，店铺ID: {}", storeId);
        List<StoreProduct> products = storeProductMapper.selectByStoreId(storeId);
        
        // 3. 初始化关系缓存和单个商品缓存（使用Lua脚本，会自动比较时间戳）
        if (products != null && !products.isEmpty()) {
            // 初始化关系缓存
            storeProductCacheService.initStoreProductIdsCache(storeId, products);
            // 缓存每个商品的详情（Lua脚本会原子性地比较update_time，只更新更新的数据）
            for (StoreProduct product : products) {
                storeProductCacheService.cacheStoreProduct(product);
            }
            log.debug("初始化店铺商品缓存完成，店铺ID: {}, 商品数量: {}", storeId, products.size());
        }
        
        return products;
    }

    /**
     * 根据ID获取店铺商品
     * 优先从缓存获取，缓存未命中则从数据库查询并缓存
     */
    public Optional<StoreProduct> getStoreProductById(Long storeProductId) {
        if (storeProductId == null) {
            return Optional.empty();
        }
        
        log.info("查询店铺商品，商品ID: {}", storeProductId);
        
        // 1. 优先从缓存获取
        Optional<StoreProduct> cachedProduct = storeProductCacheService.getStoreProductById(storeProductId);
        if (cachedProduct.isPresent()) {
            log.debug("从缓存获取店铺商品，商品ID: {}", storeProductId);
            return cachedProduct;
        }
        
        // 2. 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询店铺商品，商品ID: {}", storeProductId);
        Optional<StoreProduct> productOpt = storeProductMapper.selectById(storeProductId);
        
        // 3. 如果数据库中有数据，缓存起来
        if (productOpt.isPresent()) {
            storeProductCacheService.cacheStoreProduct(productOpt.get());
            log.debug("已将店铺商品缓存，商品ID: {}", storeProductId);
        }
        
        return productOpt;
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

