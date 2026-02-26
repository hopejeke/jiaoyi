package com.jiaoyi.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.outbox.OutboxService;
import com.jiaoyi.product.config.RocketMQConfig;
import com.jiaoyi.product.dto.*;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.InventoryChannel;
import com.jiaoyi.product.entity.InventoryDeductionIdempotency;
import com.jiaoyi.product.entity.InventoryOversellRecord;
import com.jiaoyi.product.entity.InventoryTransaction;
import com.jiaoyi.product.mapper.sharding.InventoryChannelMapper;
import com.jiaoyi.product.mapper.sharding.InventoryDeductionIdempotencyMapper;
import com.jiaoyi.product.mapper.sharding.InventoryMapper;
import com.jiaoyi.product.mapper.sharding.InventoryOversellRecordMapper;
import com.jiaoyi.product.mapper.sharding.InventoryTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final InventoryDeductionIdempotencyMapper idempotencyMapper;
    private final InventoryChannelMapper channelMapper;
    private final InventoryOversellRecordMapper oversellRecordMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    private static final String OUTBOX_TYPE_STOCK_SYNC = "INVENTORY_STOCK_SYNC_MQ";
    private static final String ALLOCATION_MODE_WEIGHTED_QUOTA = "WEIGHTED_QUOTA";
    private static final String ALLOCATION_MODE_SAFETY_STOCK = "SAFETY_STOCK";
    private static final String DEDUCT_SOURCE_FROM_CHANNEL = "FROM_CHANNEL";
    private static final String DEDUCT_SOURCE_FROM_SHARED_POOL = "FROM_SHARED_POOL";
    private static final String DEDUCT_SOURCE_FROM_SAFETY_STOCK = "FROM_SAFETY_STOCK";
    private static final String DEDUCT_SOURCE_FROM_POOL = "FROM_POOL";
    
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
        inventory.setStockMode(Inventory.StockMode.UNLIMITED); // 默认无限库存（不设限制，类似美团商家不设库存的场景）
        inventory.setCurrentStock(0); // 默认库存为0（UNLIMITED模式下不使用）
        inventory.setLockedStock(0); // UNLIMITED模式下不使用
        inventory.setMinStock(0); // 默认最低库存预警线为0（UNLIMITED模式下不使用）
        inventory.setMaxStock(999999); // 最大库存容量（UNLIMITED模式下不使用）
        
        // 计算并设置 product_shard_id（基于 storeId，用于分库分表路由）
        int productShardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(storeId);
        inventory.setProductShardId(productShardId);
        
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
        return createInventoryForSku(storeId, productId, skuId, productName, skuName, 
                Inventory.StockMode.UNLIMITED, 0, 0, null);
    }
    
    public Inventory createInventoryForSku(Long storeId, Long productId, Long skuId, String productName, String skuName,
                                          Inventory.StockMode stockMode, Integer currentStock, Integer minStock, Integer maxStock) {
        log.info("创建SKU级别库存记录，店铺ID: {}, 商品ID: {}, SKU ID: {}, SKU名称: {}, 库存模式: {}", 
                storeId, productId, skuId, skuName, stockMode);
        
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
        inventory.setStockMode(stockMode != null ? stockMode : Inventory.StockMode.UNLIMITED);
        inventory.setCurrentStock(stockMode == Inventory.StockMode.LIMITED ? (currentStock != null ? currentStock : 0) : 0);
        inventory.setLockedStock(0);
        inventory.setMinStock(stockMode == Inventory.StockMode.LIMITED ? (minStock != null ? minStock : 0) : 0);
        inventory.setMaxStock(stockMode == Inventory.StockMode.LIMITED ? maxStock : null);
        
        // 计算并设置 product_shard_id（基于 storeId，用于分库分表路由）
        int productShardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(storeId);
        inventory.setProductShardId(productShardId);
        
        inventoryMapper.insert(inventory);
        log.info("SKU库存记录创建成功，库存ID: {}, 店铺ID: {}, 商品ID: {}, SKU ID: {}, 库存模式: {}", 
                inventory.getId(), storeId, productId, skuId, stockMode);
        
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
            inventory.getStockMode(), // 保持现有的库存模式
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
     * 库存扣减统一走渠道：deductByChannel / deductByChannelBatch（下单时按渠道扣）、returnStockByOrderId（取消时归还）。
     * 不再提供 lock/deduct/unlock 单独接口。
     */

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
     * 根据店铺ID查询所有库存
     */
    public List<Inventory> getInventoryByStoreId(Long storeId) {
        log.debug("查询店铺所有库存，店铺ID: {}", storeId);
        return inventoryMapper.selectByStoreId(storeId);
    }
    
    /**
     * 根据店铺ID和商品ID查询库存
     */
    public Optional<Inventory> getInventoryByStoreIdAndProductId(Long storeId, Long productId) {
        log.debug("查询商品库存，店铺ID: {}, 商品ID: {}", storeId, productId);
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
        return Optional.ofNullable(inventory);
    }
    
    /**
     * 根据商品ID查询所有SKU库存
     */
    public List<Inventory> getInventoryByProductIdWithSku(Long productId) {
        log.debug("查询商品所有SKU库存，商品ID: {}", productId);
        return inventoryMapper.selectByProductIdWithSku(productId);
    }
    
    /**
     * 根据店铺ID查询库存不足的商品
     */
    public List<Inventory> getLowStockItemsByStoreId(Long storeId) {
        log.debug("查询店铺库存不足商品，店铺ID: {}", storeId);
        return inventoryMapper.selectLowStockItemsByStoreId(storeId);
    }
    
    /**
     * 更新库存信息（根据ID）
     * 注意：由于 inventory 表是分片表（基于 store_id），需要先查询获取 store_id，然后使用 store_id 进行更新
     */
    @Transactional
    public Inventory updateInventory(Long inventoryId, String stockMode, Integer currentStock, Integer minStock, Integer maxStock) {
        log.info("更新库存信息，库存ID: {}, 库存模式参数: '{}', 当前库存: {}, 最低库存: {}, 最大库存: {}", 
                inventoryId, stockMode, currentStock, minStock, maxStock);
        
        // 先通过 store_id 查询现有库存（因为分片表需要 store_id 才能路由到正确的分片）
        // 由于 selectById 没有分片键，可能查询不到，所以先通过其他方式查询
        // 方案：先查询所有分片找到对应的库存记录（性能较差，但能保证正确性）
        // 更好的方案：前端传递 store_id，或者通过其他有分片键的查询先获取 store_id
        
        // 临时方案：通过 store_id 遍历查询（不推荐，但能工作）
        // 更好的方案：修改接口，要求前端传递 store_id 和 product_id
        Inventory inventory = null;
        
        // 尝试通过 selectAll 查询（会查询所有分片，性能较差）
        List<Inventory> allInventories = inventoryMapper.selectAll();
        for (Inventory inv : allInventories) {
            if (inv.getId().equals(inventoryId)) {
                inventory = inv;
                break;
            }
        }
        
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在，ID: " + inventoryId);
        }
        
        // 转换 stockMode 字符串为枚举（必须使用传入的值，不能使用现有值）
        Inventory.StockMode stockModeEnum;
        if (stockMode != null && !stockMode.trim().isEmpty()) {
            try {
                stockModeEnum = Inventory.StockMode.valueOf(stockMode.trim().toUpperCase());
                log.info("✓ 使用传入的库存模式: {}", stockModeEnum);
            } catch (IllegalArgumentException e) {
                log.error("✗ 无效的库存模式: '{}'，抛出异常", stockMode);
                throw new RuntimeException("无效的库存模式: " + stockMode + "，必须是 UNLIMITED 或 LIMITED");
            }
        } else {
            // 如果 stockMode 为 null 或空，抛出异常，不允许使用现有值（避免误更新）
            log.error("✗ 库存模式参数为空或null，不允许更新！现有值: {}", inventory.getStockMode());
            throw new RuntimeException("库存模式参数不能为空，必须明确指定 UNLIMITED 或 LIMITED");
        }
        
        log.info("✓ 最终使用的库存模式: {}", stockModeEnum);
        
        // 使用 store_id 和 product_id 进行更新（包含分片键）
        int updated = inventoryMapper.updateStock(
                inventory.getStoreId(),
                inventory.getProductId(),
                inventory.getSkuId(),
                stockModeEnum, // 确保不为 null
                currentStock,
                minStock,
                maxStock
        );
        
        if (updated == 0) {
            throw new RuntimeException("更新库存失败，库存ID: " + inventoryId);
        }
        
        // 重新查询更新后的库存（使用分片键）
        if (inventory.getSkuId() != null) {
            inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
                    inventory.getStoreId(),
                    inventory.getProductId(),
                    inventory.getSkuId()
            );
        } else {
            inventory = inventoryMapper.selectByStoreIdAndProductId(
                    inventory.getStoreId(),
                    inventory.getProductId()
            );
        }
        
        if (inventory == null) {
            throw new RuntimeException("更新后查询库存失败，库存ID: " + inventoryId);
        }
        
        // 更新缓存
        inventoryCacheService.updateInventoryCache(inventory);
        
        // 清除"所有库存列表"缓存，因为列表已变化
        inventoryCacheService.evictAllInventoriesCache();
        
        log.info("库存更新成功，库存ID: {}, 当前库存: {}", inventoryId, currentStock);
        return inventory;
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
     * 记录库存变动（使用 Inventory 对象，自动获取 product_shard_id）
     */
    private void recordInventoryTransaction(Inventory inventory, Long orderId, 
                                         InventoryTransaction.TransactionType transactionType,
                                         Integer quantity, Integer beforeStock, Integer afterStock,
                                         Integer beforeLocked, Integer afterLocked, String remark) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProductId(inventory.getProductId());
        transaction.setSkuId(inventory.getSkuId()); // 添加 SKU ID
        
        // 获取 product_shard_id，如果为 null 则根据 storeId 计算（兜底逻辑）
        Integer productShardId = inventory.getProductShardId();
        if (productShardId == null && inventory.getStoreId() != null) {
            productShardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(inventory.getStoreId());
            log.warn("库存记录的 product_shard_id 为空，根据 storeId {} 计算得到: {}", inventory.getStoreId(), productShardId);
        }
        if (productShardId == null) {
            throw new RuntimeException("无法确定 product_shard_id：inventory.getProductShardId() 为 null 且 inventory.getStoreId() 也为 null");
        }
        
        transaction.setProductShardId(productShardId);
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

    /**
     * 恢复库存（由定时任务调用）
     *
     * 功能：
     * 1. 查询库存记录
     * 2. 根据 restore_stock 恢复库存
     * 3. 更新 stock_mode 和 current_stock
     * 4. 记录 last_restore_time
     *
     * @param inventoryId 库存ID
     */
    @Transactional
    public void restoreInventory(Long inventoryId) {
        log.info("开始恢复库存，库存ID: {}", inventoryId);

        // 查询库存记录
        // 由于 inventory 表是分片表，需要先通过 selectAll 查询到记录
        List<Inventory> allInventories = inventoryMapper.selectAll();
        Inventory inventory = null;

        for (Inventory inv : allInventories) {
            if (inv.getId().equals(inventoryId)) {
                inventory = inv;
                break;
            }
        }

        if (inventory == null) {
            throw new RuntimeException("库存记录不存在，ID: " + inventoryId);
        }

        // 检查是否启用自动恢复
        if (inventory.getRestoreEnabled() == null || !inventory.getRestoreEnabled()) {
            log.warn("库存自动恢复未启用，跳过恢复，库存ID: {}", inventoryId);
            return;
        }

        // 根据 restore_stock 恢复库存
        Inventory.StockMode newStockMode;
        Integer newCurrentStock;

        if (inventory.getRestoreStock() == null) {
            // restore_stock 为 null，恢复为无限库存模式
            newStockMode = Inventory.StockMode.UNLIMITED;
            newCurrentStock = 0; // UNLIMITED 模式下库存数量不生效
            log.info("恢复为无限库存模式，库存ID: {}", inventoryId);
        } else {
            // restore_stock 不为 null，恢复为有限库存模式，并设置库存数量
            newStockMode = Inventory.StockMode.LIMITED;
            newCurrentStock = inventory.getRestoreStock();
            log.info("恢复为有限库存模式，库存ID: {}, 恢复库存数量: {}", inventoryId, newCurrentStock);
        }

        // 更新库存状态（使用CAS更新，防止并发重复恢复）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int updated = inventoryMapper.restoreInventory(
                inventoryId,
                newStockMode,
                newCurrentStock,
                now
        );

        // 并发安全：如果 updated == 0，说明CAS失败（已被其他线程/实例恢复过）
        // 这是正常的并发场景，不抛异常，直接返回
        if (updated == 0) {
            log.warn("库存恢复CAS失败（可能已被其他线程恢复），库存ID: {}", inventoryId);
            return;
        }

        log.info("库存恢复成功，库存ID: {}, 新模式: {}, 新库存: {}, 恢复时间: {}",
                inventoryId, newStockMode, newCurrentStock, now);

        // 更新缓存
        // 重新查询更新后的库存
        if (inventory.getSkuId() != null) {
            inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
                    inventory.getStoreId(),
                    inventory.getProductId(),
                    inventory.getSkuId()
            );
        } else {
            inventory = inventoryMapper.selectByStoreIdAndProductId(
                    inventory.getStoreId(),
                    inventory.getProductId()
            );
        }

        if (inventory != null) {
            inventoryCacheService.updateInventoryCache(inventory);
        }
    }

    /**
     * 更新库存恢复配置
     *
     * @param inventoryId 库存ID
     * @param restoreMode 恢复模式
     * @param restoreTime 恢复时间（仅在 SCHEDULED 模式下有效）
     * @param restoreStock 恢复后的库存数量（null表示恢复为无限库存）
     * @param restoreEnabled 是否启用自动恢复
     */
    @Transactional
    public void updateRestoreConfig(Long inventoryId, Inventory.RestoreMode restoreMode,
                                   java.time.LocalDateTime restoreTime, Integer restoreStock,
                                   Boolean restoreEnabled) {
        log.info("更新库存恢复配置，库存ID: {}, 模式: {}, 恢复时间: {}, 恢复库存: {}, 启用: {}",
                inventoryId, restoreMode, restoreTime, restoreStock, restoreEnabled);

        // 参数校验
        if (restoreMode == null) {
            throw new IllegalArgumentException("恢复模式不能为空");
        }

        if (restoreMode == Inventory.RestoreMode.SCHEDULED && restoreTime == null) {
            throw new IllegalArgumentException("指定时间恢复模式下，恢复时间不能为空");
        }

        if (restoreEnabled == null) {
            restoreEnabled = false;
        }

        // 更新配置
        int updated = inventoryMapper.updateRestoreConfig(
                inventoryId,
                restoreMode,
                restoreTime,
                restoreStock,
                restoreEnabled
        );

        if (updated == 0) {
            throw new RuntimeException("更新库存恢复配置失败，库存ID: " + inventoryId);
        }

        log.info("库存恢复配置更新成功，库存ID: {}", inventoryId);

        // 清除缓存
        inventoryCacheService.evictAllInventoriesCache();
    }

    // ========================= POI 渠道库存功能（统一在 Inventory 实现） =========================

    /**
     * 辅助方法：根据 storeId + productId + skuId 查找 Inventory
     */
    private Inventory findInventoryByRequest(Long storeId, Long productId, Long skuId) {
        if (skuId != null) {
            return inventoryMapper.selectByStoreIdAndProductIdAndSkuId(storeId, productId, skuId);
        }
        return inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
    }

    /**
     * 接收POS上报库存变更（POS在线时）
     */
    @Transactional
    public void syncFromPos(StockSyncFromPosRequest request) {
        log.info("接收POS上报库存变更: storeId={}, productId={}, skuId={}",
            request.getStoreId(), request.getProductId(), request.getSkuId());

        Inventory inventory = findInventoryByRequest(
            request.getStoreId(), request.getProductId(), request.getSkuId());

        if (inventory != null && request.getUpdatedAt() != null) {
            if (!inventory.getUpdateTime().equals(request.getUpdatedAt())) {
                log.warn("库存已被其他操作修改: inventoryId={}", inventory.getId());
                throw new RuntimeException("库存已被其他操作修改，请刷新后重试");
            }
        }

        LocalDateTime now = LocalDateTime.now();

        if (inventory == null) {
            int shardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(request.getStoreId());
            inventory = new Inventory();
            inventory.setStoreId(request.getStoreId());
            inventory.setProductId(request.getProductId());
            inventory.setSkuId(request.getSkuId());
            inventory.setProductShardId(shardId);
            inventory.setStockMode(request.getStockType() != null && request.getStockType() == 1
                ? Inventory.StockMode.UNLIMITED : Inventory.StockMode.LIMITED);
            inventory.setCurrentStock(request.getRealQuantity() != null
                ? request.getRealQuantity().intValue() : 0);
            inventory.setPlanQuantity(request.getPlanQuantity() != null
                ? request.getPlanQuantity() : BigDecimal.ZERO);
            inventory.setLockedStock(0);
            inventory.setMinStock(0);
            inventory.setCreateTime(now);
            inventory.setUpdateTime(now);
            inventoryMapper.insert(inventory);
        } else {
            int rows = inventoryMapper.forceUpdateCurrentStock(
                inventory.getId(),
                request.getRealQuantity() != null ? request.getRealQuantity() : BigDecimal.ZERO,
                inventory.getLastManualSetTime(),
                now
            );
            if (rows == 0) {
                throw new RuntimeException("库存更新失败，可能已被其他操作修改");
            }
        }

        // 更新渠道库存
        if (request.getChannelStocks() != null && !request.getChannelStocks().isEmpty()) {
            channelMapper.deleteByInventoryId(inventory.getId());
            for (StockSyncFromPosRequest.ChannelStock cs : request.getChannelStocks()) {
                InventoryChannel channel = new InventoryChannel();
                channel.setInventoryId(inventory.getId());
                channel.setStoreId(request.getStoreId());
                channel.setProductId(request.getProductId());
                channel.setSkuId(request.getSkuId());
                channel.setProductShardId(inventory.getProductShardId());
                channel.setStockStatus(cs.getStockStatus());
                channel.setStockType(cs.getStockType());
                channel.setChannelCode(cs.getChannelCode());
                channel.setCreatedAt(now);
                channel.setUpdatedAt(now);
                channelMapper.insert(channel);
            }
        }

        // 写变更日志
        writePoiLog(inventory, "ABSOLUTE_SET", BigDecimal.ZERO, "POS", null, request);
    }

    /**
     * 商品中心设置库存（会同步到POS）
     */
    @Transactional
    public void updateStockFromCloud(StockSyncFromPosRequest request) {
        log.info("商品中心设置库存: storeId={}, productId={}, skuId={}",
            request.getStoreId(), request.getProductId(), request.getSkuId());

        syncFromPos(request);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sendStockSyncToOutbox(request);
                } catch (Exception e) {
                    log.error("库存已更新但写入outbox失败: storeId={}, productId={}",
                        request.getStoreId(), request.getProductId(), e);
                }
            }
        });
    }

    /**
     * POS 离线事件批量回放
     */
    @Transactional
    public PosOfflineReplayResult replayOfflineEvents(PosOfflineReplayRequest request) {
        log.info("开始POS离线事件回放: storeId={}, posInstance={}, events={}",
            request.getStoreId(), request.getPosInstanceId(), request.getEvents().size());

        PosOfflineReplayResult result = new PosOfflineReplayResult();
        result.setTotalEvents(request.getEvents().size());

        int successCount = 0, skippedCount = 0, failedCount = 0;

        for (StockChangeEvent event : request.getEvents()) {
            if (event.getStoreId() == null) event.setStoreId(request.getStoreId());
            event.setSource(StockChangeEvent.Source.POS_OFFLINE);

            try {
                boolean applied = applyStockChangeEvent(event);
                if (applied) successCount++; else skippedCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("离线事件回放失败: orderId={}, error={}", event.getOrderId(), e.getMessage());
                result.getFailedEvents().add(
                    new PosOfflineReplayResult.FailedEvent(event.getOrderId(), e.getMessage()));
            }
        }

        result.setSuccessCount(successCount);
        result.setSkippedCount(skippedCount);
        result.setFailedCount(failedCount);

        if (successCount > 0) {
            detectOversellAfterReplay(request, result);
        }

        log.info("POS离线事件回放完成: total={}, success={}, skipped={}, failed={}, oversell={}",
            result.getTotalEvents(), successCount, skippedCount, failedCount, result.isOversellDetected());

        return result;
    }

    private boolean applyStockChangeEvent(StockChangeEvent event) {
        Inventory inventory = findInventoryByRequest(event.getStoreId(), event.getProductId(), event.getSkuId());
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在: productId=" + event.getProductId() + ", skuId=" + event.getSkuId());
        }
        switch (event.getChangeType()) {
            case RELATIVE_DELTA: return handleRelativeDelta(inventory, event);
            case ABSOLUTE_SET:   return handleAbsoluteSet(inventory, event);
            case STATUS_CHANGE:  return handleStatusChange(inventory, event);
            default: throw new RuntimeException("未知变更类型: " + event.getChangeType());
        }
    }

    /**
     * 渠道级别库存扣减（单品）
     */
    @Transactional
    public ChannelDeductResult deductByChannel(ChannelDeductRequest request) {
        log.info("渠道库存扣减: storeId={}, productId={}, skuId={}, channel={}, qty={}, orderId={}",
            request.getStoreId(), request.getProductId(), request.getSkuId(),
            request.getChannelCode(), request.getQuantity(), request.getOrderId());

        Inventory inventory = findInventoryByRequest(
            request.getStoreId(), request.getProductId(), request.getSkuId());
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在: productId=" + request.getProductId());
        }

        // 幂等检查
        if (request.getOrderId() != null) {
            int count = transactionMapper.countDeductByOrderIdAndInventoryId(
                request.getOrderId(), inventory.getId());
            if (count > 0) {
                log.info("重复扣减请求，跳过: orderId={}, inventoryId={}", request.getOrderId(), inventory.getId());
                return ChannelDeductResult.duplicate();
            }
        }

        // 不限量直接通过
        if (inventory.getStockMode() == Inventory.StockMode.UNLIMITED) {
            writeDeductLog(inventory, request, "UNLIMITED_PASS");
            return ChannelDeductResult.success(ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL, null, null);
        }

        String mode = inventory.getAllocationMode() != null
            ? inventory.getAllocationMode() : ALLOCATION_MODE_WEIGHTED_QUOTA;
        if (ALLOCATION_MODE_SAFETY_STOCK.equalsIgnoreCase(mode)) {
            return deductBySafetyStock(request, inventory);
        }
        return deductByWeightedQuota(request, inventory);
    }

    private ChannelDeductResult deductByWeightedQuota(ChannelDeductRequest request, Inventory inventory) {
        BigDecimal delta = request.getQuantity();
        InventoryChannel channelStock = channelMapper.selectByInventoryIdAndChannel(
            inventory.getId(), request.getChannelCode());

        Inventory locked = inventoryMapper.selectByIdForUpdate(inventory.getId());
        if (locked == null) throw new RuntimeException("库存记录不存在: id=" + inventory.getId());

        BigDecimal realQty = locked.getCurrentStock() != null
            ? new BigDecimal(locked.getCurrentStock()) : BigDecimal.ZERO;
        if (realQty.compareTo(delta) < 0) {
            log.warn("库存不足: inventoryId={}, real={}, need={}", inventory.getId(), realQty, delta);
            return ChannelDeductResult.outOfStock(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (channelStock != null) {
            int updated = channelMapper.atomicIncreaseChannelSoldWithCap(
                inventory.getId(), request.getChannelCode(), delta);
            BigDecimal cap = channelStock.getChannelMax();
            if (cap != null && cap.compareTo(BigDecimal.ZERO) > 0 && updated == 0) {
                BigDecimal sold = channelStock.getChannelSold() != null ? channelStock.getChannelSold() : BigDecimal.ZERO;
                return new ChannelDeductResult(
                    ChannelDeductResult.Status.CHANNEL_OUT_OF_STOCK,
                    cap.subtract(sold), BigDecimal.ZERO, "超过该渠道可售上限");
            }
        }

        int deducted = inventoryMapper.atomicDeductCurrentStock(inventory.getId(), delta);
        if (deducted == 0) {
            return ChannelDeductResult.outOfStock(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        String deductSource = channelStock != null ? DEDUCT_SOURCE_FROM_CHANNEL : DEDUCT_SOURCE_FROM_POOL;
        writeDeductLog(inventory, request, deductSource);

        InventoryChannel updatedCh = channelMapper.selectByInventoryIdAndChannel(
            inventory.getId(), request.getChannelCode());
        BigDecimal channelRemaining = BigDecimal.ZERO;
        if (updatedCh != null && updatedCh.getChannelMax() != null && updatedCh.getChannelMax().compareTo(BigDecimal.ZERO) > 0) {
            channelRemaining = updatedCh.getChannelMax().subtract(
                updatedCh.getChannelSold() != null ? updatedCh.getChannelSold() : BigDecimal.ZERO);
        }
        Inventory after = findInventoryByRequest(request.getStoreId(), request.getProductId(), request.getSkuId());
        BigDecimal sharedPool = after != null && after.getSharedPoolQuantity() != null
            ? after.getSharedPoolQuantity() : BigDecimal.ZERO;
        return ChannelDeductResult.success(
            ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL, channelRemaining, sharedPool);
    }

    private ChannelDeductResult deductBySafetyStock(ChannelDeductRequest request, Inventory inventory) {
        InventoryChannel myChannel = channelMapper.selectByInventoryIdAndChannel(
            inventory.getId(), request.getChannelCode());
        int myPriority = (myChannel != null && myChannel.getChannelPriority() != null)
            ? myChannel.getChannelPriority() : 0;
        BigDecimal reservedByHigher = channelMapper.sumSafetyStockForHigherPriority(inventory.getId(), myPriority);
        if (reservedByHigher == null) reservedByHigher = BigDecimal.ZERO;

        Inventory locked = inventoryMapper.selectByIdForUpdate(inventory.getId());
        if (locked == null) throw new RuntimeException("库存记录不存在: id=" + inventory.getId());

        BigDecimal realQty = locked.getCurrentStock() != null
            ? new BigDecimal(locked.getCurrentStock()) : BigDecimal.ZERO;
        BigDecimal availableForMe = realQty.subtract(reservedByHigher);
        BigDecimal delta = request.getQuantity();
        if (availableForMe.compareTo(delta) < 0) {
            return ChannelDeductResult.outOfStock(availableForMe, BigDecimal.ZERO);
        }
        int updated = inventoryMapper.atomicDeductCurrentStockWithFloor(
            inventory.getId(), delta, reservedByHigher);
        if (updated == 0) {
            return ChannelDeductResult.outOfStock(availableForMe, BigDecimal.ZERO);
        }
        writeDeductLog(inventory, request, DEDUCT_SOURCE_FROM_SAFETY_STOCK);
        Inventory after = findInventoryByRequest(request.getStoreId(), request.getProductId(), request.getSkuId());
        BigDecimal sharedPool = after != null && after.getSharedPoolQuantity() != null
            ? after.getSharedPoolQuantity() : BigDecimal.ZERO;
        return ChannelDeductResult.success(
            ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL, availableForMe.subtract(delta), sharedPool);
    }

    /**
     * 按渠道批量扣减（一单多品）
     */
    @Transactional
    public void deductByChannelBatch(ChannelDeductBatchRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) return;
        for (ChannelDeductBatchRequest.Item item : request.getItems()) {
            ChannelDeductRequest req = new ChannelDeductRequest(
                request.getStoreId(), item.getProductId(), item.getSkuId(),
                request.getChannelCode(), item.getQuantity(), request.getOrderId());
            ChannelDeductResult res = deductByChannel(req);
            if (res.isSuccess() || res.getStatus() == ChannelDeductResult.Status.DUPLICATE) continue;
            throw new RuntimeException("库存扣减失败: " +
                (res.getMessage() != null ? res.getMessage() : res.getStatus().name()));
        }
    }

    /**
     * 按权重重新分配渠道额度
     */
    @Transactional
    public void allocateChannelQuotas(Long storeId, Long productId, Long skuId) {
        log.info("开始渠道额度分配: storeId={}, productId={}, skuId={}", storeId, productId, skuId);

        Inventory inventory = findInventoryByRequest(storeId, productId, skuId);
        if (inventory == null) throw new RuntimeException("库存记录不存在");

        if (inventory.getStockMode() == Inventory.StockMode.UNLIMITED) {
            log.info("不限量库存，跳过渠道额度分配");
            return;
        }

        List<InventoryChannel> channels = channelMapper.selectByInventoryId(inventory.getId());
        if (channels.isEmpty()) {
            log.info("没有渠道库存记录，跳过分配");
            return;
        }

        BigDecimal totalQuantity = inventory.getCurrentStock() != null
            ? new BigDecimal(inventory.getCurrentStock()) : BigDecimal.ZERO;
        if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            for (InventoryChannel ch : channels) {
                channelMapper.updateChannelQuotaAndWeight(ch.getId(), BigDecimal.ZERO, ch.getChannelWeight());
            }
            inventoryMapper.updateSharedPoolQuantity(inventory.getId(), BigDecimal.ZERO);
            return;
        }

        BigDecimal totalWeight = channels.stream()
            .map(InventoryChannel::getChannelWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            totalWeight = new BigDecimal(channels.size());
            for (InventoryChannel ch : channels) ch.setChannelWeight(BigDecimal.ONE);
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (InventoryChannel ch : channels) {
            BigDecimal quota = totalQuantity
                .multiply(ch.getChannelWeight())
                .divide(totalWeight, 1, RoundingMode.DOWN);
            channelMapper.updateChannelQuotaAndWeight(ch.getId(), quota, ch.getChannelWeight());
            allocated = allocated.add(quota);
        }

        BigDecimal sharedPool = totalQuantity.subtract(allocated);
        inventoryMapper.updateSharedPoolQuantity(inventory.getId(), sharedPool);
        channelMapper.resetChannelSold(inventory.getId());

        log.info("渠道额度分配完成: total={}, allocated={}, sharedPool={}", totalQuantity, allocated, sharedPool);
    }

    /**
     * 设置渠道库存分配模式
     */
    @Transactional
    public void setAllocationMode(Long storeId, Long productId, Long skuId, String allocationMode) {
        Inventory inventory = findInventoryByRequest(storeId, productId, skuId);
        if (inventory == null) throw new RuntimeException("库存记录不存在");
        if (!ALLOCATION_MODE_WEIGHTED_QUOTA.equalsIgnoreCase(allocationMode)
            && !ALLOCATION_MODE_SAFETY_STOCK.equalsIgnoreCase(allocationMode)) {
            throw new IllegalArgumentException("allocationMode 必须为 WEIGHTED_QUOTA 或 SAFETY_STOCK");
        }
        inventoryMapper.updateAllocationMode(inventory.getId(), allocationMode.toUpperCase());
        log.info("分配模式已更新: inventoryId={}, mode={}", inventory.getId(), allocationMode);
    }

    /**
     * 更新渠道优先级与安全线（方案二 SAFETY_STOCK 用）
     */
    @Transactional
    public void updateChannelPriorityAndSafetyStock(Long channelId, Integer channelPriority, BigDecimal safetyStock) {
        channelMapper.updatePriorityAndSafetyStock(
            channelId,
            channelPriority != null ? channelPriority : 0,
            safetyStock != null ? safetyStock : BigDecimal.ZERO);
        log.info("渠道优先级/安全线已更新: channelId={}, priority={}, safetyStock={}", channelId, channelPriority, safetyStock);
    }

    /**
     * 按订单ID归还库存（订单取消时调用）
     */
    @Transactional
    public void returnStockByOrderId(String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        List<InventoryTransaction> deductLogs = transactionMapper.selectDeductLogsByOrderId(orderId);
        if (deductLogs.isEmpty()) {
            log.info("归还跳过：无扣减记录 orderId={}", orderId);
            return;
        }
        for (InventoryTransaction logRow : deductLogs) {
            Long inventoryId = logRow.getInventoryId();
            int alreadyReturned = transactionMapper.countReturnByOrderIdAndInventoryId(orderId, inventoryId);
            if (alreadyReturned > 0) {
                log.info("归还幂等跳过: orderId={}, inventoryId={}", orderId, inventoryId);
                continue;
            }
            BigDecimal returnQty = logRow.getDelta() != null ? logRow.getDelta().abs() : BigDecimal.ZERO;
            if (returnQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            String deductSource = logRow.getDeductSource();
            String channelCode = logRow.getChannelCode();

            if (DEDUCT_SOURCE_FROM_CHANNEL.equals(deductSource) && channelCode != null && !channelCode.isEmpty()) {
                int chUpdated = channelMapper.atomicDecreaseChannelSold(inventoryId, channelCode, returnQty);
                if (chUpdated == 0) {
                    log.warn("归还渠道失败: orderId={}, inventoryId={}, channel={}", orderId, inventoryId, channelCode);
                    continue;
                }
                inventoryMapper.atomicIncreaseCurrentStock(inventoryId, returnQty);
            } else if (DEDUCT_SOURCE_FROM_SAFETY_STOCK.equals(deductSource)
                    || DEDUCT_SOURCE_FROM_POOL.equals(deductSource)) {
                inventoryMapper.atomicIncreaseCurrentStock(inventoryId, returnQty);
            } else {
                log.debug("归还跳过: orderId={}, inventoryId={}, deductSource={}", orderId, inventoryId, deductSource);
                continue;
            }
            writeReturnLog(logRow, returnQty, orderId);
        }
        log.info("按订单归还完成: orderId={}, 条数={}", orderId, deductLogs.size());
    }

    /**
     * 查询超卖记录
     */
    public List<InventoryOversellRecord> getOversellRecords(Long storeId, String status) {
        if (status != null && !status.isEmpty()) {
            return oversellRecordMapper.selectByStoreIdAndStatus(storeId, status);
        }
        return oversellRecordMapper.selectByStoreId(storeId);
    }

    /**
     * 处理超卖记录（店长确认）
     */
    @Transactional
    public void resolveOversellRecord(Long recordId, String status, String resolvedBy, String remark) {
        oversellRecordMapper.updateStatus(recordId, status, resolvedBy, LocalDateTime.now(), remark);
        log.info("超卖记录已处理: id={}, status={}, by={}", recordId, status, resolvedBy);
    }

    /**
     * 查询库存详情（附带渠道信息）
     */
    public Inventory getInventoryWithChannels(Long storeId, Long productId, Long skuId) {
        return findInventoryByRequest(storeId, productId, skuId);
    }

    /**
     * 查询库存变更记录（POI 库存日志）
     */
    public List<InventoryTransaction> getInventoryTransactionLogs(Long inventoryId, Integer limit) {
        int limitVal = limit != null && limit > 0 ? limit : 100;
        return transactionMapper.selectByInventoryId(inventoryId, limitVal);
    }

    // ========================= 私有辅助方法 =========================

    private boolean handleRelativeDelta(Inventory inventory, StockChangeEvent event) {
        if (event.getOrderId() != null) {
            int count = transactionMapper.countDeductByOrderIdAndInventoryId(
                event.getOrderId(), inventory.getId());
            if (count > 0) {
                log.info("相对变更幂等跳过: orderId={}", event.getOrderId());
                return false;
            }
        }

        BigDecimal absDelta = event.getDelta().abs();
        boolean isDeduct = event.getDelta().compareTo(BigDecimal.ZERO) < 0;

        if (isDeduct) {
            int updated = inventoryMapper.atomicDeductCurrentStock(inventory.getId(), absDelta);
            if (updated == 0) {
                BigDecimal current = inventory.getCurrentStock() != null
                    ? new BigDecimal(inventory.getCurrentStock()) : BigDecimal.ZERO;
                inventoryMapper.forceUpdateCurrentStock(
                    inventory.getId(), current.add(event.getDelta()),
                    inventory.getLastManualSetTime(), LocalDateTime.now());
            }
        } else {
            BigDecimal current = inventory.getCurrentStock() != null
                ? new BigDecimal(inventory.getCurrentStock()) : BigDecimal.ZERO;
            inventoryMapper.forceUpdateCurrentStock(
                inventory.getId(), current.add(event.getDelta()),
                inventory.getLastManualSetTime(), LocalDateTime.now());
        }

        Inventory updated = findInventoryByRequest(event.getStoreId(), event.getProductId(), event.getSkuId());
        if (updated != null && updated.getCurrentStock() != null && updated.getCurrentStock() <= 0) {
            inventoryMapper.updateStockStatusDirect(inventory.getId(), 2, LocalDateTime.now());
        }

        writePoiEventLog(inventory, event);
        return true;
    }

    private boolean handleAbsoluteSet(Inventory inventory, StockChangeEvent event) {
        Inventory lockedInv = inventoryMapper.selectByIdForUpdate(inventory.getId());
        if (lockedInv.getLastManualSetTime() != null && event.getOperateTime() != null) {
            if (!event.getOperateTime().isAfter(lockedInv.getLastManualSetTime())) {
                log.info("绝对设置已过期，跳过: operateTime={}, lastManualSetTime={}",
                    event.getOperateTime(), lockedInv.getLastManualSetTime());
                return false;
            }
        }

        LocalDateTime sinceTime = event.getOperateTime() != null
            ? event.getOperateTime() : lockedInv.getUpdateTime();
        BigDecimal deltaSinceT1 = transactionMapper.sumDeltaSince(inventory.getId(), sinceTime);
        BigDecimal finalQuantity = event.getNewQuantity().add(deltaSinceT1 != null ? deltaSinceT1 : BigDecimal.ZERO);

        log.info("绝对设置冲突合并: newQty={}, deltaSinceT1={}, finalQty={}",
            event.getNewQuantity(), deltaSinceT1, finalQuantity);

        LocalDateTime manualSetTime = event.getOperateTime() != null ? event.getOperateTime() : LocalDateTime.now();
        inventoryMapper.forceUpdateCurrentStock(inventory.getId(), finalQuantity, manualSetTime, LocalDateTime.now());

        writePoiEventLog(inventory, event);
        return true;
    }

    private boolean handleStatusChange(Inventory inventory, StockChangeEvent event) {
        inventoryMapper.updateStockStatusDirect(inventory.getId(), event.getNewStockStatus(), LocalDateTime.now());
        writePoiEventLog(inventory, event);
        return true;
    }

    private void detectOversellAfterReplay(PosOfflineReplayRequest request, PosOfflineReplayResult result) {
        List<Long> productIds = request.getEvents().stream()
            .map(StockChangeEvent::getProductId).distinct().collect(Collectors.toList());
        List<Long> skuIds = request.getEvents().stream()
            .map(StockChangeEvent::getSkuId).distinct().collect(Collectors.toList());

        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            Long skuId = i < skuIds.size() ? skuIds.get(i) : null;
            Inventory inventory = findInventoryByRequest(request.getStoreId(), productId, skuId);

            if (inventory != null && inventory.getCurrentStock() != null && inventory.getCurrentStock() < 0) {
                BigDecimal oversellQty = new BigDecimal(-inventory.getCurrentStock());
                log.warn("检测到超卖: productId={}, skuId={}, oversellQty={}", productId, skuId, oversellQty);

                InventoryOversellRecord record = new InventoryOversellRecord();
                record.setInventoryId(inventory.getId());
                record.setStoreId(request.getStoreId());
                record.setProductId(productId);
                record.setSkuId(skuId);
                record.setProductShardId(inventory.getProductShardId());
                record.setOversellQuantity(oversellQty);
                record.setSource("POS_OFFLINE");
                record.setStatus(InventoryOversellRecord.Status.PENDING.name());
                record.setRemark("POS离线回放后检测到超卖，POS实例: " + request.getPosInstanceId());
                record.setCreatedAt(LocalDateTime.now());
                oversellRecordMapper.insert(record);

                inventoryMapper.forceUpdateCurrentStock(inventory.getId(), BigDecimal.ZERO,
                    inventory.getLastManualSetTime(), LocalDateTime.now());
                inventoryMapper.updateStockStatusDirect(inventory.getId(), 2, LocalDateTime.now());

                result.setOversellDetected(true);
                result.setOversellQuantity(oversellQty);
                result.setOversellRecordId(record.getId());
            }
        }
    }

    private void writePoiLog(Inventory inventory, String changeType, BigDecimal delta,
                              String sourcePoi, String orderId, Object content) {
        try {
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(inventory.getId());
            tx.setProductId(inventory.getProductId());
            tx.setSkuId(inventory.getSkuId());
            tx.setProductShardId(inventory.getProductShardId());
            tx.setChangeTypePoi(changeType);
            tx.setDelta(delta);
            tx.setSourcePoi(sourcePoi);
            if (orderId != null) {
                try { tx.setOrderId(Long.parseLong(orderId)); } catch (NumberFormatException ignored) {}
            }
            tx.setContent(objectMapper.writeValueAsString(content));
            tx.setCreateTime(LocalDateTime.now());
            transactionMapper.insert(tx);
        } catch (Exception e) {
            log.error("写入变更记录失败", e);
            throw new RuntimeException("写入变更记录失败", e);
        }
    }

    private void writePoiEventLog(Inventory inventory, StockChangeEvent event) {
        writePoiLog(inventory, event.getChangeType().name(),
            event.getDelta() != null ? event.getDelta() : BigDecimal.ZERO,
            event.getSource() != null ? event.getSource().name() : "CLOUD",
            event.getOrderId(), event);
    }

    private void writeDeductLog(Inventory inventory, ChannelDeductRequest request, String deductSource) {
        try {
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(inventory.getId());
            tx.setProductId(inventory.getProductId());
            tx.setSkuId(inventory.getSkuId());
            tx.setProductShardId(inventory.getProductShardId());
            tx.setChangeTypePoi("RELATIVE_DELTA");
            tx.setDelta(request.getQuantity().negate());
            tx.setSourcePoi("CLOUD");
            tx.setDeductSource(deductSource);
            tx.setChannelCode(request.getChannelCode());
            if (request.getOrderId() != null) {
                try { tx.setOrderId(Long.parseLong(request.getOrderId())); } catch (NumberFormatException ignored) {}
            }
            tx.setContent(objectMapper.writeValueAsString(request));
            tx.setCreateTime(LocalDateTime.now());
            transactionMapper.insert(tx);
        } catch (Exception e) {
            log.error("写入扣减记录失败", e);
            throw new RuntimeException("写入扣减记录失败", e);
        }
    }

    private void writeReturnLog(InventoryTransaction deductLog, BigDecimal returnQty, String orderId) {
        try {
            java.util.Map<String, Object> content = new java.util.HashMap<>();
            content.put("reason", "order_cancel_return");
            content.put("orderId", orderId);
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(deductLog.getInventoryId());
            tx.setProductId(deductLog.getProductId());
            tx.setSkuId(deductLog.getSkuId());
            tx.setProductShardId(deductLog.getProductShardId());
            tx.setChangeTypePoi("RETURN");
            tx.setDelta(returnQty);
            tx.setSourcePoi("CLOUD");
            tx.setDeductSource(deductLog.getDeductSource());
            tx.setChannelCode(deductLog.getChannelCode());
            if (orderId != null) {
                try { tx.setOrderId(Long.parseLong(orderId)); } catch (NumberFormatException ignored) {}
            }
            tx.setContent(objectMapper.writeValueAsString(content));
            tx.setCreateTime(LocalDateTime.now());
            transactionMapper.insert(tx);
        } catch (Exception e) {
            log.error("写入归还记录失败", e);
            throw new RuntimeException("写入归还记录失败", e);
        }
    }

    private void sendStockSyncToOutbox(StockSyncFromPosRequest request) {
        try {
            StockSyncToPosMessage message = buildSyncToPosMessage(request);
            String payload = objectMapper.writeValueAsString(message);
            String bizKey = request.getStoreId() + ":" + request.getProductId() + ":" + System.currentTimeMillis();
            String messageKey = "stock-sync:" + request.getStoreId() + ":" + request.getProductId();
            String shardingKey = String.valueOf(request.getStoreId());

            outboxService.enqueue(
                OUTBOX_TYPE_STOCK_SYNC, bizKey, payload,
                RocketMQConfig.INVENTORY_STOCK_SYNC_TOPIC,
                RocketMQConfig.INVENTORY_STOCK_SYNC_TAG,
                messageKey, shardingKey, null
            );
            log.info("库存变更已写入outbox: bizKey={}", bizKey);
        } catch (Exception e) {
            log.error("构建库存同步消息失败", e);
            throw new RuntimeException("构建库存同步消息失败", e);
        }
    }

    private StockSyncToPosMessage buildSyncToPosMessage(StockSyncFromPosRequest request) {
        StockSyncToPosMessage message = new StockSyncToPosMessage();
        message.setStoreId(request.getStoreId());

        StockSyncToPosMessage.StockData data = new StockSyncToPosMessage.StockData();
        data.setProductId(request.getProductId());
        data.setSkuId(request.getSkuId());
        data.setStockStatus(request.getStockStatus());
        data.setStockType(request.getStockType());
        data.setPlanQuantity(request.getPlanQuantity());
        data.setRealQuantity(request.getRealQuantity());
        data.setAutoRestoreType(request.getAutoRestoreType());
        data.setAutoRestoreAt(request.getAutoRestoreAt());

        if (request.getChannelStocks() != null) {
            List<StockSyncToPosMessage.ChannelStock> channelStocks = request.getChannelStocks().stream()
                .map(cs -> {
                    StockSyncToPosMessage.ChannelStock ch = new StockSyncToPosMessage.ChannelStock();
                    ch.setStockStatus(cs.getStockStatus());
                    ch.setStockType(cs.getStockType());
                    ch.setChannelCode(cs.getChannelCode());
                    return ch;
                }).collect(Collectors.toList());
            data.setChannelStocks(channelStocks);
        }
        message.setData(data);
        return message;
    }
}

