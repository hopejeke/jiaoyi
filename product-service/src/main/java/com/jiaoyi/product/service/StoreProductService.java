package com.jiaoyi.product.service;

import com.jiaoyi.product.config.RocketMQConfig;
import com.jiaoyi.product.dto.StoreProductCacheUpdateMessage;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.mapper.sharding.StoreProductMapper;
import com.jiaoyi.product.mapper.primary.StoreMapper;
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
    private final StoreMapper storeMapper;

    /**
     * 为店铺创建商品（使用Outbox模式）
     * <p>
     * 【Outbox模式流程】：
     * 1. 在同一个本地事务中：
     * - 插入商品到store_products表
     * - 创建库存记录
     * - 写入outbox表
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
            // INSERT时version直接设置为1，插入后通过selectKey自动返回id
            // selectKey 使用 store_id 和 product_name 查询（包含分片键，确保正确路由到分片表）
            storeProductMapper.insert(storeProduct);

            Long productId = storeProduct.getId();

            if (productId == null) {
                throw new RuntimeException("店铺商品创建失败：未获取到productId，可能数据库INSERT失败");
            }

            // 通过 store_id 和 product_name 查询 version（包含分片键，确保正确路由）
            StoreProduct insertedProduct = storeProductMapper.selectByStoreIdAndProductName(storeId, productName)
                    .orElseThrow(() -> new RuntimeException("店铺商品创建失败：插入后无法查询到商品记录"));
            Long version = insertedProduct.getVersion();

            if (version == null) {
                throw new RuntimeException("店铺商品创建失败：未获取到version");
            }

            // 将 version 设置回 storeProduct 对象
            storeProduct.setVersion(version);

            log.info("商品创建成功，商品ID: {}, 版本号: {}", productId, version);

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
            message.setVersion(storeProduct.getVersion()); // 设置版本号
            message.setStoreProduct(storeProduct); // 包含完整的商品信息

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

        // 先查询商品是否存在（获取当前version用于乐观锁校验）
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

        // 使用乐观锁：设置当前version到storeProduct对象中，用于WHERE条件校验

        storeProduct.setVersion(existing.getVersion());


        // 注意：update_time 由数据库自动更新（ON UPDATE CURRENT_TIMESTAMP），不需要手动设置
        // 使用乐观锁：UPDATE时WHERE条件中加上version校验，只有version匹配才更新
        // 如果version不匹配（affected rows = 0），说明数据已被其他事务修改
        int affectedRows = storeProductMapper.update(storeProduct);
        if (affectedRows == 0) {
            throw new RuntimeException("店铺商品更新失败：乐观锁冲突，数据已被其他事务修改，商品ID: " + productId);
        }

        Long version = storeProduct.getVersion();
        if (version == null) {
            throw new RuntimeException("店铺商品更新失败：未获取到version");
        }

        log.info("数据库UPDATE成功（乐观锁），店铺商品ID: {}, 版本号: {}", productId, version);
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

        // 执行数据库更新（内部会递增版本号）
        updateStoreProductInternal(storeProduct);

        // 在同一个事务中写入outbox表
        StoreProductCacheUpdateMessage message = new StoreProductCacheUpdateMessage();
        message.setProductId(productId);
        message.setStoreId(storeId);
        message.setOperationType(StoreProductCacheUpdateMessage.OperationType.UPDATE);
        message.setTimestamp(System.currentTimeMillis());
        message.setEnrichInventory(true);
        message.setVersion(storeProduct.getVersion()); // 设置版本号
        message.setStoreProduct(storeProduct); // 包含完整的商品信息

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
     * @param storeProductId  店铺商品ID
     * @param expectedVersion 期望的版本号（用于乐观锁校验）
     * @return 删除后的版本号
     * @throws RuntimeException 如果删除失败、商品不存在或乐观锁冲突
     */
    public Long deleteStoreProductInternal(Long storeProductId, Long expectedVersion) {
        log.info("执行数据库逻辑删除操作（乐观锁），店铺商品ID: {}, 期望版本号: {}", storeProductId, expectedVersion);

        // 创建一个临时对象用于接收selectKey返回的version值
        StoreProduct tempProduct = new StoreProduct();
        tempProduct.setId(storeProductId);
        tempProduct.setVersion(expectedVersion);

        // 使用乐观锁：DELETE时WHERE条件中加上version校验，只有version匹配才删除
        // 如果version不匹配（affected rows = 0），说明数据已被其他事务修改
        int updated = storeProductMapper.deleteById(tempProduct);
        if (updated == 0) {
            throw new RuntimeException("删除店铺商品失败：商品不存在、已删除或乐观锁冲突（数据已被其他事务修改），商品ID: " + storeProductId);
        }

        Long version = tempProduct.getVersion();
        if (version == null) {
            throw new RuntimeException("删除店铺商品失败：未获取到version，商品ID: " + storeProductId);
        }

        log.info("数据库逻辑删除成功（乐观锁），店铺商品ID: {}, 版本号: {}", storeProductId, version);
        return version;
    }

    /**
     * 删除店铺商品（使用Outbox模式）
     */
    @Transactional
    public void deleteStoreProduct(Long storeProductId, Long storeId) {
        // 删除前先查询商品信息（获取版本号和店铺ID，用于乐观锁校验）
        Optional<StoreProduct> existingProduct = storeProductMapper.selectById(storeProductId);
        if (!existingProduct.isPresent()) {
            throw new RuntimeException("删除店铺商品失败：商品不存在，商品ID: " + storeProductId);
        }

        StoreProduct product = existingProduct.get();
        if (storeId == null) {
            storeId = product.getStoreId();
        }

        // 使用乐观锁：执行数据库删除时校验version，只有version匹配才删除
        Long expectedVersion = product.getVersion();
        if (expectedVersion == null) {
            throw new RuntimeException("删除店铺商品失败：未获取到当前版本号，商品ID: " + storeProductId);
        }

        // 执行数据库删除（内部会校验version并递增版本号，返回新version）
        Long version = deleteStoreProductInternal(storeProductId, expectedVersion);

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
        message.setVersion(version); // 设置版本号
        message.setStoreProduct(product); // 包含商品信息（用于消费消息时使用）

        // 写入outbox表（在同一个事务中）
        String messageKey = String.valueOf(storeProductId);
        outboxService.saveMessage(
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TOPIC,
                RocketMQConfig.PRODUCT_CACHE_UPDATE_TAG,
                messageKey,
                message
        );
        log.info("已写入outbox表（DELETE），商品ID: {}，版本号: {}，定时任务将异步发送消息", storeProductId, version);
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
        log.info("使用的 Mapper: {}", storeProductMapper.getClass().getName());
        // 从数据库查询所有未删除的商品
        // 注意：如果使用了 ShardingSphere，这个查询会广播到所有分片库（jiaoyi_0, jiaoyi_1, jiaoyi_2）
        List<StoreProduct> products = storeProductMapper.selectAll();
        log.info("查询到 {} 个店铺商品", products.size());
        if (!products.isEmpty()) {
            log.info("第一个商品的 store_id: {}, id: {}", products.get(0).getStoreId(), products.get(0).getId());
        }
        return products;
    }

    /**
     * 获取店铺商品总数
     */
    public int getStoreProductCount(Long storeId) {
        log.info("查询店铺商品总数，店铺ID: {}", storeId);
        List<StoreProduct> products = storeProductMapper.selectByStoreId(storeId);
        return products.size();
    }

    // ==================== B端商家专用方法（直接读DB，不走缓存） ====================

    /**
     * B端商家：根据店铺ID获取该店铺的所有商品（直接读DB，不走缓存）
     * 用于商家后台页面，确保修改后立即看到最新数据
     */
    public List<StoreProduct> getStoreProductsFromDb(Long storeId) {
        log.info("B端商家查询店铺商品（直接读DB），店铺ID: {}", storeId);
        return storeProductMapper.selectByStoreId(storeId);
    }

    /**
     * B端商家：根据ID获取店铺商品详情（直接读DB，不走缓存）
     * 用于商家后台页面，确保修改后立即看到最新数据
     */
    public Optional<StoreProduct> getStoreProductByIdFromDb(Long storeProductId) {
        if (storeProductId == null) {
            return Optional.empty();
        }
        log.info("B端商家查询店铺商品详情（直接读DB），商品ID: {}", storeProductId);
        // 兼容旧接口，先尝试通过id查询（会广播查询所有分片）
        return storeProductMapper.selectById(storeProductId);
    }
    
    /**
     * B端商家：根据店铺ID和商品ID获取店铺商品详情（直接读DB，不走缓存，推荐）
     * 包含分片键，查询性能更好
     */
    public Optional<StoreProduct> getStoreProductByIdFromDb(Long storeId, Long storeProductId) {
        if (storeId == null || storeProductId == null) {
            return Optional.empty();
        }
        log.info("B端商家查询店铺商品详情（直接读DB，包含分片键），店铺ID: {}, 商品ID: {}", storeId, storeProductId);
        return storeProductMapper.selectByStoreIdAndId(storeId, storeProductId);
    }

    /**
     * B端商家：根据店铺ID和状态获取商品列表（直接读DB，不走缓存）
     */
    public List<StoreProduct> getStoreProductsByStatusFromDb(Long storeId, StoreProduct.StoreProductStatus status) {
        log.info("B端商家根据状态查询店铺商品（直接读DB），店铺ID: {}, 状态: {}", storeId, status);
        return storeProductMapper.selectByStoreIdAndStatus(storeId, status);
    }

    /**
     * B端商家：搜索店铺商品（按店铺ID和商品名称，直接读DB，不走缓存）
     */
    public List<StoreProduct> searchStoreProductsFromDb(Long storeId, String name) {
        log.info("B端商家搜索店铺商品（直接读DB），店铺ID: {}, 关键词: {}", storeId, name);
        List<StoreProduct> allProducts = storeProductMapper.selectByStoreId(storeId);
        return allProducts.stream()
                .filter(p -> p.getProductName() != null && 
                           p.getProductName().toLowerCase().contains(name.toLowerCase()))
                .toList();
    }

    /**
     * B端商家：获取店铺商品总数（直接读DB，不走缓存）
     */
    public int getStoreProductCountFromDb(Long storeId) {
        log.info("B端商家查询店铺商品总数（直接读DB），店铺ID: {}", storeId);
        List<StoreProduct> products = storeProductMapper.selectByStoreId(storeId);
        return products.size();
    }
}

