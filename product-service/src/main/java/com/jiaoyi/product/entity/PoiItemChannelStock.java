package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品渠道库存表实体类
 * 对应 poi_item_channel_stock 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoiItemChannelStock {
    
    /**
     * 渠道代码枚举
     */
    public enum ChannelCode {
        POS("POS", "POS端"),
        KIOSK("KIOSK", "KIOSK端"),
        ONLINE_ORDER("ONLINE_ORDER", "在线订单");
        
        private final String code;
        private final String description;
        
        ChannelCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static ChannelCode fromCode(String code) {
            for (ChannelCode channel : values()) {
                if (channel.code.equals(code)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("Unknown ChannelCode: " + code);
        }
    }
    
    private Long id;
    
    /**
     * 品牌ID
     */
    private String brandId;
    
    /**
     * 门店ID
     */
    private String poiId;
    
    /**
     * 关联 poi_item_stock.id
     */
    private Long stockId;
    
    /**
     * 库存状态：1-可售, 2-售罄
     */
    private Integer stockStatus;
    
    /**
     * 库存类型：1-不限量, 2-共享限量
     */
    private Integer stockType = 0;
    
    /**
     * 渠道代码：POS, KIOSK, ONLINE_ORDER
     */
    private String channelCode;
    
    /**
     * 渠道分配额度
     */
    private java.math.BigDecimal channelQuota = java.math.BigDecimal.ZERO;
    
    /**
     * 渠道已售数量
     */
    private java.math.BigDecimal channelSold = java.math.BigDecimal.ZERO;
    
    /**
     * 渠道分配权重（0-1，所有渠道权重之和=1）
     */
    private java.math.BigDecimal channelWeight = new java.math.BigDecimal("0.33");
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 获取渠道剩余可用额度
     */
    public java.math.BigDecimal getChannelRemaining() {
        if (channelQuota == null || channelSold == null) return java.math.BigDecimal.ZERO;
        return channelQuota.subtract(channelSold);
    }
}
