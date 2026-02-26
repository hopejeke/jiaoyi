package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 渠道库存扣减请求DTO
 *
 * 扣减逻辑：
 * 1. 先扣渠道专属额度（channel_quota - channel_sold）
 * 2. 渠道额度不够 → 从共享池借（shared_pool_quantity）
 * 3. 共享池也不够 → 售罄
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDeductRequest {

    /** 门店ID */
    private Long storeId;

    /** 商品ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 渠道代码：POS, KIOSK, ONLINE_ORDER */
    private String channelCode;

    /** 扣减数量（正数） */
    private BigDecimal quantity;

    /** 关联订单ID（用于幂等） */
    private String orderId;
}
