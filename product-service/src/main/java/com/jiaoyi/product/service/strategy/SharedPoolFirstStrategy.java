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
 * 共享池优先扣减策略
 *
 * 优先从共享池扣减，共享池不够时再扣渠道额度。
 *
 * 适用场景：
 * - 全渠道促销活动（如双11），所有渠道共享同一批库存
 * - 爆品策略：不关心具体从哪个渠道消耗，只关心总消耗速度
 * - 渠道间需要极致的库存流动性
 *
 * 扣减路径（反转标准漏斗）：
 *
 *   ┌─────────────────┐
 *   │  Layer 1: 共享池  │ ← 优先走公共池，最大化利用
 *   │  shared_pool     │   WHERE shared_pool >= delta
 *   └────────┬────────┘
 *            │ 池不够
 *   ┌────────▼────────┐
 *   │  Layer 2: 渠道额度 │ ← 降级走渠道自有额度
 *   │  channel_quota   │
 *   └────────┬────────┘
 *            │ 也不够
 *   ┌────────▼────────┐
 *   │  Layer 3: 拒绝   │
 *   │  OUT_OF_STOCK   │
 *   └─────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SharedPoolFirstStrategy implements ChannelDeductionStrategy {

    private final PoiItemChannelStockMapper channelStockMapper;
    private final PoiItemStockMapper stockMapper;

    @Override
    public String name() {
        return "SHARED_POOL_FIRST";
    }

    @Override
    public ChannelDeductResult deduct(PoiItemStock stock, PoiItemChannelStock channelStock, ChannelDeductRequest request) {
        BigDecimal delta = request.getQuantity();

        // ========== Layer 1: 优先从共享池扣 ==========
        int sharedDeducted = stockMapper.atomicDeductSharedPool(stock.getId(), delta);
        if (sharedDeducted > 0) {
            log.info("[{}] Layer1 共享池扣减成功: channel={}, delta={}",
                    name(), request.getChannelCode(), delta);

            PoiItemStock updatedStock = stockMapper.selectByBrandIdAndPoiIdAndObjectId(
                    request.getBrandId(), request.getPoiId(), request.getObjectId());
            BigDecimal channelRemaining = channelStock != null ? channelStock.getChannelRemaining() : BigDecimal.ZERO;

            return ChannelDeductResult.success(
                    ChannelDeductResult.Status.SUCCESS_FROM_SHARED_POOL,
                    channelRemaining, updatedStock.getSharedPoolQuantity());
        }

        // ========== Layer 2: 共享池不够，降级到渠道额度 ==========
        if (channelStock != null) {
            int channelDeducted = channelStockMapper.atomicDeductChannelQuota(
                    stock.getId(), request.getChannelCode(), delta);

            if (channelDeducted > 0) {
                stockMapper.atomicDeduct(stock.getId(), delta);

                log.info("[{}] Layer2 渠道额度降级扣减: channel={}, delta={}",
                        name(), request.getChannelCode(), delta);

                PoiItemChannelStock updated = channelStockMapper.selectByStockIdAndChannel(
                        stock.getId(), request.getChannelCode());
                BigDecimal channelRemaining = updated != null ? updated.getChannelRemaining() : BigDecimal.ZERO;

                return ChannelDeductResult.success(
                        ChannelDeductResult.Status.SUCCESS_FROM_CHANNEL,
                        channelRemaining, stock.getSharedPoolQuantity());
            }
        }

        // ========== Layer 3: 库存不足 ==========
        BigDecimal channelRemaining = channelStock != null ? channelStock.getChannelRemaining() : BigDecimal.ZERO;

        log.warn("[{}] Layer3 库存不足: channel={}", name(), request.getChannelCode());

        return ChannelDeductResult.outOfStock(channelRemaining, stock.getSharedPoolQuantity());
    }
}
