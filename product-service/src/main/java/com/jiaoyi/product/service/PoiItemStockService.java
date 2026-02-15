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
import com.jiaoyi.product.service.strategy.ChannelDeductionStrategy;
import com.jiaoyi.product.service.strategy.StandardDeductionStrategy;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品中心库存服务
 *
 * 架构概览（三层库存模型）：
 *
 *   ┌──────────────────────────────────────────────────┐
 *   │              POI 总库存 (real_quantity)            │
 *   │                                                  │
 *   │   ┌──────────┐ ┌──────────┐ ┌──────────┐       │
 *   │   │  POS 渠道 │ │ KIOSK渠道│ │ 在线渠道  │       │
 *   │   │  quota:30│ │ quota:20 │ │ quota:30 │       │
 *   │   │  sold:25 │ │ sold:5   │ │ sold:28  │       │
 *   │   └──────────┘ └──────────┘ └──────────┘       │
 *   │                                                  │
 *   │   ┌──────────────────────────────────────┐      │
 *   │   │         共享池 (shared_pool: 20)      │      │
 *   │   │   任何渠道额度不够时可以借用            │      │
 *   │   └──────────────────────────────────────┘      │
 *   └──────────────────────────────────────────────────┘
 *
 * 核心功能：
 * 1. POS/商品中心 库存同步（syncFromPos / updateStock）
 * 2. POS 离线事件回放（replayOfflineEvents）
 * 3. 渠道库存隔离扣减（deductByChannel）：策略模式，支持多种扣减策略
 * 4. 共享池加权分配（allocateChannelQuotas）
 * 5. 超卖检测和补偿（detectOversellAndRecord）
 * 6. 绝对设置 vs 相对变更冲突合并（handleAbsoluteSet / handleRelativeDelta）
 * 7. 渠道贡献度追踪 + 动态权重调整（ChannelStockRebalanceService）
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

    /**
     * 扣减策略注册表
     * key = 策略名称, value = 策略实例
     * 支持运行时通过配置中心切换策略
     */
    @Autowired
    private Map<String, ChannelDeductionStrategy> strategyMap;

    /**
     * 默认扣减策略（标准三层漏斗）
     */
    @Autowired
    private StandardDeductionStrategy defaultDeductionStrategy;

    private static final String OUTBOX_TYPE_STOCK_SYNC = "POI_ITEM_STOCK_SYNC_MQ";
    
    // ========================= 1. POS 在线库存同步 =========================
    
    /**
     * 接收POS上报库存变更（POS在线时）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void syncFromPos(StockSyncFromPosRequest request) {
        log.info("接收POS上报库存变更: brandId={}, poiId={}, objectId={}", 
            request.getBrandId(), request.getPoiId(), request.getObjectId());
        
        PoiItemStock stock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
            request.getBrandId(), request.getPoiId(), request.getObjectId());
        
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
            stock.setPoiId(request.getPoiId());
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
                stock.getId(), request.getBrandId(), request.getPoiId(),
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
                channel.setPoiId(request.getPoiId());
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
        writeLog(stock, request.getBrandId(), request.getPoiId(),
            "ABSOLUTE_SET", BigDecimal.ZERO, "POS", null, request);
    }
    
    /**
     * 商品中心设置库存（会同步到POS）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void updateStock(StockSyncFromPosRequest request) {
        log.info("商品中心设置库存: brandId={}, poiId={}, objectId={}", 
            request.getBrandId(), request.getPoiId(), request.getObjectId());
        
        syncFromPos(request);
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sendStockSyncToOutbox(request);
                } catch (Exception e) {
                    log.error("库存已更新但写入outbox失败: brandId={}, poiId={}, objectId={}", 
                        request.getBrandId(), request.getPoiId(), request.getObjectId(), e);
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
        log.info("开始POS离线事件回放: brandId={}, poiId={}, posInstance={}, events={}",
            request.getBrandId(), request.getPoiId(), 
            request.getPosInstanceId(), request.getEvents().size());
        
        PosOfflineReplayResult result = new PosOfflineReplayResult();
        result.setTotalEvents(request.getEvents().size());
        
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        
        for (StockChangeEvent event : request.getEvents()) {
            // 补充 brand/poi（事件可能不带）
            if (event.getBrandId() == null) event.setBrandId(request.getBrandId());
            if (event.getPoiId() == null) event.setPoiId(request.getPoiId());
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
        PoiItemStock stock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
            event.getBrandId(), event.getPoiId(), event.getObjectId());
        
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
    
    // ========================= 3. 渠道库存隔离扣减（策略模式） =========================

    /**
     * 渠道级别库存扣减（策略模式入口）
     *
     * 通过策略模式支持多种扣减策略，解耦扣减逻辑与业务控制：
     * - STANDARD_FUNNEL（默认）: 渠道额度 → 共享池借调 → 拒绝
     * - STRICT_CHANNEL:         渠道额度 → 直接拒绝（不借共享池）
     * - SHARED_POOL_FIRST:      共享池 → 渠道额度 → 拒绝（促销活动场景）
     *
     * 策略选择可以通过 request.getStrategyName() 指定，也可以通过配置中心按
     * brandId/poiId/channelCode 路由到不同策略。
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public ChannelDeductResult deductByChannel(ChannelDeductRequest request) {
        log.info("渠道库存扣减: poiId={}, objectId={}, channel={}, qty={}, orderId={}",
            request.getPoiId(), request.getObjectId(), request.getChannelCode(),
            request.getQuantity(), request.getOrderId());

        // 1. 幂等检查
        if (request.getOrderId() != null) {
            int count = stockLogMapper.countByOrderId(request.getOrderId());
            if (count > 0) {
                log.info("重复扣减请求，跳过: orderId={}", request.getOrderId());
                return ChannelDeductResult.duplicate();
            }
        }

        // 2. 查询库存主表
        PoiItemStock stock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
            request.getBrandId(), request.getPoiId(), request.getObjectId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在: objectId=" + request.getObjectId());
        }

        // 不限量模式直接成功
        if (stock.getStockType() != null && stock.getStockType() == PoiItemStock.StockType.UNLIMITED.getCode()) {
            writeDeductLog(stock, request, "UNLIMITED_PASS");
            return ChannelDeductResult.success(
                ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL, null, null);
        }

        // 3. 查询渠道库存
        PoiItemChannelStock channelStock = channelStockMapper.selectByStockIdAndChannel(
            stock.getId(), request.getChannelCode());

        // 如果渠道已售罄
        if (channelStock != null && channelStock.getStockStatus() != null
            && channelStock.getStockStatus() == PoiItemStock.StockStatus.OUT_OF_STOCK.getCode()) {
            return new ChannelDeductResult(
                ChannelDeductResult.Status.CHANNEL_OUT_OF_STOCK, BigDecimal.ZERO,
                stock.getSharedPoolQuantity(), "该渠道已售罄");
        }

        // 4. 选择扣减策略（策略模式）
        ChannelDeductionStrategy strategy = resolveStrategy(request);
        log.info("使用扣减策略: {}, channel={}", strategy.name(), request.getChannelCode());

        // 5. 执行策略扣减
        ChannelDeductResult result = strategy.deduct(stock, channelStock, request);

        // 6. 后置处理：写日志 + 自动售罄检测
        if (result.isSuccess()) {
            String source = result.getStatus() == ChannelDeductResult.Status.SUCCESS_FROM_SHARED_POOL
                    ? "FROM_SHARED_POOL" : "FROM_CHANNEL";
            writeDeductLog(stock, request, source);

            // 自动售罄检测
            if (channelStock != null) {
                PoiItemChannelStock updated = channelStockMapper.selectByStockIdAndChannel(
                        stock.getId(), request.getChannelCode());
                if (updated != null) {
                    autoMarkChannelSoldOut(stock.getId(), request.getChannelCode(), updated.getChannelRemaining());
                }
            }
        } else if (result.getStatus() == ChannelDeductResult.Status.OUT_OF_STOCK) {
            autoMarkChannelSoldOut(stock.getId(), request.getChannelCode(), BigDecimal.ZERO);
            autoMarkStockSoldOut(stock);
        }

        return result;
    }

    /**
     * 策略路由：根据请求参数选择扣减策略
     *
     * 路由优先级：
     * 1. 请求显式指定 strategyName → 使用指定策略
     * 2. 未指定 → 使用默认策略（StandardDeductionStrategy）
     *
     * 扩展点：可以接入配置中心，按 brandId/poiId 路由到不同策略
     */
    private ChannelDeductionStrategy resolveStrategy(ChannelDeductRequest request) {
        // 如果请求显式指定了策略
        if (request.getStrategyName() != null && !request.getStrategyName().isEmpty()) {
            for (ChannelDeductionStrategy s : strategyMap.values()) {
                if (s.name().equalsIgnoreCase(request.getStrategyName())) {
                    return s;
                }
            }
            log.warn("未找到策略: {}, 降级使用默认策略", request.getStrategyName());
        }
        return defaultDeductionStrategy;
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
    public void allocateChannelQuotas(String brandId, String poiId, Long objectId) {
        log.info("开始渠道额度分配: brandId={}, poiId={}, objectId={}", brandId, poiId, objectId);
        
        PoiItemStock stock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(brandId, poiId, objectId);
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
        PoiItemStock updatedStock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
            event.getBrandId(), event.getPoiId(), event.getObjectId());
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
            PoiItemStock stock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
                request.getBrandId(), request.getPoiId(), objectId);
            
            if (stock != null && stock.getRealQuantity().compareTo(BigDecimal.ZERO) < 0) {
                // 超卖了！
                BigDecimal oversellQty = stock.getRealQuantity().abs();
                
                log.warn("检测到超卖: objectId={}, oversellQty={}", objectId, oversellQty);
                
                // 记录超卖
                OversellRecord record = new OversellRecord();
                record.setBrandId(request.getBrandId());
                record.setPoiId(request.getPoiId());
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
    public List<OversellRecord> getOversellRecords(String brandId, String poiId, String status) {
        return oversellRecordMapper.selectByBrandIdAndPoiId(brandId, poiId, status, 100);
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
     * 写入通用变更日志
     */
    private void writeLog(PoiItemStock stock, String brandId, String poiId,
                          String changeType, BigDecimal delta, String source, 
                          String orderId, Object content) {
        try {
            PoiItemStockLog logEntry = new PoiItemStockLog();
            logEntry.setBrandId(brandId);
            logEntry.setPoiId(poiId);
            logEntry.setStockId(stock.getId());
            logEntry.setContent(objectMapper.writeValueAsString(content));
            logEntry.setChangeType(changeType);
            logEntry.setDelta(delta);
            logEntry.setSource(source);
            logEntry.setOrderId(orderId);
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
        writeLog(stock, event.getBrandId(), event.getPoiId(),
            event.getChangeType().name(),
            event.getDelta() != null ? event.getDelta() : BigDecimal.ZERO,
            event.getSource().name(),
            event.getOrderId(),
            event);
    }
    
    /**
     * 写入扣减日志
     */
    private void writeDeductLog(PoiItemStock stock, ChannelDeductRequest request, String remark) {
        writeLog(stock, request.getBrandId(), request.getPoiId(),
            "RELATIVE_DELTA",
            request.getQuantity().negate(),
            "CLOUD",
            request.getOrderId(),
            request);
    }
    
    /**
     * 查询库存详情
     */
    public PoiItemStock getStockDetail(String brandId, String poiId, Long objectId) {
        return stockMapper.selectByBrandIdAndPoiIdAndObjectId(brandId, poiId, objectId);
    }
    
    /**
     * 查询库存变更记录
     */
    public List<PoiItemStockLog> getStockLogs(String brandId, String poiId, Long stockId, Integer limit) {
        if (stockId != null) {
            return stockLogMapper.selectByStockId(stockId, limit != null ? limit : 100);
        } else {
            return stockLogMapper.selectByBrandIdAndPoiId(brandId, poiId, limit != null ? limit : 100);
        }
    }
    
    // ========================= Outbox 同步 =========================
    
    private void sendStockSyncToOutbox(StockSyncFromPosRequest request) {
        try {
            StockSyncToPosMessage message = buildSyncToPosMessage(request);
            String payload = objectMapper.writeValueAsString(message);
            String bizKey = request.getPoiId() + ":" + request.getObjectId() + ":" + System.currentTimeMillis();
            String messageKey = "stock-sync:" + request.getPoiId() + ":" + request.getObjectId();
            String shardingKey = request.getPoiId();
            
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
        message.setPoiId(request.getPoiId());
        
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
