package com.jiaoyi.service;

import com.jiaoyi.entity.Inventory;
import com.jiaoyi.entity.InventoryTransaction;
import com.jiaoyi.entity.OrderItem;
import com.jiaoyi.exception.InsufficientStockException;
import com.jiaoyi.mapper.InventoryMapper;
import com.jiaoyi.mapper.InventoryTransactionMapper;
import com.jiaoyi.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 库存服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final OrderItemMapper orderItemMapper;
    private final InventoryCacheService inventoryCacheService;
    
    /**
     * 创建库存记录（商品创建时调用）
     */
    @Transactional
    public Inventory createInventory(Long storeId, Long productId, String productName) {
        log.info("创建库存记录，店铺ID: {}, 商品ID: {}, 商品名称: {}", storeId, productId, productName);
        
        // 检查是否已存在库存记录
        Inventory existing = inventoryMapper.selectByStoreIdAndProductId(storeId, productId);
        if (existing != null) {
            log.warn("库存记录已存在，店铺ID: {}, 商品ID: {}", storeId, productId);
            return existing;
        }
        
        // 创建新的库存记录
        Inventory inventory = new Inventory();
        inventory.setStoreId(storeId);
        inventory.setProductId(productId);
        inventory.setProductName(productName);
        inventory.setCurrentStock(0); // 默认库存为0
        inventory.setLockedStock(0);
        inventory.setMinStock(0); // 默认最低库存预警线为0
        inventory.setMaxStock(null); // 最大库存容量不限制
        
        inventoryMapper.insert(inventory);
        log.info("库存记录创建成功，库存ID: {}, 店铺ID: {}, 商品ID: {}", inventory.getId(), storeId, productId);
        
        // 缓存新创建的库存记录
        inventoryCacheService.cacheInventory(inventory);
        
        return inventory;
    }
    
    /**
     * 检查并锁定库存（下单时调用）
     */
    @Transactional
    public void checkAndLockStock(Long productId, Integer quantity) {
        log.info("检查并锁定库存，商品ID: {}, 数量: {}", productId, quantity);
        
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory == null) {
            throw new InsufficientStockException(productId, "未知商品", quantity, 0);
        }
        
        // 检查可用库存
        int availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
        if (availableStock < quantity) {
            throw new InsufficientStockException(
                productId, 
                inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 锁定库存
        int updatedRows = inventoryMapper.lockStock(productId, quantity);
        if (updatedRows == 0) {
             availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
            throw new InsufficientStockException(
                productId, 
                inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 记录库存变动
        recordInventoryTransaction(
            productId, 
            null, 
            InventoryTransaction.TransactionType.LOCK, 
            quantity, 
            inventory.getCurrentStock(), 
            inventory.getCurrentStock(),
            inventory.getLockedStock(),
            inventory.getLockedStock() + quantity,
            "下单锁定库存"
        );
        
        // 更新缓存
        inventory.setLockedStock(inventory.getLockedStock() + quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存锁定成功，商品ID: {}, 锁定数量: {}", productId, quantity);
    }
    
    /**
     * 扣减库存（支付成功后调用）
     */
    @Transactional
    public void deductStock(Long productId, Integer quantity, Long orderId) {
        log.info("扣减库存，商品ID: {}, 数量: {}, 订单ID: {}", productId, quantity, orderId);
        
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory == null) {
            log.error("商品不存在，商品ID: {}", productId);
            return;
        }
        
        // 扣减库存
        int updatedRows = inventoryMapper.deductStock(productId, quantity);
        if (updatedRows == 0) {
            log.error("库存扣减失败，商品ID: {}, 数量: {}", productId, quantity);
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
            "支付成功扣减库存"
        );
        
        // 更新缓存
        inventory.setCurrentStock(inventory.getCurrentStock());
        inventory.setLockedStock(inventory.getLockedStock() - quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存扣减成功，商品ID: {}, 扣减数量: {}", productId, quantity);
    }
    
    /**
     * 解锁库存（订单取消时调用）
     */
    @Transactional
    public void unlockStock(Long productId, Integer quantity, Long orderId) {
        log.info("解锁库存，商品ID: {}, 数量: {}, 订单ID: {}", productId, quantity, orderId);
        
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory == null) {
            log.error("商品不存在，商品ID: {}", productId);
            return;
        }
        
        // 解锁库存
        int updatedRows = inventoryMapper.unlockStock(productId, quantity);
        if (updatedRows == 0) {
            log.error("库存解锁失败，商品ID: {}, 数量: {}", productId, quantity);
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
            "订单取消解锁库存"
        );
        
        // 更新缓存
        inventory.setLockedStock(inventory.getLockedStock() - quantity);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存解锁成功，商品ID: {}, 解锁数量: {}", productId, quantity);
    }
    
    /**
     * 批量检查并锁定库存
     */
    @Transactional
    public void checkAndLockStockBatch(List<Long> productIds, List<Integer> quantities) {
        log.info("批量检查并锁定库存，商品数量: {}", productIds.size());
        
        for (int i = 0; i < productIds.size(); i++) {
            checkAndLockStock(productIds.get(i), quantities.get(i));
        }
        
        log.info("批量库存锁定完成");
    }
    
    /**
     * 批量扣减库存
     */
    @Transactional
    public void deductStockBatch(List<Long> productIds, List<Integer> quantities, Long orderId) {
        log.info("批量扣减库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        for (int i = 0; i < productIds.size(); i++) {
            deductStock(productIds.get(i), quantities.get(i), orderId);
        }
        
        log.info("批量库存扣减完成");
    }
    
    /**
     * 批量解锁库存
     */
    @Transactional
    public void unlockStockBatch(List<Long> productIds, List<Integer> quantities, Long orderId) {
        log.info("批量解锁库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        for (int i = 0; i < productIds.size(); i++) {
            unlockStock(productIds.get(i), quantities.get(i), orderId);
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
     * 根据订单号扣减库存（支付成功后调用）
     */
    @Transactional
    public void deductStockByOrderNo(String orderNo) {
        log.info("根据订单号扣减库存，订单号: {}", orderNo);
        
        // 查询订单项
        List<OrderItem> orderItems = orderItemMapper.selectByOrderNo(orderNo);
        if (orderItems.isEmpty()) {
            log.warn("订单项不存在，订单号: {}", orderNo);
            return;
        }
        
        // 遍历订单项，扣减每个商品的库存
        for (OrderItem orderItem : orderItems) {
            try {
                deductStock(orderItem.getProductId(), orderItem.getQuantity(), null);
                log.info("商品库存扣减成功，商品ID: {}, 数量: {}", orderItem.getProductId(), orderItem.getQuantity());
            } catch (Exception e) {
                log.error("商品库存扣减失败，商品ID: {}, 数量: {}, 错误: {}", 
                         orderItem.getProductId(), orderItem.getQuantity(), e.getMessage());
                throw new RuntimeException("库存扣减失败: " + e.getMessage());
            }
        }
        
        log.info("订单库存扣减完成，订单号: {}", orderNo);
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
     * 根据订单号重新锁定库存（用于已取消订单的支付回调）
     */
    @Transactional
    public void lockStockByOrderNo(String orderNo) {
        log.info("重新锁定库存，订单号: {}", orderNo);
        
        try {
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderNo(orderNo);
            if (orderItems == null || orderItems.isEmpty()) {
                log.warn("订单项不存在，订单号: {}", orderNo);
                return;
            }
            
            // 重新锁定库存
            for (OrderItem item : orderItems) {
                int updatedRows = inventoryMapper.lockStock(item.getProductId(), item.getQuantity());
                if (updatedRows == 0) {
                    log.error("重新锁定库存失败，商品ID: {}, 数量: {}", item.getProductId(), item.getQuantity());
                    throw new RuntimeException("重新锁定库存失败");
                }
                log.info("重新锁定库存成功，商品ID: {}, 数量: {}", item.getProductId(), item.getQuantity());
            }
            
            log.info("重新锁定库存完成，订单号: {}", orderNo);
        } catch (Exception e) {
            log.error("重新锁定库存异常，订单号: {}", orderNo, e);
            throw e;
        }
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
}
