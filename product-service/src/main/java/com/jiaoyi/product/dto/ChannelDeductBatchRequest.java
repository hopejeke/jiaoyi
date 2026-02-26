package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 渠道库存批量扣减请求DTO（一单多品）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDeductBatchRequest {

    /** 门店ID */
    private Long storeId;

    /** 渠道代码：POS, KIOSK, ONLINE_ORDER */
    private String channelCode;

    /** 订单ID（用于幂等） */
    private String orderId;

    /** 扣减明细列表 */
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 商品ID */
        private Long productId;

        /** SKU ID */
        private Long skuId;

        /** 扣减数量 */
        private BigDecimal quantity;
    }
}
