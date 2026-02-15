package com.jiaoyi.product.service.strategy;

import com.jiaoyi.product.dto.ChannelDeductRequest;
import com.jiaoyi.product.dto.ChannelDeductResult;
import com.jiaoyi.product.entity.PoiItemChannelStock;
import com.jiaoyi.product.entity.PoiItemStock;

/**
 * 渠道库存扣减策略接口
 *
 * 使用策略模式将扣减逻辑从 PoiItemStockService 中抽离出来，
 * 使得不同的业务场景可以使用不同的扣减策略：
 *
 * 1. StandardDeductionStrategy  - 标准三层漏斗: 渠道额度 → 共享池 → 拒绝
 * 2. StrictChannelStrategy      - 严格渠道隔离: 渠道额度 → 拒绝（不允许借共享池）
 * 3. SharedPoolFirstStrategy    - 共享池优先: 共享池 → 渠道额度 → 拒绝（促销活动场景）
 *
 * 扩展点：
 * - 可以按 brandId / poiId / channelCode 做策略路由
 * - 可以在运营后台动态切换策略（通过配置中心）
 */
public interface ChannelDeductionStrategy {

    /**
     * 策略名称（用于日志和监控）
     */
    String name();

    /**
     * 执行扣减
     *
     * @param stock 库存主记录
     * @param channelStock 渠道库存（可能为 null，表示渠道未配置）
     * @param request 扣减请求
     * @return 扣减结果
     */
    ChannelDeductResult deduct(PoiItemStock stock, PoiItemChannelStock channelStock, ChannelDeductRequest request);
}
