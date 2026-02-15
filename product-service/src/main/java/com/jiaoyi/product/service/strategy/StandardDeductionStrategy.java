package com.jiaoyi.product.service.strategy;

import com.jiaoyi.product.dto.ChannelDeductRequest;
import com.jiaoyi.product.dto.ChannelDeductResult;
import com.jiaoyi.product.entity.PoiItemChannelStock;
import com.jiaoyi.product.entity.PoiItemStock;
import com.jiaoyi.product.mapper.primary.PoiItemChannelStockMapper;
import com.jiaoyi.product.mapper.primary.PoiItemStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 标准三层漏斗扣减策略
 *
 * 扣减路径（同一事务内，每层失败才尝试下一层）：
 *
 *   ┌─────────────────┐
 *   │  Layer 1: 渠道额度 │ ← 优先扣自己的渠道配额
 *   │  channel_quota   │   WHERE (quota - sold) >= delta
 *   └────────┬────────┘
 *            │ 额度不够
 *   ┌────────▼────────┐
 *   │  Layer 2: 共享池  │ ← 从公共池借调（记录 borrowed_from_pool）
 *   │  shared_pool     │   WHERE shared_pool >= delta
 *   └────────┬────────┘
 *            │ 池也不够
 *   ┌────────▼────────┐
 *   │  Layer 3: 拒绝   │ ← 库存不足，返回 OUT_OF_STOCK
 *   │  OUT_OF_STOCK   │
 *   └─────────────────┘
 *
 * 设计类比：
 * - Layer 1 类似"预留实例"（Reserved Instance），成本最低
 * - Layer 2 类似"按需实例"（On-Demand Instance），灵活但有限
 * - Layer 3 类似"容量不足"（InsufficientCapacity），需要等待或拒绝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandardDeductionStrategy implements ChannelDeductionStrategy {

    private final PoiItemChannelStockMapper channelStockMapper;
    private final PoiItemStockMapper stockMapper;

    @Override
    public String name() {
        return "STANDARD_FUNNEL";
    }

    @Override
    public ChannelDeductResult deduct(PoiItemStock stock, PoiItemChannelStock channelStock, ChannelDeductRequest request) {
        BigDecimal delta = request.getQuantity();

        // ========== Layer 1: 尝试扣渠道专属额度 ==========
        if (channelStock != null) {
            int channelDeducted = channelStockMapper.atomicDeductChannelQuota(
                    stock.getId(), request.getChannelCode(), delta);

            if (channelDeducted > 0) {
                // 渠道额度扣成功，同步扣主表 real_quantity
                stockMapper.atomicDeduct(stock.getId(), delta);

                log.info("[{}] Layer1 渠道额度扣减成功: channel={}, delta={}",
                        name(), request.getChannelCode(), delta);

                PoiItemChannelStock updated = channelStockMapper.selectByStockIdAndChannel(
                        stock.getId(), request.getChannelCode());
                BigDecimal channelRemaining = updated != null ? updated.getChannelRemaining() : BigDecimal.ZERO;

                return ChannelDeductResult.success(
                        ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL,
                        channelRemaining, stock.getSharedPoolQuantity());
            }
        }

        // ========== Layer 2: 渠道额度不够，尝试从共享池借 ==========
        int sharedDeducted = stockMapper.atomicDeductSharedPool(stock.getId(), delta);
        if (sharedDeducted > 0) {
            // 记录借调量（贡献追踪）
            if (channelStock != null) {
                channelStockMapper.addBorrowedFromPool(
                        stock.getId(), request.getChannelCode(), delta);
            }

            log.info("[{}] Layer2 共享池借调成功: channel={}, delta={}",
                    name(), request.getChannelCode(), delta);

            PoiItemStock updatedStock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
                    request.getBrandId(), request.getPoiId(), request.getObjectId());
            BigDecimal channelRemaining = channelStock != null ? channelStock.getChannelRemaining() : BigDecimal.ZERO;

            return ChannelDeductResult.success(
                    ChannelDeductResult.Status.SUCCESS_FROM_SHARED_POOL,
                    channelRemaining, updatedStock.getSharedPoolQuantity());
        }

        // ========== Layer 3: 都不够，库存不足 ==========
        BigDecimal channelRemaining = channelStock != null ? channelStock.getChannelRemaining() : BigDecimal.ZERO;

        log.warn("[{}] Layer3 库存不足: channel={}, channelRemaining={}, sharedPool={}",
                name(), request.getChannelCode(), channelRemaining, stock.getSharedPoolQuantity());

        return ChannelDeductResult.outOfStock(channelRemaining, stock.getSharedPoolQuantity());
    }
}
