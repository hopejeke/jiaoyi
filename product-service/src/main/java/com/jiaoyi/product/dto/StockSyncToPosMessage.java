package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品中心下发库存变更消息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSyncToPosMessage {
    
    /**
     * 品牌ID
     */
    private String brandId;
    
    /**
     * 门店ID
     */
    private String poiId;
    
    /**
     * 对象类型
     */
    private String objectType = "item_stock";
    
    /**
     * 事件类型
     */
    private String eventType = "poi.item.stock";
    
    /**
     * 数据
     */
    private StockData data;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockData {
        /**
         * 对象ID
         */
        private Long objectId;
        
        /**
         * 库存状态：1-可售, 2-售罄
         */
        private Integer stockStatus;
        
        /**
         * 库存类型：1-不限量, 2-限量
         */
        private Integer stockType;
        
        /**
         * 计划库存份数
         */
        private BigDecimal planQuantity;
        
        /**
         * 实时库存
         */
        private BigDecimal realQuantity;
        
        /**
         * 自动恢复类型：1-自动恢复, 2-不自动恢复
         */
        private Integer autoRestoreType;
        
        /**
         * 恢复时间
         */
        private LocalDateTime autoRestoreAt;
        
        /**
         * 渠道库存列表
         */
        private List<ChannelStock> channelStocks;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelStock {
        /**
         * 库存状态：1-可售, 2-售罄
         */
        private Integer stockStatus;
        
        /**
         * 库存类型：1-不限量, 2-共享限量
         */
        private Integer stockType;
        
        /**
         * 渠道代码：POS, KIOSK, ONLINE_ORDER
         */
        private String channelCode;
    }
}
