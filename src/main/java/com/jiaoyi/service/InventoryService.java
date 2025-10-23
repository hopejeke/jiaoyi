package com.jiaoyi.service;

import com.jiaoyi.entity.Inventory;
import com.jiaoyi.entity.InventoryTransaction;
import com.jiaoyi.exception.InsufficientStockException;
import com.jiaoyi.mapper.InventoryMapper;
import com.jiaoyi.mapper.InventoryTransactionMapper;
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
     * 根据商品ID查询库存
     */
    public Optional<Inventory> getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        return Optional.ofNullable(inventory);
    }
    
    /**
     * 查询库存不足的商品
     */
    public List<Inventory> getLowStockItems() {
        return inventoryMapper.selectLowStockItems();
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
}
