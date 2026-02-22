package com.jiaoyi.product.service;

import com.jiaoyi.outbox.OutboxService;
import com.jiaoyi.product.config.RocketMQConfig;
import com.jiaoyi.product.dto.*;
import com.jiaoyi.product.entity.PoiItemChannelStock;
import com.jiaoyi.product.entity.PoiItemStock;
import com.jiaoyi.product.entity.PoiItemStockLog;
import com.jiaoyi.product.entity.OversellRecord;
import com.jiaoyi.product.mapper.primary.PoiItemChannelStockMapper;
import com.jiaoyi.product.mapper.primary.PoiItemStockLogMapper;
import com.jiaoyi.product.mapper.primary.PoiItemStockMapper;
import com.jiaoyi.product.mapper.primary.OversellRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品中心库存服务
 * 
 * 核心功能：
 * 1. POS/商品中心 库存同步（syncFromPos / updateStock）
 * 2. POS 离线事件回放（replayOfflineEvents）
 * 3. 渠道库存隔离扣减（deductByChannel：先扣渠道额度→再借共享池）
 * 4. 共享池加权分配（allocateChannelQuotas）
 * 5. 超卖检测和补偿（detectOversellAndRecord）
 * 6. 绝对设置 vs 相对变更冲突合并（handleAbsoluteSet / handleRelativeDelta）
 */
@Slf4j
@Service
public class PoiItemStockService {
    
    @Autowired
    private PoiItemStockMapper stockMapper;
    
    @Autowired
    private PoiItemChannelStockMapper channelStockMapper;
    
    @Autowired
    private PoiItemStockLogMapper stockLogMapper;
    
    @Autowired
    private OversellRecordMapper oversellRecordMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private OutboxService outboxService;
    
    private static final String OUTBOX_TYPE_STOCK_SYNC = "POI_ITEM_STOCK_SYNC_MQ";
    /** 分配模式一：单池+渠道上限。总库存一个池子，每渠道仅一个可售上限 channel_max，超过不能卖，0=不设上限；渠道不单独占库存。 */
    private static final String ALLOCATION_MODE_WEIGHTED_QUOTA = "WEIGHTED_QUOTA";
    /** 分配模式二：安全线保护。不预分配，总库存低于高优先级渠道安全线时锁定低优先级渠道。 */
    private static final String ALLOCATION_MODE_SAFETY_STOCK = "SAFETY_STOCK";
    /** 扣减来源：安全线模式（仅扣主表 real_quantity，归还时只还主表） */
    private static final String DEDUCT_SOURCE_FROM_SAFETY_STOCK = "FROM_SAFETY_STOCK";
    /** 扣减来源：方案一无渠道记录时只扣主表，归还时只还主表 */
    private static final String DEDUCT_SOURCE_FROM_POOL = "FROM_POOL";
    
    // ========================= 1. POS 在线库存同步 =========================
    
    /**
     * 接收POS上报库存变更（POS在线时）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void syncFromPos(StockSyncFromPosRequest request) {
        log.info("接收POS上报库存变更: brandId={}, storeId={}, objectId={}", 
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        
        PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        
        if (stock != null && request.getUpdatedAt() != null) {
            if (!stock.getUpdatedAt().equals(request.getUpdatedAt())) {
                log.warn("库存已被其他操作修改: stockId={}, dbUpdatedAt={}, reqUpdatedAt={}", 
                    stock.getId(), stock.getUpdatedAt(), request.getUpdatedAt());
                throw new RuntimeException("库存已被其他操作修改，请刷新后重试");
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (stock == null) {
            stock = new PoiItemStock();
            stock.setBrandId(request.getBrandId());
            stock.setStoreId(request.getStoreId());
            stock.setObjectType(PoiItemStock.ObjectType.SKU.getCode());
            stock.setObjectId(request.getObjectId());
            stock.setStockStatus(request.getStockStatus());
            stock.setStockType(request.getStockType());
            stock.setPlanQuantity(request.getPlanQuantity());
            stock.setRealQuantity(request.getRealQuantity());
            stock.setAutoRestoreType(request.getAutoRestoreType());
            stock.setAutoRestoreAt(request.getAutoRestoreAt());
            stock.setCreatedAt(now);
            stock.setUpdatedAt(now);
            stockMapper.insert(stock);
        } else {
            int updated = stockMapper.updateStock(
                stock.getId(), request.getBrandId(), request.getStoreId(),
                request.getStockStatus(), request.getStockType(),
                request.getPlanQuantity(), request.getRealQuantity(),
                request.getAutoRestoreType(), request.getAutoRestoreAt(),
                now, stock.getUpdatedAt()
            );
            if (updated == 0) {
                throw new RuntimeException("库存更新失败，可能已被其他操作修改");
            }
            stock.setUpdatedAt(now);
        }
        
        // 更新渠道库存
        if (request.getChannelStocks() != null && !request.getChannelStocks().isEmpty()) {
            channelStockMapper.deleteByStockId(stock.getId());
            for (StockSyncFromPosRequest.ChannelStock cs : request.getChannelStocks()) {
                PoiItemChannelStock channel = new PoiItemChannelStock();
                channel.setBrandId(request.getBrandId());
                channel.setStoreId(request.getStoreId());
                channel.setStockId(stock.getId());
                channel.setStockStatus(cs.getStockStatus());
                channel.setStockType(cs.getStockType());
                channel.setChannelCode(cs.getChannelCode());
                channel.setCreatedAt(now);
                channel.setUpdatedAt(now);
                channelStockMapper.insert(channel);
            }
        }
        
        // 写变更日志
        writeLog(stock, request.getBrandId(), request.getStoreId(),
            "ABSOLUTE_SET", BigDecimal.ZERO, "POS", null, request);
    }
    
    /**
     * 商品中心设置库存（会同步到POS）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void updateStock(StockSyncFromPosRequest request) {
        log.info("商品中心设置库存: brandId={}, storeId={}, objectId={}", 
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        
        syncFromPos(request);
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sendStockSyncToOutbox(request);
                } catch (Exception e) {
                    log.error("库存已更新但写入outbox失败: brandId={}, storeId={}, objectId={}", 
                        request.getBrandId(), request.getStoreId(), request.getObjectId(), e);
                }
            }
        });
    }
    
    // ========================= 2. POS 离线事件回放 =========================
    
    /**
     * POS 离线事件批量回放
     * 
     * 核心逻辑：
     * 1. 按时间顺序逐条回放
     * 2. 每条事件基于 orderId 做幂等（跳过已处理的）
     * 3. RELATIVE_DELTA 直接原子扣减
     * 4. ABSOLUTE_SET 走冲突合并（补偿离线期间的自动扣减）
     * 5. 所有事件回放完后，检测是否超卖
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public PosOfflineReplayResult replayOfflineEvents(PosOfflineReplayRequest request) {
        log.info("开始POS离线事件回放: brandId={}, storeId={}, posInstance={}, events={}",
            request.getBrandId(), request.getStoreId(), 
            request.getPosInstanceId(), request.getEvents().size());
        
        PosOfflineReplayResult result = new PosOfflineReplayResult();
        result.setTotalEvents(request.getEvents().size());
        
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        
        for (StockChangeEvent event : request.getEvents()) {
            // 补充 brand/poi（事件可能不带）
            if (event.getBrandId() == null) event.setBrandId(request.getBrandId());
            if (event.getStoreId() == null) event.setStoreId(request.getStoreId());
            event.setSource(StockChangeEvent.Source.POS_OFFLINE);
            
            try {
                boolean applied = applyStockChangeEvent(event);
                if (applied) {
                    successCount++;
                } else {
                    skippedCount++;  // 幂等跳过
                }
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
        
        // 检测超卖
        if (successCount > 0) {
            detectOversellAfterReplay(request, result);
        }
        
        log.info("POS离线事件回放完成: total={}, success={}, skipped={}, failed={}, oversell={}",
            result.getTotalEvents(), successCount, skippedCount, failedCount, result.isOversellDetected());
        
        return result;
    }
    
    /**
     * 应用单条库存变更事件
     * 返回 true=已应用, false=幂等跳过
     */
    private boolean applyStockChangeEvent(StockChangeEvent event) {
        // 查找库存记录
        PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            event.getBrandId(), event.getStoreId(), event.getObjectId());
        
        if (stock == null) {
            throw new RuntimeException("库存记录不存在: objectId=" + event.getObjectId());
        }
        
        switch (event.getChangeType()) {
            case RELATIVE_DELTA:
                return handleRelativeDelta(stock, event);
            case ABSOLUTE_SET:
                return handleAbsoluteSet(stock, event);
            case STATUS_CHANGE:
                return handleStatusChange(stock, event);
            default:
                throw new RuntimeException("未知变更类型: " + event.getChangeType());
        }
    }
    
    // ========================= 3. 渠道库存隔离扣减 =========================
    
    /**
     * 渠道级别库存扣减
     * 
     * 三层扣减逻辑（同一事务内）：
     * ① 检查渠道是否售罄 → 拒绝
     * ② 尝试扣渠道专属额度（channel_quota - channel_sold >= delta）
     * ③ 渠道额度不够 → 尝试从共享池借（shared_pool_quantity >= delta）
     * ④ 共享池也不够 → 返回库存不足
     * ⑤ 扣减成功后，同步扣减 real_quantity 并写日志
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public ChannelDeductResult deductByChannel(ChannelDeductRequest request) {
        log.info("渠道库存扣减: storeId={}, objectId={}, channel={}, qty={}, orderId={}",
            request.getStoreId(), request.getObjectId(), request.getChannelCode(),
            request.getQuantity(), request.getOrderId());

        PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在: objectId=" + request.getObjectId());
        }
        if (request.getOrderId() != null) {
            int count = stockLogMapper.countDeductByOrderIdAndStockId(request.getOrderId(), stock.getId());
            if (count > 0) {
                log.info("重复扣减请求，跳过: orderId={}, stockId={}", request.getOrderId(), stock.getId());
                return ChannelDeductResult.duplicate();
            }
        }
        if (stock.getStockType() != null && stock.getStockType() == PoiItemStock.StockType.UNLIMITED.getCode()) {
            writeDeductLog(stock, request, "UNLIMITED_PASS");
            return ChannelDeductResult.success(
                ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL, null, null);
        }

        // 商家可选两种模式：WEIGHTED_QUOTA=单池+渠道上限，SAFETY_STOCK=安全线保护
        String mode = stock.getAllocationMode() != null ? stock.getAllocationMode() : ALLOCATION_MODE_WEIGHTED_QUOTA;
        if (ALLOCATION_MODE_SAFETY_STOCK.equalsIgnoreCase(mode)) {
            return deductBySafetyStock(request, stock);
        }
        return deductByWeightedQuota(request, stock);
    }

    /**
     * 方案一：总库存一个池子，渠道不单独占库存；每渠道仅一个可售上限（channel_max），超过则不能卖，0或未填=不设上限。
     */
    private ChannelDeductResult deductByWeightedQuota(ChannelDeductRequest request, PoiItemStock stock) {
        BigDecimal delta = request.getQuantity();
        PoiItemChannelStock channelStock = channelStockMapper.selectByStockIdAndChannel(
            stock.getId(), request.getChannelCode());

        PoiItemStock locked = stockMapper.selectByIdForUpdate(stock.getId());
        if (locked == null) {
            throw new RuntimeException("库存记录不存在: id=" + stock.getId());
        }
        BigDecimal realQty = locked.getRealQuantity() != null ? locked.getRealQuantity() : BigDecimal.ZERO;
        if (realQty.compareTo(delta) < 0) {
            log.warn("库存不足: stockId={}, real={}, need={}", stock.getId(), realQty, delta);
            autoMarkStockSoldOut(stock);
            return ChannelDeductResult.outOfStock(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (channelStock != null) {
            int updated = channelStockMapper.atomicIncreaseChannelSoldWithCap(
                stock.getId(), request.getChannelCode(), delta);
            BigDecimal cap = channelStock.getChannelMax();
            if (cap != null && cap.compareTo(BigDecimal.ZERO) > 0 && updated == 0) {
                BigDecimal sold = channelStock.getChannelSold() != null ? channelStock.getChannelSold() : BigDecimal.ZERO;
                log.warn("超过渠道可售上限: channel={}, sold={}, max={}, need={}",
                    request.getChannelCode(), sold, cap, delta);
                return new ChannelDeductResult(
                    ChannelDeductResult.Status.CHANNEL_OUT_OF_STOCK,
                    cap.subtract(sold),
                    BigDecimal.ZERO,
                    "超过该渠道可售上限");
            }
        }

        int deducted = stockMapper.atomicDeduct(stock.getId(), delta);
        if (deducted == 0) {
            return ChannelDeductResult.outOfStock(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        String deductSource = channelStock != null ? "FROM_CHANNEL" : DEDUCT_SOURCE_FROM_POOL;
        writeDeductLog(stock, request, deductSource);

        PoiItemChannelStock updatedCh = channelStockMapper.selectByStockIdAndChannel(
            stock.getId(), request.getChannelCode());
        BigDecimal channelRemaining = BigDecimal.ZERO;
        if (updatedCh != null && updatedCh.getChannelMax() != null && updatedCh.getChannelMax().compareTo(BigDecimal.ZERO) > 0) {
            channelRemaining = updatedCh.getChannelMax().subtract(updatedCh.getChannelSold() != null ? updatedCh.getChannelSold() : BigDecimal.ZERO);
        }
        PoiItemStock after = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        BigDecimal sharedPool = after != null && after.getSharedPoolQuantity() != null ? after.getSharedPoolQuantity() : BigDecimal.ZERO;
        return ChannelDeductResult.success(
            ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL,
            channelRemaining, sharedPool);
    }

    /** 方案二：安全线保护扣减。可用库存 = real_quantity - 高优先级渠道安全线之和；扣减后不能低于安全线。 */
    private ChannelDeductResult deductBySafetyStock(ChannelDeductRequest request, PoiItemStock stock) {
        PoiItemChannelStock myChannel = channelStockMapper.selectByStockIdAndChannel(
            stock.getId(), request.getChannelCode());
        int myPriority = (myChannel != null && myChannel.getChannelPriority() != null)
            ? myChannel.getChannelPriority() : 0;
        BigDecimal reservedByHigher = channelStockMapper.sumSafetyStockForHigherPriority(stock.getId(), myPriority);
        if (reservedByHigher == null) {
            reservedByHigher = BigDecimal.ZERO;
        }
        PoiItemStock locked = stockMapper.selectByIdForUpdate(stock.getId());
        if (locked == null) {
            throw new RuntimeException("库存记录不存在: id=" + stock.getId());
        }
        BigDecimal realQty = locked.getRealQuantity() != null ? locked.getRealQuantity() : BigDecimal.ZERO;
        BigDecimal availableForMe = realQty.subtract(reservedByHigher);
        BigDecimal delta = request.getQuantity();
        if (availableForMe.compareTo(delta) < 0) {
            log.warn("安全线模式库存不足: channel={}, available={}, reserved={}, need={}",
                request.getChannelCode(), availableForMe, reservedByHigher, delta);
            return ChannelDeductResult.outOfStock(availableForMe, BigDecimal.ZERO);
        }
        int updated = stockMapper.atomicDeductWithFloor(stock.getId(), delta, reservedByHigher);
        if (updated == 0) {
            log.warn("安全线模式扣减失败(并发): stockId={}, delta={}, floor={}", stock.getId(), delta, reservedByHigher);
            return ChannelDeductResult.outOfStock(availableForMe, BigDecimal.ZERO);
        }
        writeDeductLog(stock, request, DEDUCT_SOURCE_FROM_SAFETY_STOCK);
        PoiItemStock after = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            request.getBrandId(), request.getStoreId(), request.getObjectId());
        BigDecimal sharedPool = after != null && after.getSharedPoolQuantity() != null ? after.getSharedPoolQuantity() : BigDecimal.ZERO;
        log.info("安全线模式扣减成功: channel={}, remaining={}", request.getChannelCode(), availableForMe.subtract(delta));
        return ChannelDeductResult.success(
            ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL,
            availableForMe.subtract(delta), sharedPool);
    }

    /**
     * 按渠道批量扣减（一单多品，下单时调用）
     * 逐行扣减，按 orderId+stockId 幂等；任一行库存不足则抛异常（调用方需保证要么全成功要么全失败）。
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void deductByChannelBatch(ChannelDeductBatchRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }
        for (ChannelDeductBatchRequest.Item item : request.getItems()) {
            ChannelDeductRequest req = new ChannelDeductRequest(
                request.getBrandId(), request.getStoreId(), item.getObjectId(),
                request.getChannelCode(), item.getQuantity(), request.getOrderId());
            ChannelDeductResult res = deductByChannel(req);
            if (res.isSuccess()) {
                continue;
            }
            if (res.getStatus() == ChannelDeductResult.Status.DUPLICATE) {
                continue;
            }
            throw new RuntimeException("库存扣减失败: " + (res.getMessage() != null ? res.getMessage() : res.getStatus().name()));
        }
    }

    // ========================= 4. 共享池加权分配 =========================
    
    /**
     * 按权重重新分配渠道额度
     * 
     * 算法：
     * 1. 计算总可分配库存 = real_quantity（已扣除已售部分）
     * 2. 按权重分配：channel_quota = 总量 × channel_weight
     * 3. 余数分配给权重最高的渠道
     * 4. 剩余不足整除的部分放入共享池
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void allocateChannelQuotas(String brandId, String storeId, Long objectId) {
        log.info("开始渠道额度分配: brandId={}, storeId={}, objectId={}", brandId, storeId, objectId);
        
        PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(brandId, storeId, objectId);
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
        
        // 不限量不需要分配
        if (stock.getStockType() != null && stock.getStockType() == PoiItemStock.StockType.UNLIMITED.getCode()) {
            log.info("不限量库存，跳过渠道额度分配");
            return;
        }
        
        List<PoiItemChannelStock> channels = channelStockMapper.selectByStockId(stock.getId());
        if (channels.isEmpty()) {
            log.info("没有渠道库存记录，跳过分配");
            return;
        }
        
        BigDecimal totalQuantity = stock.getRealQuantity();
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("库存为0，所有渠道额度设为0");
            for (PoiItemChannelStock ch : channels) {
                channelStockMapper.updateChannelQuotaAndWeight(ch.getId(), BigDecimal.ZERO, ch.getChannelWeight());
            }
            stockMapper.updateSharedPoolQuantity(stock.getId(), BigDecimal.ZERO);
            return;
        }
        
        // 计算权重总和（用于归一化）
        BigDecimal totalWeight = channels.stream()
            .map(PoiItemChannelStock::getChannelWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            // 没有权重，平均分配
            totalWeight = new BigDecimal(channels.size());
            for (PoiItemChannelStock ch : channels) {
                ch.setChannelWeight(BigDecimal.ONE);
            }
        }
        
        // 按权重分配（向下取整）
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal highestWeight = BigDecimal.ZERO;
        PoiItemChannelStock highestWeightChannel = null;
        
        for (PoiItemChannelStock ch : channels) {
            BigDecimal quota = totalQuantity
                .multiply(ch.getChannelWeight())
                .divide(totalWeight, 1, RoundingMode.DOWN);
            
            channelStockMapper.updateChannelQuotaAndWeight(ch.getId(), quota, ch.getChannelWeight());
            allocated = allocated.add(quota);
            
            if (ch.getChannelWeight().compareTo(highestWeight) > 0) {
                highestWeight = ch.getChannelWeight();
                highestWeightChannel = ch;
            }
            
            log.info("渠道额度分配: channel={}, weight={}, quota={}", 
                ch.getChannelCode(), ch.getChannelWeight(), quota);
        }
        
        // 余数放入共享池
        BigDecimal sharedPool = totalQuantity.subtract(allocated);
        stockMapper.updateSharedPoolQuantity(stock.getId(), sharedPool);
        
        // 重置已售数量
        channelStockMapper.resetChannelSold(stock.getId());
        
        log.info("渠道额度分配完成: total={}, allocated={}, sharedPool={}", 
            totalQuantity, allocated, sharedPool);
    }
    
    // ========================= 5. 绝对设置 vs 相对变更冲突合并 =========================
    
    /**
     * 处理相对变更（订单扣减/释放）
     * 
     * 逻辑：
     * 1. 幂等检查（orderId）
     * 2. 原子扣减 real_quantity（WHERE real_quantity >= delta）
     * 3. 写入日志
     * 
     * 返回 true=已应用, false=幂等跳过
     */
    private boolean handleRelativeDelta(PoiItemStock stock, StockChangeEvent event) {
        // 幂等检查
        if (event.getOrderId() != null) {
            int count = stockLogMapper.countByOrderId(event.getOrderId());
            if (count > 0) {
                log.info("相对变更幂等跳过: orderId={}", event.getOrderId());
                return false;
            }
        }
        
        BigDecimal absDelta = event.getDelta().abs();
        boolean isDeduct = event.getDelta().compareTo(BigDecimal.ZERO) < 0;
        
        if (isDeduct) {
            // 扣减
            int updated = stockMapper.atomicDeduct(stock.getId(), absDelta);
            if (updated == 0) {
                // 库存不足 → 记录但不阻断（离线回放时可能已超卖）
                log.warn("相对变更扣减失败（库存不足）: stockId={}, delta={}, current={}",
                    stock.getId(), event.getDelta(), stock.getRealQuantity());
                // 强制扣减到负数（标记超卖，后续检测）
                stockMapper.forceUpdateQuantity(
                    stock.getId(),
                    stock.getRealQuantity().add(event.getDelta()),
                    stock.getLastManualSetTime(),
                    LocalDateTime.now()
                );
            }
        } else {
            // 补货（正数变更）
            stockMapper.forceUpdateQuantity(
                stock.getId(),
                stock.getRealQuantity().add(event.getDelta()),
                stock.getLastManualSetTime(),
                LocalDateTime.now()
            );
        }
        
        // 自动判断售罄
        PoiItemStock updatedStock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
            event.getBrandId(), event.getStoreId(), event.getObjectId());
        if (updatedStock.getRealQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            stockMapper.updateStockStatus(stock.getId(), 
                PoiItemStock.StockStatus.OUT_OF_STOCK.getCode(), LocalDateTime.now());
        }
        
        // 写日志
        writeEventLog(stock, event);
        
        return true;
    }
    
    /**
     * 处理绝对设置（店长手动设库存）
     * 
     * 冲突合并核心算法：
     * 1. 加行锁 SELECT ... FOR UPDATE
     * 2. 检查操作时间是否晚于 last_manual_set_time（防重复/乱序）
     * 3. 查询操作时间之后的所有 RELATIVE_DELTA 总和（这段时间内的自动扣减）
     * 4. 计算 finalQuantity = newQuantity + deltaSinceOperateTime
     * 5. forceUpdate 写入最终值
     * 
     * 为什么这样做？
     * - 假设 T1 时刻店长看到库存50，设置为30
     * - T1~T2 之间线上自动扣了5杯（RELATIVE_DELTA = -5）
     * - T2 时刻云端处理这条绝对设置
     * - 如果直接写30，相当于丢了这5杯的扣减
     * - 正确做法：30 + (-5) = 25
     */
    private boolean handleAbsoluteSet(PoiItemStock stock, StockChangeEvent event) {
        // 加行锁
        PoiItemStock lockedStock = stockMapper.selectByIdForUpdate(stock.getId());
        
        // 检查操作时间（防止处理旧的设置请求）
        if (lockedStock.getLastManualSetTime() != null && event.getOperateTime() != null) {
            if (!event.getOperateTime().isAfter(lockedStock.getLastManualSetTime())) {
                log.info("绝对设置已过期，跳过: operateTime={}, lastManualSetTime={}",
                    event.getOperateTime(), lockedStock.getLastManualSetTime());
                return false;
            }
        }
        
        // 查询操作时间之后的 RELATIVE_DELTA 总和
        LocalDateTime sinceTime = event.getOperateTime() != null ? event.getOperateTime() : lockedStock.getUpdatedAt();
        BigDecimal deltaSinceT1 = stockLogMapper.sumDeltaSince(stock.getId(), sinceTime);
        
        // 计算最终值 = 新设置值 + 期间的自动扣减
        BigDecimal finalQuantity = event.getNewQuantity().add(deltaSinceT1);
        
        log.info("绝对设置冲突合并: newQty={}, deltaSinceT1={}, finalQty={}",
            event.getNewQuantity(), deltaSinceT1, finalQuantity);
        
        // 强制更新
        LocalDateTime manualSetTime = event.getOperateTime() != null ? event.getOperateTime() : LocalDateTime.now();
        stockMapper.forceUpdateQuantity(stock.getId(), finalQuantity, manualSetTime, LocalDateTime.now());
        
        // 写日志
        writeEventLog(stock, event);
        
        return true;
    }
    
    /**
     * 处理状态变更（可售→售罄 或 售罄→可售）
     */
    private boolean handleStatusChange(PoiItemStock stock, StockChangeEvent event) {
        stockMapper.updateStockStatus(stock.getId(), event.getNewStockStatus(), LocalDateTime.now());
        writeEventLog(stock, event);
        return true;
    }
    
    // ========================= 超卖检测 =========================
    
    /**
     * POS离线回放后的超卖检测
     * 
     * 逻辑：
     * 1. 重新查询库存
     * 2. 如果 real_quantity < 0，说明超卖了
     * 3. 记录超卖记录，通知店长确认
     * 4. 将库存强制设为0（不能是负数）
     */
    private void detectOversellAfterReplay(PosOfflineReplayRequest request, PosOfflineReplayResult result) {
        // 收集所有涉及的 objectId
        List<Long> objectIds = request.getEvents().stream()
            .map(StockChangeEvent::getObjectId)
            .distinct()
            .collect(Collectors.toList());
        
        for (Long objectId : objectIds) {
            PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(
                request.getBrandId(), request.getStoreId(), objectId);
            
            if (stock != null && stock.getRealQuantity().compareTo(BigDecimal.ZERO) < 0) {
                // 超卖了！
                BigDecimal oversellQty = stock.getRealQuantity().abs();
                
                log.warn("检测到超卖: objectId={}, oversellQty={}", objectId, oversellQty);
                
                // 记录超卖
                OversellRecord record = new OversellRecord();
                record.setBrandId(request.getBrandId());
                record.setStoreId(request.getStoreId());
                record.setStockId(stock.getId());
                record.setObjectId(objectId);
                record.setOversellQuantity(oversellQty);
                record.setSource("POS_OFFLINE");
                record.setStatus(OversellRecord.Status.PENDING.name());
                record.setRemark("POS离线回放后检测到超卖，POS实例: " + request.getPosInstanceId());
                oversellRecordMapper.insert(record);
                
                // 将库存强制设为0
                stockMapper.forceUpdateQuantity(stock.getId(), BigDecimal.ZERO,
                    stock.getLastManualSetTime(), LocalDateTime.now());
                stockMapper.updateStockStatus(stock.getId(),
                    PoiItemStock.StockStatus.OUT_OF_STOCK.getCode(), LocalDateTime.now());
                
                result.setOversellDetected(true);
                result.setOversellQuantity(oversellQty);
                result.setOversellRecordId(record.getId());
            }
        }
    }
    
    /**
     * 查询超卖记录
     */
    public List<OversellRecord> getOversellRecords(String brandId, String storeId, String status) {
        return oversellRecordMapper.selectByBrandIdAndStoreId(brandId, storeId, status, 100);
    }
    
    /**
     * 处理超卖记录（店长确认）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void resolveOversellRecord(Long recordId, String status, String resolvedBy, String remark) {
        OversellRecord record = oversellRecordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("超卖记录不存在: id=" + recordId);
        }
        oversellRecordMapper.updateStatus(recordId, status, resolvedBy, remark);
        log.info("超卖记录已处理: id={}, status={}, by={}", recordId, status, resolvedBy);
    }
    
    // ========================= 辅助方法 =========================
    
    /**
     * 自动判断渠道是否售罄
     */
    private void autoMarkChannelSoldOut(Long stockId, String channelCode, BigDecimal channelRemaining) {
        if (channelRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            PoiItemChannelStock ch = channelStockMapper.selectByStockIdAndChannel(stockId, channelCode);
            if (ch != null && ch.getStockStatus() != PoiItemStock.StockStatus.OUT_OF_STOCK.getCode()) {
                channelStockMapper.updateChannelStock(ch.getId(), 
                    PoiItemStock.StockStatus.OUT_OF_STOCK.getCode(), ch.getStockType());
                log.info("渠道自动售罄: channel={}", channelCode);
            }
        }
    }
    
    /**
     * 自动判断主库存是否售罄
     */
    private void autoMarkStockSoldOut(PoiItemStock stock) {
        if (stock.getRealQuantity().compareTo(BigDecimal.ZERO) <= 0 
            && stock.getSharedPoolQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            stockMapper.updateStockStatus(stock.getId(), 
                PoiItemStock.StockStatus.OUT_OF_STOCK.getCode(), LocalDateTime.now());
            log.info("库存自动售罄: stockId={}", stock.getId());
        }
    }
    
    /**
     * 写入通用变更日志（无扣减来源时 deductSource/channelCode 传 null）
     */
    private void writeLog(PoiItemStock stock, String brandId, String storeId,
                          String changeType, BigDecimal delta, String source,
                          String orderId, Object content) {
        writeLog(stock, brandId, storeId, changeType, delta, source, orderId, content, null, null);
    }

    private void writeLog(PoiItemStock stock, String brandId, String storeId,
                          String changeType, BigDecimal delta, String source,
                          String orderId, Object content, String deductSource, String channelCode) {
        try {
            PoiItemStockLog logEntry = new PoiItemStockLog();
            logEntry.setBrandId(brandId);
            logEntry.setStoreId(storeId);
            logEntry.setStockId(stock.getId());
            logEntry.setContent(objectMapper.writeValueAsString(content));
            logEntry.setChangeType(changeType);
            logEntry.setDelta(delta);
            logEntry.setSource(source);
            logEntry.setOrderId(orderId);
            logEntry.setDeductSource(deductSource);
            logEntry.setChannelCode(channelCode);
            logEntry.setCreatedAt(LocalDateTime.now());
            stockLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("写入变更记录失败", e);
            throw new RuntimeException("写入变更记录失败", e);
        }
    }
    
    /**
     * 写入事件类型的日志
     */
    private void writeEventLog(PoiItemStock stock, StockChangeEvent event) {
        writeLog(stock, event.getBrandId(), event.getStoreId(),
            event.getChangeType().name(),
            event.getDelta() != null ? event.getDelta() : BigDecimal.ZERO,
            event.getSource().name(),
            event.getOrderId(),
            event);
    }
    
    /**
     * 写入扣减日志（remark 为 FROM_CHANNEL / FROM_SHARED_POOL，用于按源头归还）
     */
    private void writeDeductLog(PoiItemStock stock, ChannelDeductRequest request, String remark) {
        writeLog(stock, request.getBrandId(), request.getStoreId(),
            "RELATIVE_DELTA",
            request.getQuantity().negate(),
            "CLOUD",
            request.getOrderId(),
            request,
            remark,
            request.getChannelCode());
    }
    
    // ========================= 按订单归还（按源头还） =========================

    /** 扣减来源常量，与日志 deduct_source 一致 */
    private static final String DEDUCT_SOURCE_FROM_CHANNEL = "FROM_CHANNEL";
    private static final String DEDUCT_SOURCE_FROM_SHARED_POOL = "FROM_SHARED_POOL";

    /**
     * 按订单ID归还库存（订单取消时调用）
     * 根据扣减日志的 deduct_source 与 channel_code 按源头归还：渠道还渠道、共享池还共享池；幂等按 orderId+stockId 是否已有 RETURN 记录判断。
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void returnStockByOrderId(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        List<PoiItemStockLog> deductLogs = stockLogMapper.selectDeductLogsByOrderId(orderId);
        if (deductLogs.isEmpty()) {
            log.info("归还跳过：无扣减记录 orderId={}", orderId);
            return;
        }
        for (PoiItemStockLog logRow : deductLogs) {
            Long stockId = logRow.getStockId();
            int alreadyReturned = stockLogMapper.countReturnByOrderIdAndStockId(orderId, stockId);
            if (alreadyReturned > 0) {
                log.info("归还幂等跳过：已存在归还记录 orderId={}, stockId={}", orderId, stockId);
                continue;
            }
            BigDecimal returnQty = logRow.getDelta() != null ? logRow.getDelta().abs() : BigDecimal.ZERO;
            if (returnQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String deductSource = logRow.getDeductSource();
            String channelCode = logRow.getChannelCode();

            if (DEDUCT_SOURCE_FROM_CHANNEL.equals(deductSource) && channelCode != null && !channelCode.isEmpty()) {
                int chUpdated = channelStockMapper.atomicDecreaseChannelSold(stockId, channelCode, returnQty);
                if (chUpdated == 0) {
                    log.warn("归还渠道失败（渠道已售不足或不存在）: orderId={}, stockId={}, channel={}, qty={}", orderId, stockId, channelCode, returnQty);
                    continue;
                }
                stockMapper.atomicIncreaseRealQuantity(stockId, returnQty);
            } else if (DEDUCT_SOURCE_FROM_SHARED_POOL.equals(deductSource)) {
                stockMapper.atomicReturnToSharedPool(stockId, returnQty);
            } else if (DEDUCT_SOURCE_FROM_SAFETY_STOCK.equals(deductSource) || DEDUCT_SOURCE_FROM_POOL.equals(deductSource)) {
                stockMapper.atomicIncreaseRealQuantity(stockId, returnQty);
            } else {
                log.debug("归还跳过（非渠道/共享池/安全线/池子扣减或旧数据）: orderId={}, stockId={}, deductSource={}", orderId, stockId, deductSource);
                continue;
            }
            writeReturnLog(logRow, returnQty);
        }
        log.info("按订单归还完成: orderId={}, 条数={}", orderId, deductLogs.size());
    }

    private void writeReturnLog(PoiItemStockLog deductLog, BigDecimal returnQty) {
        PoiItemStock stub = new PoiItemStock();
        stub.setId(deductLog.getStockId());
        stub.setBrandId(deductLog.getBrandId());
        stub.setStoreId(deductLog.getStoreId());
        try {
            java.util.Map<String, Object> content = new java.util.HashMap<>();
            content.put("reason", "order_cancel_return");
            content.put("orderId", deductLog.getOrderId() != null ? deductLog.getOrderId() : "");
            writeLog(stub, deductLog.getBrandId(), deductLog.getStoreId(),
                "RETURN", returnQty, "CLOUD", deductLog.getOrderId(), content,
                deductLog.getDeductSource(), deductLog.getChannelCode());
        } catch (Exception e) {
            log.error("写入归还记录失败", e);
            throw new RuntimeException("写入归还记录失败", e);
        }
    }

    /**
     * 设置渠道库存分配模式（WEIGHTED_QUOTA | SAFETY_STOCK）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void setAllocationMode(String brandId, String storeId, Long objectId, String allocationMode) {
        PoiItemStock stock = stockMapper.selectByBrandIdAndStoreIdAndObjectId(brandId, storeId, objectId);
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
        if (!ALLOCATION_MODE_WEIGHTED_QUOTA.equalsIgnoreCase(allocationMode)
            && !ALLOCATION_MODE_SAFETY_STOCK.equalsIgnoreCase(allocationMode)) {
            throw new IllegalArgumentException("allocationMode 必须为 WEIGHTED_QUOTA（单池+渠道上限）或 SAFETY_STOCK（安全线保护）");
        }
        stockMapper.updateAllocationMode(stock.getId(), allocationMode.toUpperCase());
        log.info("分配模式已更新: stockId={}, mode={}", stock.getId(), allocationMode);
    }

    /**
     * 更新渠道优先级与安全线（方案二 SAFETY_STOCK 用，运营后台配置）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void updateChannelPriorityAndSafetyStock(Long channelId, Integer channelPriority, BigDecimal safetyStock) {
        PoiItemChannelStock ch = channelStockMapper.selectById(channelId);
        if (ch == null) {
            throw new RuntimeException("渠道库存记录不存在: id=" + channelId);
        }
        channelStockMapper.updatePriorityAndSafetyStock(channelId,
            channelPriority != null ? channelPriority : (ch.getChannelPriority() != null ? ch.getChannelPriority() : 0),
            safetyStock != null ? safetyStock : (ch.getSafetyStock() != null ? ch.getSafetyStock() : BigDecimal.ZERO));
        log.info("渠道优先级/安全线已更新: channelId={}, priority={}, safetyStock={}", channelId, channelPriority, safetyStock);
    }
    
    /**
     * 查询库存详情
     */
    public PoiItemStock getStockDetail(String brandId, String storeId, Long objectId) {
        return stockMapper.selectByBrandIdAndStoreIdAndObjectId(brandId, storeId, objectId);
    }

    /**
     * 查询库存变更记录
     */
    public List<PoiItemStockLog> getStockLogs(String brandId, String storeId, Long stockId, Integer limit) {
        if (stockId != null) {
            return stockLogMapper.selectByStockId(stockId, limit != null ? limit : 100);
        } else {
            return stockLogMapper.selectByBrandIdAndStoreId(brandId, storeId, limit != null ? limit : 100);
        }
    }
    
    // ========================= Outbox 同步 =========================
    
    private void sendStockSyncToOutbox(StockSyncFromPosRequest request) {
        try {
            StockSyncToPosMessage message = buildSyncToPosMessage(request);
            String payload = objectMapper.writeValueAsString(message);
            String bizKey = request.getStoreId() + ":" + request.getObjectId() + ":" + System.currentTimeMillis();
            String messageKey = "stock-sync:" + request.getStoreId() + ":" + request.getObjectId();
            String shardingKey = request.getStoreId();
            
            outboxService.enqueue(
                OUTBOX_TYPE_STOCK_SYNC, bizKey, payload,
                RocketMQConfig.POI_ITEM_STOCK_SYNC_TOPIC,
                RocketMQConfig.POI_ITEM_STOCK_SYNC_TAG,
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
        message.setBrandId(request.getBrandId());
        message.setStoreId(request.getStoreId());
        
        StockSyncToPosMessage.StockData data = new StockSyncToPosMessage.StockData();
        data.setObjectId(request.getObjectId());
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
                })
                .collect(Collectors.toList());
            data.setChannelStocks(channelStocks);
        }
        
        message.setData(data);
        return message;
    }
}
