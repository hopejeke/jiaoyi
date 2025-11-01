package com.jiaoyi.service;

import com.jiaoyi.entity.Product;
import com.jiaoyi.entity.Inventory;
import com.jiaoyi.dto.ProductCacheUpdateMessage;
import com.jiaoyi.mapper.ProductMapper;
import com.jiaoyi.service.InventoryService;
import com.jiaoyi.service.ProductCacheService;
import com.jiaoyi.service.ProductCacheUpdateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 商品服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductMapper productMapper;
    private final InventoryService inventoryService;
    private final ProductCacheService productCacheService;
    private final ProductCacheUpdateMessageService productCacheUpdateMessageService;
    
    /**
     * 根据ID获取商品（聚合库存信息，优先从缓存）
     */
    public Optional<Product> getProductById(Long id) {
        log.info("根据ID查询商品，ID: {}", id);
        
        // 先从缓存获取
        Optional<Product> cachedProduct = productCacheService.getProductById(id);
        if (cachedProduct.isPresent()) {
            log.debug("从缓存获取商品信息，商品ID: {}", id);
            return cachedProduct;
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询商品信息，商品ID: {}", id);
        Optional<Product> productOpt = productMapper.selectById(id);
        
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            // 聚合库存信息
            enrichProductWithInventory(product);
            // 缓存商品信息
            productCacheService.cacheProduct(product);
            return Optional.of(product);
        }
        
        return Optional.empty();
    }
    
    /**
     * 为商品聚合库存信息
     */
    private void enrichProductWithInventory(Product product) {
        try {
            Optional<Inventory> inventoryOpt = inventoryService.getInventoryByProductId(product.getId());
            if (inventoryOpt.isPresent()) {
                Inventory inventory = inventoryOpt.get();
                product.setCurrentStock(inventory.getCurrentStock());
                product.setLockedStock(inventory.getLockedStock());
                product.setMinStock(inventory.getMinStock());
                product.setMaxStock(inventory.getMaxStock());
                log.debug("商品 {} 库存信息聚合成功", product.getId());
            } else {
                // 如果没有库存记录，设置默认值
                product.setCurrentStock(0);
                product.setLockedStock(0);
                product.setMinStock(0);
                product.setMaxStock(0);
                log.warn("商品 {} 没有库存记录，设置默认值", product.getId());
            }
        } catch (Exception e) {
            log.error("聚合商品 {} 库存信息失败", product.getId(), e);
            // 发生异常时设置默认值
            product.setCurrentStock(0);
            product.setLockedStock(0);
            product.setMinStock(0);
            product.setMaxStock(0);
        }
    }
    
    /**
     * 获取所有商品（优先从缓存）
     */
    public List<Product> getAllProducts() {
        log.info("查询所有商品");
        
        // 先从缓存获取
        Optional<List<Product>> cachedProducts = productCacheService.getCachedProductList();
        if (cachedProducts.isPresent()) {
            log.debug("从缓存获取商品列表");
            return cachedProducts.get();
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询商品列表");
        List<Product> products = productMapper.selectAll();
        
        // 缓存查询结果
        if (products != null && !products.isEmpty()) {
            productCacheService.cacheProductList(products);
        }
        
        return products;
    }
    
    /**
     * 根据状态获取商品列表（优先从缓存）
     */
    public List<Product> getProductsByStatus(Product.ProductStatus status) {
        log.info("根据状态查询商品，状态: {}", status);
        
        // 先从缓存获取
        Optional<List<Product>> cachedProducts = productCacheService.getCachedProductsByStatus(status.name());
        if (cachedProducts.isPresent()) {
            log.debug("从缓存获取状态商品列表，状态: {}", status);
            return cachedProducts.get();
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询状态商品列表，状态: {}", status);
        List<Product> products = productMapper.selectByStatus(status);
        
        // 缓存查询结果
        if (products != null && !products.isEmpty()) {
            productCacheService.cacheProductsByStatus(status.name(), products);
        }
        
        return products;
    }
    
    /**
     * 根据分类获取商品列表（优先从缓存）
     */
    public List<Product> getProductsByCategory(String category) {
        log.info("根据分类查询商品，分类: {}", category);
        
        // 先从缓存获取
        Optional<List<Product>> cachedProducts = productCacheService.getCachedProductsByCategory(category);
        if (cachedProducts.isPresent()) {
            log.debug("从缓存获取分类商品列表，分类: {}", category);
            return cachedProducts.get();
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询分类商品列表，分类: {}", category);
        List<Product> products = productMapper.selectByCategory(category);
        
        // 缓存查询结果
        if (products != null && !products.isEmpty()) {
            productCacheService.cacheProductsByCategory(category, products);
        }
        
        return products;
    }
    
    /**
     * 根据商品名称模糊查询
     */
    public List<Product> searchProductsByName(String name) {
        log.info("根据名称搜索商品，名称: {}", name);
        return productMapper.selectByNameLike(name);
    }
    
    /**
     * 分页获取商品列表
     */
    public List<Product> getProductsByPage(int pageNum, int pageSize) {
        log.info("分页查询商品，页码: {}, 大小: {}", pageNum, pageSize);
        int offset = (pageNum - 1) * pageSize;
        return productMapper.selectByPage(offset, pageSize);
    }
    
    /**
     * 获取商品总数
     */
    public int getProductCount() {
        log.info("查询商品总数");
        return productMapper.countAll();
    }
    
    /**
     * 创建商品
     * 
     * 【事务消息流程 - 先插入DB，再发送半消息更新缓存】
     * 1. 先执行数据库INSERT操作（立即获取ID）
     * 2. 如果INSERT成功，发送半消息（用于更新缓存）
     * 3. 在executeLocalTransaction中验证商品是否存在
     * 4. 如果验证成功，提交半消息；如果失败，回滚
     * 
     * 【为什么CREATE需要先INSERT】
     * - CREATE操作需要立即获取自增ID，返回给调用者
     * - RocketMQ事务消息的executeLocalTransaction无法返回值
     * - 所以采用折中方案：先INSERT，再发送半消息验证并更新缓存
     * 
     * 【防宕机保证】
     * - 如果INSERT成功，但半消息未发送 → 缓存不更新，但可通过定时任务补偿
     * - 如果半消息已发送，但验证失败 → 半消息会被回滚，不影响数据库
     * - 如果验证成功，但提交前宕机 → 通过回查机制确认并提交
     */
    public Product createProduct(Product product) {
        log.info("创建商品，商品名称: {}", product.getProductName());
        
        // 步骤1: 先执行数据库INSERT操作（立即获取ID）
        log.info("步骤1: 执行数据库INSERT操作，商品名称: {}", product.getProductName());
        productMapper.insert(product);
        
        // INSERT后，product.getId()会被自动设置（如果使用自增ID）
        Long productId = product.getId();
        if (productId == null) {
            throw new RuntimeException("商品创建失败：未获取到productId，可能数据库INSERT失败");
        }
        log.info("数据库INSERT成功，商品ID: {}", productId);
        
        // 步骤2: 发送事务消息（半消息），用于异步更新缓存
        // 
        // 【重要】sendMessageInTransaction 是同步阻塞调用，执行流程：
        //   1) 发送半消息到Broker（同步，消息已存储但对消费者不可见）
        //   2) 立即同步调用 executeLocalTransaction()（在当前线程执行，验证商品是否存在）
        //   3) 根据返回值决定 COMMIT/ROLLBACK 半消息（同步）
        //   4) 方法返回
        //
        // 【关键】当 sendCacheUpdateMessage 返回时：
        // - ✅ 半消息已经发送到Broker（物理上已存储）
        // - ✅ executeLocalTransaction 已经执行完成（验证商品存在）
        // - ✅ 半消息已经变为正式消息（COMMIT）或被删除（ROLLBACK）
        // - ✅ 如果是COMMIT，消费者已经可以看到消息了（会被异步消费更新缓存）
        productCacheUpdateMessageService.sendCacheUpdateMessage(
            productId, // 使用INSERT后获得的ID
            ProductCacheUpdateMessage.OperationType.CREATE,
            true, // 聚合库存信息
            product, // 传递Product数据，用于缓存更新
            null // CREATE操作不需要productIdRef
        );
        
        // 执行到这里时，消息已经：
        // 1. 已发送到Broker并存储
        // 2. 已通过验证（executeLocalTransaction返回COMMIT）
        // 3. 已变为正式消息，消费者可以看到了
        // 4. 消费者会异步调用 onMessage() 更新缓存
        log.info("商品创建成功，商品ID: {}，缓存更新消息已发送并提交（消费者可以看到消息了）", productId);
        return product;
    }
    
    /**
     * 更新商品
     * 
     * 【事务消息流程 - 先发送半消息，再执行DB更新】
     * 1. 先发送半消息（包含Product数据）
     * 2. 在executeLocalTransaction中执行数据库UPDATE
     * 3. 如果UPDATE成功，提交半消息；否则回滚
     * 
     * 【防宕机保证】
     * - 如果半消息已发送，但UPDATE未执行 → 半消息会被回滚，数据库不会被修改
     * - 如果UPDATE成功，但返回COMMIT前宕机 → 通过回查机制确认并提交
     * - 只有UPDATE成功，消息才会被提交，缓存才会更新
     */
    public Product updateProduct(Product product) {
        log.info("更新商品，商品ID: {}", product.getId());
        
        // 先发送事务消息（半消息），数据库操作在executeLocalTransaction中执行
        productCacheUpdateMessageService.sendCacheUpdateMessage(
            product.getId(),
            ProductCacheUpdateMessage.OperationType.UPDATE,
            true, // 聚合库存信息
            product, // 传递Product数据，在executeLocalTransaction中执行update
            null // UPDATE操作不需要productIdRef
        );
        
        log.info("商品更新消息已发送，商品ID: {}（数据库更新在事务监听器中执行）", product.getId());
        return product;
    }
    
    /**
     * 删除商品
     * 
     * 【事务消息流程 - 先发送半消息，再执行DB删除】
     * 1. 先发送半消息（包含productId）
     * 2. 在executeLocalTransaction中执行数据库DELETE
     * 3. 如果DELETE成功，提交半消息；否则回滚
     * 
     * 【防宕机保证】
     * - 如果半消息已发送，但DELETE未执行 → 半消息会被回滚，数据库不会被修改
     * - 如果DELETE成功，但返回COMMIT前宕机 → 通过回查机制确认并提交
     * - 只有DELETE成功，消息才会被提交，缓存才会删除
     */
    public boolean deleteProduct(Long productId) {
        log.info("删除商品，商品ID: {}", productId);
        
        // 先发送事务消息（半消息），数据库操作在executeLocalTransaction中执行
        productCacheUpdateMessageService.sendCacheUpdateMessage(
            productId,
            ProductCacheUpdateMessage.OperationType.DELETE,
            false, // 删除操作不需要库存信息
            null, // DELETE操作不需要Product对象
            null // DELETE操作不需要productIdRef
        );
        
        log.info("商品删除消息已发送，商品ID: {}（数据库删除在事务监听器中执行）", productId);
        // 注意：实际删除操作在executeLocalTransaction中执行
        // 如果失败，executeLocalTransaction会返回ROLLBACK，半消息不会提交
        return true;
    }
    
    // ==================== 缓存管理方法 ====================
    
    /**
     * 刷新商品缓存（从数据库重新加载）
     * 使用事务消息异步刷新
     */
    public void refreshProductCache(Long productId) {
        log.info("刷新商品缓存，商品ID: {}", productId);
        
        // 发送事务消息，异步刷新缓存
        productCacheUpdateMessageService.sendCacheUpdateMessage(
            productId,
            ProductCacheUpdateMessage.OperationType.REFRESH,
            true, // 聚合库存信息
            null, // REFRESH操作不需要Product对象
            null // REFRESH操作不需要productIdRef
        );
    }
    
    /**
     * 同步刷新商品缓存（直接从数据库加载，不使用消息）
     * 用于消息消费或降级场景
     */
    public void refreshProductCacheSync(Long productId) {
        log.info("同步刷新商品缓存，商品ID: {}", productId);
        
        // 删除旧缓存
        productCacheService.evictProductCache(productId);
        
        // 重新从数据库查询并缓存
        Optional<Product> productOpt = productMapper.selectById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            // 聚合库存信息
            enrichProductWithInventory(product);
            // 缓存商品信息
            productCacheService.cacheProduct(product);
            log.info("商品缓存同步刷新成功，商品ID: {}", productId);
        } else {
            log.warn("商品不存在，无法刷新缓存，商品ID: {}", productId);
        }
    }
    
    /**
     * 刷新商品列表缓存
     */
    public void refreshProductListCache() {
        log.info("刷新商品列表缓存");
        
        // 删除旧缓存
        productCacheService.evictProductListCache();
        
        // 重新从数据库查询并缓存
        List<Product> products = productMapper.selectAll();
        if (products != null && !products.isEmpty()) {
            productCacheService.cacheProductList(products);
            log.info("商品列表缓存刷新成功，数量: {}", products.size());
        } else {
            log.info("没有商品数据");
        }
    }
    
    /**
     * 清空所有商品缓存
     */
    public void clearAllProductCache() {
        log.info("清空所有商品缓存");
        productCacheService.clearAllProductCache();
    }
    
    /**
     * 检查商品缓存状态
     */
    public boolean hasProductCache(Long productId) {
        return productCacheService.hasProductCache(productId);
    }
    
    /**
     * 获取商品缓存剩余时间
     */
    public Long getProductCacheExpireTime(Long productId) {
        return productCacheService.getProductCacheExpireTime(productId);
    }
}
