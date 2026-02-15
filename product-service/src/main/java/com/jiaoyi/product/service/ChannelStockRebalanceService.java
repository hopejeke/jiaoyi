package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.PoiItemChannelStock;
import com.jiaoyi.product.entity.PoiItemStock;
import com.jiaoyi.product.mapper.primary.PoiItemChannelStockMapper;
import com.jiaoyi.product.mapper.primary.PoiItemStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 渠道库存动态再平衡服务
 *
 * 核心思路（类比云计算弹性调度）：
 * - 每个渠道(POS/KIOSK/ONLINE_ORDER)有独立配额(channel_quota)，类似"预留实例"
 * - 共享池(shared_pool)类似"按需实例"，所有渠道都可以借用
 * - 当某个渠道压力大(使用率>HIGH_WATERMARK)，自动从空闲渠道回收额度到共享池
 * - 当所有渠道压力都大时，共享池兜底
 *
 * 水位线模型：
 * ┌────────────────────────────────────────────────┐
 * │                  100%                          │
 * │  ■■■■■■■■■■■■■  HIGH_WATERMARK (80%)          │ → 触发共享池借调
 * │  ■■■■■■■■■■■■■                                │
 * │  ■■■■■■■■■■■■■                                │
 * │  ■■■■■■■■■■■■■  LOW_WATERMARK (30%)           │ → 触发额度回收
 * │                                                │
 * │                  0%                            │
 * └────────────────────────────────────────────────┘
 *
 * 贡献度追踪：
 * - borrowed_from_pool: 该渠道从共享池借了多少
 * - contributed_to_pool: 该渠道被回收了多少到共享池
 * - net_contribution = contributed - borrowed（净贡献度，用于下一轮权重调整）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelStockRebalanceService {

    private final PoiItemChannelStockMapper channelStockMapper;
    private final PoiItemStockMapper stockMapper;

    /**
     * 高水位线：渠道使用率超过此值时，说明渠道压力大
     */
    private static final BigDecimal HIGH_WATERMARK = new BigDecimal("80");

    /**
     * 低水位线：渠道使用率低于此值时，说明渠道空闲，额度可回收
     */
    private static final BigDecimal LOW_WATERMARK = new BigDecimal("30");

    /**
     * 回收比例：从空闲渠道回收多少比例的剩余额度（50%）
     */
    private static final BigDecimal RECLAIM_RATIO = new BigDecimal("0.5");

    /**
     * 最小回收量（避免频繁回收小额度）
     */
    private static final BigDecimal MIN_RECLAIM_AMOUNT = new BigDecimal("1");

    // ========================= 核心：动态再平衡 =========================

    /**
     * 执行一次动态再平衡
     *
     * 算法流程：
     * 1. 扫描空闲渠道（使用率 < LOW_WATERMARK）
     * 2. 从空闲渠道回收 50% 的剩余额度到共享池
     * 3. 记录贡献量（contributed_to_pool）
     * 4. 更新共享池总量
     *
     * 设计原则：
     * - 只回收空闲额度（channel_quota - channel_sold 的一部分），不影响已售
     * - 原子操作，WHERE (channel_quota - channel_sold) >= reclaimAmount
     * - 幂等安全：多次执行只会回收当前空闲的部分
     *
     * @return 再平衡结果摘要
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public RebalanceResult rebalance(Long stockId) {
        log.info("开始渠道库存动态再平衡: stockId={}", stockId);

        PoiItemStock stock = stockMapper.selectByIdForUpdate(stockId);
        if (stock == null) {
            log.warn("库存记录不存在: stockId={}", stockId);
            return RebalanceResult.skipped("库存记录不存在");
        }

        // 不限量不需要再平衡
        if (stock.getStockType() != null && stock.getStockType() == PoiItemStock.StockType.UNLIMITED.getCode()) {
            return RebalanceResult.skipped("不限量库存无需再平衡");
        }

        List<PoiItemChannelStock> channels = channelStockMapper.selectByStockId(stockId);
        if (channels.isEmpty()) {
            return RebalanceResult.skipped("无渠道库存");
        }

        BigDecimal totalReclaimed = BigDecimal.ZERO;
        int reclaimedChannels = 0;

        // Step 1: 从低使用率渠道回收额度到共享池
        for (PoiItemChannelStock ch : channels) {
            BigDecimal utilization = ch.calcUtilizationRate();

            if (utilization.compareTo(LOW_WATERMARK) < 0
                    && ch.getChannelRemaining().compareTo(MIN_RECLAIM_AMOUNT) >= 0) {

                // 计算回收量 = 剩余额度 × 回收比例，向下取整
                BigDecimal reclaimAmount = ch.getChannelRemaining()
                        .multiply(RECLAIM_RATIO)
                        .setScale(1, RoundingMode.DOWN);

                if (reclaimAmount.compareTo(MIN_RECLAIM_AMOUNT) < 0) {
                    continue;
                }

                // 原子回收（CAS: channel_quota - channel_sold >= reclaimAmount）
                int updated = channelStockMapper.reclaimChannelQuota(ch.getId(), reclaimAmount);
                if (updated > 0) {
                    totalReclaimed = totalReclaimed.add(reclaimAmount);
                    reclaimedChannels++;

                    log.info("回收渠道额度: channel={}, reclaimed={}, oldQuota={}, utilization={}%",
                            ch.getChannelCode(), reclaimAmount, ch.getChannelQuota(), utilization);
                }
            }
        }

        // Step 2: 将回收的额度注入共享池
        if (totalReclaimed.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newSharedPool = stock.getSharedPoolQuantity().add(totalReclaimed);
            stockMapper.updateSharedPoolQuantity(stockId, newSharedPool);

            log.info("共享池注入完成: stockId={}, injected={}, newSharedPool={}",
                    stockId, totalReclaimed, newSharedPool);
        }

        RebalanceResult result = new RebalanceResult(
                true, reclaimedChannels, totalReclaimed,
                "再平衡完成: 回收" + reclaimedChannels + "个渠道, 共" + totalReclaimed + "份注入共享池");

        log.info("渠道库存动态再平衡完成: stockId={}, result={}", stockId, result);
        return result;
    }

    // ========================= 贡献度分析 =========================

    /**
     * 基于贡献度动态调整渠道权重
     *
     * 核心算法：
     * 1. 计算每个渠道的净贡献度 = contributed_to_pool - borrowed_from_pool
     * 2. 正净贡献 = 渠道分配过多（权重应调低）
     * 3. 负净贡献 = 渠道分配不够（权重应调高）
     * 4. 新权重 = 旧权重 × (1 + adjustment_factor)
     *
     * 调整公式：
     *   net_contribution_rate = net_contribution / channel_quota
     *   如果 net_contribution_rate > 0（贡献者）：权重下调 → weight × (1 - rate × 0.1)
     *   如果 net_contribution_rate < 0（借调者）：权重上调 → weight × (1 + |rate| × 0.1)
     *   最终归一化使所有权重之和 = 1
     *
     * 设计意义：
     * - 自动学习渠道的真实需求，比手动设权重更准确
     * - 经过多轮调整后，渠道权重会收敛到与实际销售比例一致
     * - 类似于 TCP 拥塞控制的 AIMD 思想（加性增、乘性减）
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void adjustWeightsByContribution(Long stockId) {
        log.info("基于贡献度调整渠道权重: stockId={}", stockId);

        List<PoiItemChannelStock> channels = channelStockMapper.selectByStockId(stockId);
        if (channels.size() < 2) {
            log.info("渠道数不足2个，跳过权重调整");
            return;
        }

        // Step 1: 计算每个渠道的调整因子
        BigDecimal totalNewWeight = BigDecimal.ZERO;

        for (PoiItemChannelStock ch : channels) {
            BigDecimal netContribution = ch.getNetContribution();
            BigDecimal quota = ch.getChannelQuota();

            if (quota.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 净贡献率 = 净贡献 / 额度
            BigDecimal netRate = netContribution.divide(quota, 4, RoundingMode.HALF_UP);

            // 调整因子：贡献者权重下调，借调者权重上调
            // adjustment = -netRate × 0.1（贡献者 netRate > 0，所以 adjustment < 0，权重下调）
            BigDecimal adjustment = netRate.negate().multiply(new BigDecimal("0.1"));

            // 限制调整幅度在 [-0.2, +0.2] 之间，避免剧烈波动
            adjustment = adjustment.max(new BigDecimal("-0.2")).min(new BigDecimal("0.2"));

            BigDecimal newWeight = ch.getChannelWeight()
                    .multiply(BigDecimal.ONE.add(adjustment))
                    .max(new BigDecimal("0.05")); // 最低权重 5%

            ch.setChannelWeight(newWeight);
            totalNewWeight = totalNewWeight.add(newWeight);

            log.info("渠道权重调整: channel={}, netContribution={}, netRate={}, adjustment={}, newWeight={}",
                    ch.getChannelCode(), netContribution, netRate, adjustment, newWeight);
        }

        // Step 2: 归一化（所有权重之和 = 1）
        if (totalNewWeight.compareTo(BigDecimal.ZERO) > 0) {
            for (PoiItemChannelStock ch : channels) {
                BigDecimal normalizedWeight = ch.getChannelWeight()
                        .divide(totalNewWeight, 4, RoundingMode.HALF_UP);
                channelStockMapper.updateChannelQuotaAndWeight(
                        ch.getId(), ch.getChannelQuota(), normalizedWeight);

                log.info("渠道权重归一化: channel={}, weight={}", ch.getChannelCode(), normalizedWeight);
            }
        }

        // Step 3: 重置贡献追踪计数（新一轮开始）
        channelStockMapper.resetContributionTracking(stockId);

        log.info("渠道权重调整完成: stockId={}", stockId);
    }

    // ========================= 查询 =========================

    /**
     * 获取渠道库存全景视图（用于运营后台展示）
     */
    public ChannelStockPanorama getPanorama(Long stockId) {
        PoiItemStock stock = stockMapper.selectByIdForUpdate(stockId);
        List<PoiItemChannelStock> channels = channelStockMapper.selectByStockId(stockId);

        ChannelStockPanorama panorama = new ChannelStockPanorama();
        panorama.setStockId(stockId);
        panorama.setTotalQuantity(stock != null ? stock.getRealQuantity() : BigDecimal.ZERO);
        panorama.setSharedPoolQuantity(stock != null ? stock.getSharedPoolQuantity() : BigDecimal.ZERO);
        panorama.setChannels(channels);

        // 汇总统计
        BigDecimal totalBorrowed = BigDecimal.ZERO;
        BigDecimal totalContributed = BigDecimal.ZERO;
        for (PoiItemChannelStock ch : channels) {
            if (ch.getBorrowedFromPool() != null) totalBorrowed = totalBorrowed.add(ch.getBorrowedFromPool());
            if (ch.getContributedToPool() != null) totalContributed = totalContributed.add(ch.getContributedToPool());
        }
        panorama.setTotalBorrowed(totalBorrowed);
        panorama.setTotalContributed(totalContributed);

        return panorama;
    }

    // ========================= 内部类 =========================

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class RebalanceResult {
        private boolean executed;
        private int reclaimedChannels;
        private BigDecimal reclaimedAmount;
        private String message;

        public static RebalanceResult skipped(String reason) {
            return new RebalanceResult(false, 0, BigDecimal.ZERO, reason);
        }
    }

    @lombok.Data
    public static class ChannelStockPanorama {
        private Long stockId;
        private BigDecimal totalQuantity;
        private BigDecimal sharedPoolQuantity;
        private List<PoiItemChannelStock> channels;
        private BigDecimal totalBorrowed;
        private BigDecimal totalContributed;
    }
}
