package com.jiaoyi.product.service;

import com.jiaoyi.common.exception.InsufficientStockException;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.InventoryTransaction;
import com.jiaoyi.product.mapper.sharding.InventoryMapper;
import com.jiaoyi.product.mapper.sharding.InventoryTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 库存服务层
 * 注意：微服务拆分后，与订单相关的库存操作（如根据订单号扣减库存）应通过 Feign Client 调用 order-service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryCacheService inventoryCacheService;
    
    /**
     * 创建库存记录（商品创建时调用，商品级别库存）
     * 注意：现在库存是SKU级别的，商品创建时不再自动创建库存
     * 需要在创建SKU时调用 createInventoryForSku 创建SKU级别的库存
     */
    @Transactional
    public Inventory createInventory(Long storeId, Long productId, String productName) {
        log.info("创建库存记录（商品级别，兼容旧逻辑），店铺ID: {}, 商品ID: {}, 商品名称: {}", storeId, productId, productName);
        
        // 检查是否已存在库存记录（商品级别，sku_id为NULL）
        Inventory existing = inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
        if (existing != null) {
            log.warn("库存记录已存在，店铺ID: {}, 商品ID: {}", storeId, productId);
            return existing;
        }
        
        // 创建新的库存记录（商品级别，sku_id为NULL）
        Inventory inventory = new Inventory();
        inventory.setStoreId(storeId);
        inventory.setProductId(productId);
        inventory.setSkuId(null); // 商品级别库存，sku_id为NULL
        inventory.setProductName(productName);
        inventory.setSkuName(null);
        inventory.setCurrentStock(0); // 默认库存为0
        inventory.setLockedStock(0);
        inventory.setMinStock(0); // 默认最低库存预警线为0
        inventory.setMaxStock(999999); // 最大库存容量，999999表示不限制
        
        inventoryMapper.insert(inventory);
        log.info("库存记录创建成功（商品级别），库存ID: {}, 店铺ID: {}, 商品ID: {}", inventory.getId(), storeId, productId);
        
        // 缓存新创建的库存记录
        inventoryCacheService.cacheInventory(inventory);
        
        return inventory;
    }
    
    /**
     * 创建SKU级别的库存记录（SKU创建时调用）
     */
    @Transactional
    public Inventory createInventoryForSku(Long storeId, Long productId, Long skuId, String productName, String skuName) {
        log.info("创建SKU级别库存记录，店铺ID: {}, 商品ID: {}, SKU ID: {}, SKU名称: {}", storeId, productId, skuId, skuName);
        
        // 检查是否已存在SKU库存记录
        Inventory existing = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(storeId, productId, skuId);
        if (existing != null) {
            log.warn("SKU库存记录已存在，店铺ID: {}, 商品ID: {}, SKU ID: {}", storeId, productId, skuId);
            return existing;
        }
        
        // 创建新的SKU级别库存记录
        Inventory inventory = new Inventory();
        inventory.setStoreId(storeId);
        inventory.setProductId(productId);
        inventory.setSkuId(skuId); // SKU级别库存
        inventory.setProductName(productName);
        inventory.setSkuName(skuName);
        inventory.setCurrentStock(0); // 默认库存为0
        inventory.setLockedStock(0);
        inventory.setMinStock(0); // 默认最低库存预警线为0
        inventory.setMaxStock(999999); // 最大库存容量，999999表示不限制
        
        inventoryMapper.insert(inventory);
        log.info("SKU库存记录创建成功，库存ID: {}, 店铺ID: {}, 商品ID: {}, SKU ID: {}", inventory.getId(), storeId, productId, skuId);
        
        // 缓存新创建的库存记录
        inventoryCacheService.cacheInventory(inventory);
        
        return inventory;
    }
    
    /**
     * 设置库存数量（商品级别或SKU级别）
     */
    @Transactional
    public Inventory setStock(Long storeId, Long productId, Long skuId, Integer stock, Integer minStock, Integer maxStock) {
        log.info("设置库存数量，店铺ID: {}, 商品ID: {}, SKU ID: {}, 库存: {}", storeId, productId, skuId, stock);
        
        Inventory inventory;
        if (skuId != null) {
            // SKU级别库存
            inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(storeId, productId, skuId);
            if (inventory == null) {
                throw new RuntimeException("SKU库存记录不存在，店铺ID: " + storeId + ", 商品ID: " + productId + ", SKU ID: " + skuId);
            }
        } else {
            // 商品级别库存
            inventory = inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
            if (inventory == null) {
                throw new RuntimeException("商品库存记录不存在，店铺ID: " + storeId + ", 商品ID: " + productId);
            }
        }
        
        int updatedRows = inventoryMapper.updateStock(
            storeId, 
            productId, 
            skuId, 
            stock != null ? stock : inventory.getCurrentStock(),
            minStock,
            maxStock
        );
        
        if (updatedRows == 0) {
            throw new RuntimeException("库存更新失败，可能记录不存在");
        }
        
        // 重新查询更新后的库存
        if (skuId != null) {
            inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(storeId, productId, skuId);
        } else {
            inventory = inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
        }
        
        log.info("库存数量设置成功，库存ID: {}, 当前库存: {}", inventory.getId(), inventory.getCurrentStock());
        
        // 更新缓存
        inventoryCacheService.cacheInventory(inventory);
        
        return inventory;
    }
    
    /**
     * 检查并锁定库存（下单时调用，基于SKU）
     */
    @Transactional
    public void checkAndLockStock(Long productId, Long skuId, Integer quantity) {
        log.info("检查并锁定库存，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
        
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID不能为空");
        }
        
        // 查询SKU库存（需要storeId，但这里只有productId，先查询商品获取storeId）
        Inventory productInventory = inventoryMapper.selectByProductId(productId);
        if (productInventory == null) {
            throw new InsufficientStockException(productId, "未知商品", quantity, 0);
        }
        
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
            productInventory.getStoreId(), productId, skuId);
        if (inventory == null) {
            throw new InsufficientStockException(productId, "SKU库存不存在", quantity, 0);
        }
        
        // 检查可用库存
        int availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
        if (availableStock < quantity) {
            throw new InsufficientStockException(
                productId, 
                inventory.getSkuName() != null ? inventory.getSkuName() : inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 锁定库存（SKU级别）
        int updatedRows = inventoryMapper.lockStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
             availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
            throw new InsufficientStockException(
                productId, 
                inventory.getSkuName() != null ? inventory.getSkuName() : inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 记录库存变动
        recordInventoryTransaction(
            productId, 
            null, // orderId，下单时还没有订单ID
            InventoryTransaction.TransactionType.LOCK, 
            quantity, 
            inventory.getCurrentStock(), 
            inventory.getCurrentStock(),
            inventory.getLockedStock(),
            inventory.getLockedStock() + quantity,
            "下单锁定库存（SKU级别，SKU ID: " + skuId + "）"
        );
        
        // 更新缓存
        inventory.setLockedStock(inventory.getLockedStock() + quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存锁定成功，商品ID: {}, SKU ID: {}, 锁定数量: {}", productId, skuId, quantity);
    }
    
    /**
     * 扣减库存（支付成功后调用，基于SKU）
     */
    @Transactional
    public void deductStock(Long productId, Long skuId, Integer quantity, Long orderId) {
        log.info("扣减库存，商品ID: {}, SKU ID: {}, 数量: {}, 订单ID: {}", productId, skuId, quantity, orderId);
        
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID不能为空");
        }
        
        // 查询SKU库存
        Inventory productInventory = inventoryMapper.selectByProductId(productId);
        if (productInventory == null) {
            log.error("商品不存在，商品ID: {}", productId);
            throw new RuntimeException("商品不存在");
        }
        
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
            productInventory.getStoreId(), productId, skuId);
        if (inventory == null) {
            log.error("SKU库存不存在，商品ID: {}, SKU ID: {}", productId, skuId);
            throw new RuntimeException("SKU库存不存在");
        }
        
        // 扣减库存（SKU级别）
        int updatedRows = inventoryMapper.deductStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
            log.error("库存扣减失败，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
            throw new RuntimeException("库存扣减失败");
        }
        
        // 记录库存变动
        recordInventoryTransaction(
            productId, 
            orderId, 
            InventoryTransaction.TransactionType.OUT, 
            quantity, 
            inventory.getCurrentStock() + quantity, 
            inventory.getCurrentStock(),
            inventory.getLockedStock(),
            inventory.getLockedStock() - quantity,
            "支付成功扣减库存（SKU级别，SKU ID: " + skuId + "）"
        );
        
        // 更新缓存
        inventory.setCurrentStock(inventory.getCurrentStock());
        inventory.setLockedStock(inventory.getLockedStock() - quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存扣减成功，商品ID: {}, SKU ID: {}, 扣减数量: {}", productId, skuId, quantity);
    }
    
    /**
     * 解锁库存（订单取消时调用，基于SKU）
     */
    @Transactional
    public void unlockStock(Long productId, Long skuId, Integer quantity, Long orderId) {
        log.info("解锁库存，商品ID: {}, SKU ID: {}, 数量: {}, 订单ID: {}", productId, skuId, quantity, orderId);
        
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID不能为空");
        }
        
        // 查询SKU库存
        Inventory productInventory = inventoryMapper.selectByProductId(productId);
        if (productInventory == null) {
            log.error("商品不存在，商品ID: {}", productId);
            throw new RuntimeException("商品不存在");
        }
        
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
            productInventory.getStoreId(), productId, skuId);
        if (inventory == null) {
            log.error("SKU库存不存在，商品ID: {}, SKU ID: {}", productId, skuId);
            throw new RuntimeException("SKU库存不存在");
        }
        
        // 解锁库存（SKU级别）
        int updatedRows = inventoryMapper.unlockStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
            log.error("库存解锁失败，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
            throw new RuntimeException("库存解锁失败");
        }
        
        // 记录库存变动
        recordInventoryTransaction(
            productId, 
            orderId, 
            InventoryTransaction.TransactionType.UNLOCK, 
            quantity, 
            inventory.getCurrentStock(), 
            inventory.getCurrentStock(),
            inventory.getLockedStock(),
            inventory.getLockedStock() - quantity,
            "订单取消解锁库存（SKU级别，SKU ID: " + skuId + "）"
        );
        
        // 更新缓存
        inventory.setLockedStock(inventory.getLockedStock() - quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存解锁成功，商品ID: {}, SKU ID: {}, 解锁数量: {}", productId, skuId, quantity);
    }
    
    /**
     * 批量检查并锁定库存（基于SKU）
     */
    @Transactional
    public void checkAndLockStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities) {
        log.info("批量检查并锁定库存，商品数量: {}", productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            throw new IllegalArgumentException("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        for (int i = 0; i < productIds.size(); i++) {
            checkAndLockStock(productIds.get(i), skuIds.get(i), quantities.get(i));
        }
        
        log.info("批量库存锁定完成");
    }
    
    /**
     * 批量扣减库存（基于SKU）
     */
    @Transactional
    public void deductStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId) {
        log.info("批量扣减库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            throw new IllegalArgumentException("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        for (int i = 0; i < productIds.size(); i++) {
            deductStock(productIds.get(i), skuIds.get(i), quantities.get(i), orderId);
        }
        
        log.info("批量库存扣减完成");
    }
    
    /**
     * 批量解锁库存（基于SKU）
     */
    @Transactional
    public void unlockStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId) {
        log.info("批量解锁库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            throw new IllegalArgumentException("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        for (int i = 0; i < productIds.size(); i++) {
            unlockStock(productIds.get(i), skuIds.get(i), quantities.get(i), orderId);
        }
        
        log.info("批量库存解锁完成");
    }
    
    /**
     * 根据商品ID查询库存（优先从缓存）
     */
    public Optional<Inventory> getInventoryByProductId(Long productId) {
        // 先从缓存获取
        Optional<Inventory> cachedInventory = inventoryCacheService.getInventoryByProductId(productId);
        if (cachedInventory.isPresent()) {
            log.debug("从缓存获取库存信息，商品ID: {}", productId);
            return cachedInventory;
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询库存信息，商品ID: {}", productId);
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        
        // 将查询结果缓存
        if (inventory != null) {
            inventoryCacheService.cacheInventory(inventory);
        }
        
        return Optional.ofNullable(inventory);
    }
    
    /**
     * 根据SKU ID查询库存
     */
    public Optional<Inventory> getInventoryBySkuId(Long storeId, Long productId, Long skuId) {
        log.debug("查询SKU库存，店铺ID: {}, 商品ID: {}, SKU ID: {}", storeId, productId, skuId);
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(storeId, productId, skuId);
        return Optional.ofNullable(inventory);
    }
    
    /**
     * 查询库存不足的商品（优先从缓存）
     */
    public List<Inventory> getLowStockItems() {
        // 先从缓存获取
        Optional<List<Inventory>> cachedItems = inventoryCacheService.getLowStockItems();
        if (cachedItems.isPresent()) {
            log.debug("从缓存获取库存不足商品列表");
            return cachedItems.get();
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询库存不足商品列表");
        List<Inventory> lowStockItems = inventoryMapper.selectLowStockItems();
        
        // 将查询结果缓存
        if (lowStockItems != null && !lowStockItems.isEmpty()) {
            inventoryCacheService.cacheLowStockItems(lowStockItems);
        }
        
        return lowStockItems;
    }
    
    /**
     * 记录库存变动
     */
    private void recordInventoryTransaction(Long productId, Long orderId, 
                                         InventoryTransaction.TransactionType transactionType,
                                         Integer quantity, Integer beforeStock, Integer afterStock,
                                         Integer beforeLocked, Integer afterLocked, String remark) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProductId(productId);
        transaction.setOrderId(orderId);
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setBeforeStock(beforeStock);
        transaction.setAfterStock(afterStock);
        transaction.setBeforeLocked(beforeLocked);
        transaction.setAfterLocked(afterLocked);
        transaction.setRemark(remark);
        
        transaction.setCreateTime(java.time.LocalDateTime.now());
        transactionMapper.insert(transaction);
    }
    
    /**
     * 刷新库存缓存（从数据库重新加载）
     */
    public void refreshInventoryCache(Long productId) {
        log.info("刷新库存缓存，商品ID: {}", productId);
        
        // 删除旧缓存
        inventoryCacheService.evictInventoryCache(productId);
        
        // 重新从数据库查询并缓存
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory != null) {
            inventoryCacheService.cacheInventory(inventory);
            log.info("库存缓存刷新成功，商品ID: {}", productId);
        } else {
            log.warn("商品不存在，无法刷新缓存，商品ID: {}", productId);
        }
    }
    
    /**
     * 刷新库存不足商品列表缓存
     */
    public void refreshLowStockItemsCache() {
        log.info("刷新库存不足商品列表缓存");
        
        // 删除旧缓存
        inventoryCacheService.evictLowStockItemsCache();
        
        // 重新从数据库查询并缓存
        List<Inventory> lowStockItems = inventoryMapper.selectLowStockItems();
        if (lowStockItems != null && !lowStockItems.isEmpty()) {
            inventoryCacheService.cacheLowStockItems(lowStockItems);
            log.info("库存不足商品列表缓存刷新成功，数量: {}", lowStockItems.size());
        } else {
            log.info("没有库存不足的商品");
        }
    }
    
    /**
     * 清空所有库存缓存
     */
    public void clearAllInventoryCache() {
        log.info("清空所有库存缓存");
        inventoryCacheService.clearAllInventoryCache();
    }
    
    /**
     * 检查库存缓存状态
     */
    public boolean hasInventoryCache(Long productId) {
        return inventoryCacheService.hasInventoryCache(productId);
    }
    
    /**
     * 获取库存缓存剩余时间
     */
    public Long getInventoryCacheExpireTime(Long productId) {
        return inventoryCacheService.getCacheExpireTime(productId);
    }
    
    /**
     * 获取所有库存记录（兼容前端 /api/inventory 接口）
     */
    public List<Inventory> getAllInventory() {
        log.info("获取所有库存记录");
        // 先从缓存获取
        Optional<List<Inventory>> cachedInventories = inventoryCacheService.getAllInventories();
        if (cachedInventories.isPresent()) {
            log.debug("从缓存获取所有库存信息");
            return cachedInventories.get();
        }
        
        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询所有库存信息");
        List<Inventory> inventories = inventoryMapper.selectAll();
        
        // 将查询结果缓存
        if (inventories != null && !inventories.isEmpty()) {
            inventoryCacheService.cacheAllInventories(inventories);
        }
        
        return inventories != null ? inventories : List.of();
    }
}

