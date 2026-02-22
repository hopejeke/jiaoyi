package com.jiaoyi.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.InsufficientStockException;
import com.jiaoyi.product.entity.Inventory;
import com.jiaoyi.product.entity.InventoryDeductionIdempotency;
import com.jiaoyi.product.entity.InventoryTransaction;
import com.jiaoyi.product.mapper.sharding.InventoryDeductionIdempotencyMapper;
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
    private final InventoryDeductionIdempotencyMapper idempotencyMapper;
    private final ObjectMapper objectMapper;
    
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
     * 检查并锁定库存（下单时调用，基于SKU）
     * 
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param quantity 数量
     * @param orderId 订单ID（用于幂等性校验）
     */
    @Transactional
    public void checkAndLockStock(Long productId, Long skuId, Integer quantity, Long orderId) {
        log.info("检查并锁定库存，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
        
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID不能为空");
        }
        
        // 查询SKU级别的库存（只查询 sku_id IS NOT NULL 的记录）
        List<Inventory> skuInventories = inventoryMapper.selectByProductIdWithSku(productId);
        if (skuInventories == null || skuInventories.isEmpty()) {
            throw new InsufficientStockException(productId, "SKU库存记录不存在，请先为SKU创建库存记录", quantity, 0);
        }
        
        // 找到对应的SKU库存
        Inventory inventory = skuInventories.stream()
                .filter(inv -> skuId.equals(inv.getSkuId()))
                .findFirst()
                .orElse(null);
        
        if (inventory == null) {
            throw new InsufficientStockException(productId, "SKU库存记录不存在，SKU ID: " + skuId + "，请先为SKU创建库存记录", quantity, 0);
        }
        
        // 检查可用库存
        // 如果库存模式是 UNLIMITED（无限库存），直接跳过数量检查
        if (inventory.getStockMode() == Inventory.StockMode.UNLIMITED) {
            log.info("库存模式为 UNLIMITED（无限库存），跳过数量检查，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
            // UNLIMITED 模式下不锁定库存，直接返回成功
            return;
        }
        
        // LIMITED 模式：检查可用库存
        int availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
        if (availableStock < quantity) {
            throw new InsufficientStockException(
                productId, 
                inventory.getSkuName() != null ? inventory.getSkuName() : inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 幂等性校验：先尝试插入库存变动记录（LOCK 操作）
        // 唯一索引：(order_id, sku_id, transaction_type)，防止同一订单对同一SKU重复锁定
        if (orderId == null) {
            throw new IllegalArgumentException("锁定库存时必须提供订单ID（用于幂等性校验）");
        }
        
        InventoryTransaction lockTransaction = new InventoryTransaction();
        lockTransaction.setProductId(productId);
        lockTransaction.setSkuId(skuId);
        lockTransaction.setProductShardId(inventory.getProductShardId());
        lockTransaction.setOrderId(orderId);
        lockTransaction.setTransactionType(InventoryTransaction.TransactionType.LOCK);
        lockTransaction.setQuantity(quantity);
        lockTransaction.setBeforeStock(inventory.getCurrentStock());
        lockTransaction.setAfterStock(inventory.getCurrentStock());
        lockTransaction.setBeforeLocked(inventory.getLockedStock());
        lockTransaction.setAfterLocked(inventory.getLockedStock() + quantity);
        lockTransaction.setRemark("下单锁定库存（SKU级别，SKU ID: " + skuId + "）");
        lockTransaction.setCreateTime(java.time.LocalDateTime.now());
        
        // 尝试插入记录（如果已存在则返回 0，说明已经锁定过）
        int inserted = transactionMapper.tryInsert(lockTransaction);
        if (inserted == 0) {
            log.warn("库存锁定操作已执行过（幂等性校验），订单ID: {}, 商品ID: {}, SKU ID: {}", orderId, productId, skuId);
            return; // 幂等：已锁定过，直接返回
        }
        
        // 锁定库存（SKU级别，仅在 LIMITED 模式下执行）
        // 注意：SQL 条件包含 (current_stock - locked_stock) >= quantity，确保可用库存充足
        int updatedRows = inventoryMapper.lockStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
            // 锁定失败，重新查询最新库存状态
            inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
                    inventory.getStoreId(), productId, skuId);
            if (inventory != null) {
                availableStock = inventory.getCurrentStock() - inventory.getLockedStock();
            }
            throw new InsufficientStockException(
                productId, 
                inventory != null && inventory.getSkuName() != null ? inventory.getSkuName() : inventory.getProductName(), 
                quantity, 
                availableStock
            );
        }
        
        // 记录已成功插入，无需再次插入
        
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
        
        // 如果库存模式是 UNLIMITED（无限库存），不扣减库存，直接返回
        if (inventory.getStockMode() == Inventory.StockMode.UNLIMITED) {
            log.info("库存模式为 UNLIMITED（无限库存），跳过扣减操作，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
            return;
        }
        
        if (orderId == null) {
            throw new IllegalArgumentException("扣减库存时必须提供订单ID（用于幂等性校验）");
        }
        
        // ========== 幂等与分支：已有流水则按状态处理 ==========
        InventoryTransaction existingTx = transactionMapper.selectByOrderIdAndSkuId(orderId, skuId);
        if (existingTx != null) {
            String currentType = existingTx.getTransactionType() != null ? existingTx.getTransactionType().name() : null;
            if ("OUT".equals(currentType)) {
                log.warn("库存扣减已执行过（幂等），订单ID: {}, SKU ID: {}", orderId, skuId);
                return;
            }
            if ("UNLOCK".equals(currentType)) {
                throw new RuntimeException("订单已解锁库存，不允许扣减，订单ID: " + orderId + ", SKU ID: " + skuId);
            }
            // existingType == LOCK -> 走 OO 流程（先锁后扣），见下方 casUpdateToDeducted
        }
        
        // ========== 无锁直扣（POS 等）：无流水时先插 OUT 再只扣 current_stock ==========
        if (existingTx == null) {
            InventoryTransaction outTx = new InventoryTransaction();
            outTx.setProductId(productId);
            outTx.setSkuId(skuId);
            outTx.setProductShardId(inventory.getProductShardId());
            outTx.setOrderId(orderId);
            outTx.setTransactionType(InventoryTransaction.TransactionType.OUT);
            outTx.setQuantity(quantity);
            outTx.setBeforeStock(inventory.getCurrentStock());
            outTx.setAfterStock(inventory.getCurrentStock() - quantity);
            outTx.setBeforeLocked(0);
            outTx.setAfterLocked(0);
            outTx.setRemark("直接实扣（无锁定，如 POS 支付成功）SKU ID: " + skuId);
            outTx.setCreateTime(java.time.LocalDateTime.now());
            int inserted = transactionMapper.tryInsert(outTx);
            if (inserted == 0) {
                // 并发下可能已被其他请求插入，再查一次
                existingTx = transactionMapper.selectByOrderIdAndSkuId(orderId, skuId);
                if (existingTx != null && "OUT".equals(existingTx.getTransactionType() != null ? existingTx.getTransactionType().name() : null)) {
                    log.warn("库存扣减已执行过（幂等，并发），订单ID: {}, SKU ID: {}", orderId, skuId);
                    return;
                }
                throw new RuntimeException("库存扣减流水插入冲突，订单ID: " + orderId + ", SKU ID: " + skuId);
            }
            int directUpdated = inventoryMapper.deductStockBySkuDirect(productId, skuId, quantity);
            if (directUpdated == 0) {
                throw new RuntimeException("库存不足或记录不存在，无法直接扣减，订单ID: " + orderId + ", SKU ID: " + skuId);
            }
            Inventory latest = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(inventory.getStoreId(), productId, skuId);
            if (latest != null) {
                inventory.setCurrentStock(latest.getCurrentStock());
                inventoryCacheService.updateInventoryCache(inventory);
            }
            log.info("库存直接实扣成功（无锁），商品ID: {}, SKU ID: {}, 数量: {}, 订单ID: {}", productId, skuId, quantity, orderId);
            return;
        }
        
        // ========== OO 流程：CAS 更新流水 LOCK -> OUT，再扣 current_stock + locked_stock ==========
        int casUpdated = transactionMapper.casUpdateToDeducted(
                orderId,
                skuId,
                quantity,
                inventory.getCurrentStock() + quantity,  // beforeStock（扣减前）
                inventory.getCurrentStock(),              // afterStock（扣减后）
                inventory.getLockedStock(),               // beforeLocked
                inventory.getLockedStock() - quantity,   // afterLocked
                "支付成功扣减库存（SKU级别，SKU ID: " + skuId + "）"
        );
        
        if (casUpdated == 0) {
            // CAS 失败，说明状态不是 LOCK（可能已被解锁或已扣减）
            InventoryTransaction existing = transactionMapper.selectByOrderIdAndSkuId(orderId, skuId);
            if (existing != null) {
                String currentType = existing.getTransactionType() != null ? existing.getTransactionType().name() : "UNKNOWN";
                log.warn("库存扣减失败（CAS），订单ID: {}, 商品ID: {}, SKU ID: {}, 当前状态: {}", 
                        orderId, productId, skuId, currentType);
                if ("UNLOCK".equals(currentType)) {
                    throw new RuntimeException("订单已解锁库存，不允许扣减，订单ID: " + orderId + ", SKU ID: " + skuId);
                } else if ("OUT".equals(currentType)) {
                    log.warn("库存扣减操作已执行过（幂等性校验），订单ID: {}, 商品ID: {}, SKU ID: {}", 
                            orderId, productId, skuId);
                    return; // 幂等：已扣减过，直接返回
                }
            }
            throw new RuntimeException("库存扣减失败（CAS），订单ID: " + orderId + ", SKU ID: " + skuId + "，可能状态不是 LOCK");
        }
        
        // LIMITED 模式：扣减库存（SKU级别）
        int beforeCurrentStock = inventory.getCurrentStock();
        int beforeLockedStock = inventory.getLockedStock();
        int expectedAfterCurrentStock = beforeCurrentStock - quantity;
        int expectedAfterLockedStock = beforeLockedStock - quantity;
        log.info("【库存扣减】准备执行SQL更新，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 扣减前 current_stock: {}, locked_stock: {}, 预期扣减后 current_stock: {}, locked_stock: {}", 
                orderId, productId, skuId, quantity, beforeCurrentStock, beforeLockedStock, expectedAfterCurrentStock, expectedAfterLockedStock);
        
        int updatedRows = inventoryMapper.deductStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
            log.error("【库存扣减】SQL更新失败，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 当前 locked_stock: {}，可能原因：locked_stock < quantity", 
                    orderId, productId, skuId, quantity, beforeLockedStock);
            throw new RuntimeException("库存扣减失败");
        }
        
        // 重新查询最新状态，验证扣减结果
        Inventory latestInventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
                inventory.getStoreId(), productId, skuId);
        if (latestInventory != null) {
            int actualAfterCurrentStock = latestInventory.getCurrentStock();
            int actualAfterLockedStock = latestInventory.getLockedStock();
            log.info("【库存扣减】SQL更新成功，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 扣减前 current_stock: {}, locked_stock: {}, 扣减后 current_stock: {}, locked_stock: {}", 
                    orderId, productId, skuId, quantity, beforeCurrentStock, beforeLockedStock, actualAfterCurrentStock, actualAfterLockedStock);
            
            // 检查是否出现负数（防御性检查）
            if (actualAfterLockedStock < 0) {
                log.error("【库存扣减】⚠️ 检测到 locked_stock 为负数！订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 扣减前: {}, 扣减后: {}", 
                        orderId, productId, skuId, quantity, beforeLockedStock, actualAfterLockedStock);
            }
        }
        
        // 更新缓存
        inventory.setCurrentStock(latestInventory != null ? latestInventory.getCurrentStock() : expectedAfterCurrentStock);
        inventory.setLockedStock(latestInventory != null ? latestInventory.getLockedStock() : expectedAfterLockedStock);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存扣减成功，商品ID: {}, SKU ID: {}, 扣减数量: {}", productId, skuId, quantity);
    }
    
    /**
     * 解锁库存（订单取消时调用，基于SKU）
     * 
     * 注意：
     * 1. 使用幂等性校验：先尝试插入 inventory_transactions 记录（UNLOCK 类型）
     * 2. 唯一索引：(order_id, sku_id) 防止重复解锁
     * 3. 如果插入失败（唯一索引冲突），说明已经解锁过，返回 IDEMPOTENT_SUCCESS
     * 
     * @return OperationResult 包含操作结果状态（SUCCESS、IDEMPOTENT_SUCCESS、FAILED）
     */
    @Transactional
    public com.jiaoyi.common.OperationResult unlockStock(Long productId, Long skuId, Integer quantity, Long orderId) {
        log.info("解锁库存，商品ID: {}, SKU ID: {}, 数量: {}, 订单ID: {}", productId, skuId, quantity, orderId);
        
        if (skuId == null) {
            return com.jiaoyi.common.OperationResult.failed("SKU ID不能为空");
        }
        
        if (orderId == null) {
            return com.jiaoyi.common.OperationResult.failed("解锁库存时必须提供订单ID（用于幂等性校验）");
        }
        
        // 查询SKU库存
        Inventory productInventory = inventoryMapper.selectByProductId(productId);
        if (productInventory == null) {
            log.error("商品不存在，商品ID: {}", productId);
            return com.jiaoyi.common.OperationResult.failed("商品不存在，商品ID: " + productId);
        }
        
        Inventory inventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
            productInventory.getStoreId(), productId, skuId);
        if (inventory == null) {
            log.error("SKU库存不存在，商品ID: {}, SKU ID: {}", productId, skuId);
            return com.jiaoyi.common.OperationResult.failed("SKU库存不存在，商品ID: " + productId + ", SKU ID: " + skuId);
        }
        
        // 如果库存模式是 UNLIMITED（无限库存），不解锁库存，直接返回成功
        if (inventory.getStockMode() == Inventory.StockMode.UNLIMITED) {
            log.info("库存模式为 UNLIMITED（无限库存），跳过解锁操作，商品ID: {}, SKU ID: {}, 数量: {}", productId, skuId, quantity);
            return com.jiaoyi.common.OperationResult.success("库存模式为 UNLIMITED（无限库存），无需解锁");
        }
        
        // ========== CAS 更新：从 LOCKED 状态转为 UNLOCKED ==========
        // 使用 CAS 更新，只有当前状态是 LOCK 时才能更新为 UNLOCK
        // 如果状态不是 LOCK（已被扣减或已解锁），affected rows = 0，拒绝操作
        // 这保证了 deduct 和 unlock 的互斥性
        
        int casUpdated = transactionMapper.casUpdateToUnlocked(
                orderId,
                skuId,
                quantity,
                inventory.getCurrentStock(),              // beforeStock（不变）
                inventory.getCurrentStock(),              // afterStock（不变）
                inventory.getLockedStock(),               // beforeLocked
                inventory.getLockedStock() - quantity,   // afterLocked
                "订单取消解锁库存（SKU级别，SKU ID: " + skuId + "）"
        );
        
        if (casUpdated == 0) {
            // CAS 失败，说明状态不是 LOCK（可能已被扣减或已解锁）
            InventoryTransaction existing = transactionMapper.selectByOrderIdAndSkuId(orderId, skuId);
            if (existing != null) {
                String currentType = existing.getTransactionType() != null ? existing.getTransactionType().name() : "UNKNOWN";
                log.warn("库存解锁失败（CAS），订单ID: {}, 商品ID: {}, SKU ID: {}, 当前状态: {}", 
                        orderId, productId, skuId, currentType);
                if ("OUT".equals(currentType)) {
                    return com.jiaoyi.common.OperationResult.failed("订单已扣减库存，不允许解锁，订单ID: " + orderId + ", SKU ID: " + skuId);
                } else if ("UNLOCK".equals(currentType)) {
                    log.warn("库存解锁操作已执行过（幂等性校验），订单ID: {}, 商品ID: {}, SKU ID: {}", 
                            orderId, productId, skuId);
                    return com.jiaoyi.common.OperationResult.idempotentSuccess("库存解锁操作已执行过（幂等：重复调用），订单ID: " + orderId + ", SKU ID: " + skuId);
                }
            }
            return com.jiaoyi.common.OperationResult.failed("库存解锁失败（CAS），订单ID: " + orderId + ", SKU ID: " + skuId + "，可能状态不是 LOCK");
        }
        
        // LIMITED 模式：解锁库存（SKU级别）
        // 注意：SQL 条件包含 locked_stock >= quantity，防止解锁把 locked 扣成负数
        int beforeLocked = inventory.getLockedStock();
        int expectedAfterLocked = beforeLocked - quantity;
        log.info("【库存解锁】准备执行SQL更新，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 解锁前 locked_stock: {}, 预期解锁后: {}", 
                orderId, productId, skuId, quantity, beforeLocked, expectedAfterLocked);
        
        int updatedRows = inventoryMapper.unlockStockBySku(productId, skuId, quantity);
        if (updatedRows == 0) {
            log.error("【库存解锁】SQL更新失败，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 当前 locked_stock: {}，可能原因：locked_stock < quantity 或库存记录不存在", 
                    orderId, productId, skuId, quantity, beforeLocked);
            // 关键修复：抛异常让事务回滚，防止幂等记录已插入但库存未解锁，导致后续无法重试
            throw new RuntimeException("库存解锁失败，商品ID: " + productId + ", SKU ID: " + skuId + 
                    "，可能原因：locked_stock < quantity 或库存记录不存在，触发事务回滚以允许重试");
        }
        
        // 重新查询最新状态，验证解锁结果
        Inventory latestInventory = inventoryMapper.selectByStoreIdAndProductIdAndSkuId(
                inventory.getStoreId(), productId, skuId);
        if (latestInventory != null) {
            int actualAfterLocked = latestInventory.getLockedStock();
            log.info("【库存解锁】SQL更新成功，订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 解锁前 locked_stock: {}, 解锁后 locked_stock: {}", 
                    orderId, productId, skuId, quantity, beforeLocked, actualAfterLocked);
            
            // 检查是否出现负数（防御性检查）
            if (actualAfterLocked < 0) {
                log.error("【库存解锁】⚠️ 检测到 locked_stock 为负数！订单ID: {}, 商品ID: {}, SKU ID: {}, 数量: {}, 解锁前: {}, 解锁后: {}", 
                        orderId, productId, skuId, quantity, beforeLocked, actualAfterLocked);
                // 不抛异常，记录日志，让业务继续（可能是其他订单的 lock 导致的）
            }
        }
        
        // 更新缓存
        inventory.setLockedStock(latestInventory != null ? latestInventory.getLockedStock() : expectedAfterLocked);
        inventoryCacheService.updateInventoryCache(inventory);
        
        log.info("库存解锁成功，商品ID: {}, SKU ID: {}, 解锁数量: {}", productId, skuId, quantity);
        return com.jiaoyi.common.OperationResult.success("库存解锁成功，商品ID: " + productId + ", SKU ID: " + skuId);
    }
    
    /**
     * 批量检查并锁定库存（基于SKU）
     * 
     * @param productIds 商品ID列表
     * @param skuIds SKU ID列表
     * @param quantities 数量列表
     * @param orderId 订单ID（用于幂等性校验）
     */
    @Transactional
    public void checkAndLockStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId) {
        log.info("批量检查并锁定库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            throw new IllegalArgumentException("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        if (orderId == null) {
            throw new IllegalArgumentException("批量锁定库存时必须提供订单ID（用于幂等性校验）");
        }
        
        for (int i = 0; i < productIds.size(); i++) {
            checkAndLockStock(productIds.get(i), skuIds.get(i), quantities.get(i), orderId);
        }
        
        log.info("批量库存锁定完成");
    }
    
    /**
     * 批量检查并锁定库存（兼容旧接口，需要传入 orderId）
     */
    @Transactional
    public void checkAndLockStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities) {
        throw new IllegalArgumentException("批量锁定库存时必须提供订单ID（用于幂等性校验），请使用 checkAndLockStockBatch(productIds, skuIds, quantities, orderId)");
    }
    
    /**
     * 批量扣减库存（基于SKU）
     * 
     * @param productIds 商品ID列表
     * @param skuIds SKU ID列表
     * @param quantities 数量列表
     * @param orderId 订单ID
     * @param idempotencyKey 幂等键（可选，格式：orderId + "-DEDUCT"）
     */
    @Transactional
    public void deductStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId, String idempotencyKey) {
        log.info("批量扣减库存，订单ID: {}, idempotencyKey: {}, 商品数量: {}", orderId, idempotencyKey, productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            throw new IllegalArgumentException("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        // 计算 product_shard_id（从第一个商品查询 storeId）
        Integer productShardId = null;
        if (!productIds.isEmpty()) {
            try {
                Inventory productInventory = inventoryMapper.selectByProductId(productIds.get(0));
                if (productInventory != null && productInventory.getStoreId() != null) {
                    productShardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(productInventory.getStoreId());
                }
            } catch (Exception e) {
                log.warn("查询商品信息获取 product_shard_id 失败，商品ID: {}", productIds.get(0), e);
            }
        }
        
        if (productShardId == null) {
            throw new RuntimeException("无法确定 product_shard_id，请确保商品ID有效");
        }
        
        // 幂等性检查：如果提供了 idempotencyKey，先检查是否已处理过
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            // 查询是否已存在幂等性日志
            InventoryDeductionIdempotency existing = idempotencyMapper.selectByIdempotencyKey(idempotencyKey, productShardId);
            
            if (existing != null) {
                // 已存在幂等性日志
                if (existing.getStatus() == InventoryDeductionIdempotency.Status.SUCCESS) {
                    log.warn("重复请求（幂等性检查），订单 {} 已经成功扣过库存，idempotencyKey: {}", orderId, idempotencyKey);
                    return; // 幂等：已成功处理过，直接返回
                } else if (existing.getStatus() == InventoryDeductionIdempotency.Status.PROCESSING) {
                    log.warn("重复请求（幂等性检查），订单 {} 正在处理中，idempotencyKey: {}", orderId, idempotencyKey);
                    throw new RuntimeException("库存扣减请求正在处理中，请勿重复提交");
                } else if (existing.getStatus() == InventoryDeductionIdempotency.Status.FAILED) {
                    log.info("订单 {} 之前的扣库存请求失败，允许重试，idempotencyKey: {}", orderId, idempotencyKey);
                    // 失败状态允许重试，继续执行
                }
            } else {
                // 不存在，尝试插入幂等性日志（使用 INSERT IGNORE 实现原子性）
                try {
                    String productIdsJson = objectMapper.writeValueAsString(productIds);
                    String skuIdsJson = objectMapper.writeValueAsString(skuIds);
                    String quantitiesJson = objectMapper.writeValueAsString(quantities);
                    
                    int inserted = idempotencyMapper.tryInsert(idempotencyKey, orderId, productShardId, productIdsJson, skuIdsJson, quantitiesJson);
                    
                    if (inserted == 0) {
                        // 插入失败（可能并发插入），再次查询
                        existing = idempotencyMapper.selectByIdempotencyKey(idempotencyKey, productShardId);
                        if (existing != null && existing.getStatus() == InventoryDeductionIdempotency.Status.SUCCESS) {
                            log.warn("并发请求（幂等性检查），订单 {} 已经成功扣过库存，idempotencyKey: {}", orderId, idempotencyKey);
                            return; // 幂等：已成功处理过，直接返回
                        }
                    }
                } catch (Exception e) {
                    log.error("插入幂等性日志失败，idempotencyKey: {}", idempotencyKey, e);
                    // 幂等性日志插入失败不影响主流程，继续执行（但会有重复扣库存的风险）
                }
            }
        }
        
        // 执行扣减
        try {
            for (int i = 0; i < productIds.size(); i++) {
                deductStock(productIds.get(i), skuIds.get(i), quantities.get(i), orderId);
            }
            
            // 更新幂等性日志为成功
            if (idempotencyKey != null && !idempotencyKey.isEmpty() && productShardId != null) {
                idempotencyMapper.updateStatusToSuccess(idempotencyKey, productShardId);
            }
            
            log.info("批量库存扣减完成，订单ID: {}", orderId);
        } catch (Exception e) {
            // 更新幂等性日志为失败
            if (idempotencyKey != null && !idempotencyKey.isEmpty() && productShardId != null) {
                idempotencyMapper.updateStatusToFailed(idempotencyKey, productShardId, e.getMessage());
            }
            throw e;
        }
    }
    
    /**
     * 批量扣减库存（基于SKU，兼容旧接口，不传 idempotencyKey）
     */
    @Transactional
    public void deductStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId) {
        // 如果没有提供 idempotencyKey，使用 orderId + "-DEDUCT" 作为默认值
        String idempotencyKey = orderId != null ? orderId + "-DEDUCT" : null;
        deductStockBatch(productIds, skuIds, quantities, orderId, idempotencyKey);
    }
    
    /**
     * 批量解锁库存（基于SKU）
     * 
     * @return OperationResult 包含操作结果状态（SUCCESS、IDEMPOTENT_SUCCESS、FAILED）
     *         如果所有SKU都成功或幂等成功，返回 SUCCESS
     *         如果部分SKU幂等成功，返回 IDEMPOTENT_SUCCESS（表示部分或全部已处理过）
     *         如果有SKU失败，返回 FAILED
     */
    @Transactional
    public com.jiaoyi.common.OperationResult unlockStockBatch(List<Long> productIds, List<Long> skuIds, List<Integer> quantities, Long orderId) {
        log.info("批量解锁库存，订单ID: {}, 商品数量: {}", orderId, productIds.size());
        
        if (productIds.size() != skuIds.size() || productIds.size() != quantities.size()) {
            return com.jiaoyi.common.OperationResult.failed("商品ID、SKU ID和数量列表长度必须一致");
        }
        
        int successCount = 0;
        int idempotentCount = 0;
        int failedCount = 0;
        
        for (int i = 0; i < productIds.size(); i++) {
            com.jiaoyi.common.OperationResult result = unlockStock(productIds.get(i), skuIds.get(i), quantities.get(i), orderId);
            if (com.jiaoyi.common.OperationResult.ResultStatus.SUCCESS.equals(result.getStatus())) {
                successCount++;
            } else if (com.jiaoyi.common.OperationResult.ResultStatus.IDEMPOTENT_SUCCESS.equals(result.getStatus())) {
                idempotentCount++;
            } else {
                failedCount++;
            }
        }
        
        log.info("批量库存解锁完成，订单ID: {}, 成功: {}, 幂等成功: {}, 失败: {}", 
                orderId, successCount, idempotentCount, failedCount);
        
        if (failedCount > 0) {
            return com.jiaoyi.common.OperationResult.failed(
                    String.format("批量解锁库存失败，订单ID: %s, 成功: %d, 幂等成功: %d, 失败: %d", 
                            orderId, successCount, idempotentCount, failedCount));
        } else if (idempotentCount > 0) {
            return com.jiaoyi.common.OperationResult.idempotentSuccess(
                    String.format("批量解锁库存完成（部分或全部已处理过），订单ID: %s, 成功: %d, 幂等成功: %d", 
                            orderId, successCount, idempotentCount));
        } else {
            return com.jiaoyi.common.OperationResult.success(
                    String.format("批量解锁库存成功，订单ID: %s, 成功数量: %d", orderId, successCount));
        }
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
}

