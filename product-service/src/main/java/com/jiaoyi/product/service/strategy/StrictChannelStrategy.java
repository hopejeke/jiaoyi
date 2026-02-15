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
 * 严格渠道隔离扣减策略
 *
 * 只允许从渠道自身额度扣减，不允许借用共享池。
 *
 * 适用场景：
 * - 不同渠道的库存严格独立（如：线上和线下库存完全隔离）
 * - 某些高价值渠道需要独占库存保护
 * - 促销活动中需要保证特定渠道的库存不被其他渠道挤占
 *
 * 扣减路径（只有一层）：
 *
 *   ┌─────────────────┐
 *   │  Layer 1: 渠道额度 │
 *   │  channel_quota   │   WHERE (quota - sold) >= delta
 *   └────────┬────────┘
 *            │ 额度不够
 *   ┌────────▼────────┐
 *   │  直接拒绝        │ ← 不走共享池
 *   │  OUT_OF_STOCK   │
 *   └─────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrictChannelStrategy implements ChannelDeductionStrategy {

    private final PoiItemChannelStockMapper channelStockMapper;
    private final PoiItemStockMapper stockMapper;

    @Override
    public String name() {
        return "STRICT_CHANNEL";
    }

    @Override
    public ChannelDeductResult deduct(PoiItemStock stock, PoiItemChannelStock channelStock, ChannelDeductRequest request) {
        BigDecimal delta = request.getQuantity();

        if (channelStock == null) {
            return ChannelDeductResult.outOfStock(BigDecimal.ZERO, stock.getSharedPoolQuantity());
        }

        int channelDeducted = channelStockMapper.atomicDeductChannelQuota(
                stock.getId(), request.getChannelCode(), delta);

        if (channelDeducted > 0) {
            stockMapper.atomicDeduct(stock.getId(), delta);

            log.info("[{}] 渠道额度扣减成功: channel={}, delta={}",
                    name(), request.getChannelCode(), delta);

            PoiItemChannelStock updated = channelStockMapper.selectByStockIdAndChannel(
                    stock.getId(), request.getChannelCode());
            BigDecimal channelRemaining = updated != null ? updated.getChannelRemaining() : BigDecimal.ZERO;

            return ChannelDeductResult.success(
                    ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL,
                    channelRemaining, stock.getSharedPoolQuantity());
        }

        // 严格模式：不走共享池，直接拒绝
        log.warn("[{}] 渠道额度不足，严格模式拒绝: channel={}, remaining={}",
                name(), request.getChannelCode(), channelStock.getChannelRemaining());

        return ChannelDeductResult.outOfStock(
                channelStock.getChannelRemaining(), stock.getSharedPoolQuantity());
    }
}
