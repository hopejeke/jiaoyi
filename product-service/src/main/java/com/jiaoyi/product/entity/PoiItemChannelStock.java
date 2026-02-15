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
     * 从共享池借调累计量
     * 记录该渠道从共享池"借"了多少库存（渠道额度不够时触发借调）
     * 用于贡献度分析和动态权重调整
     */
    private java.math.BigDecimal borrowedFromPool = java.math.BigDecimal.ZERO;

    /**
     * 向共享池贡献累计量
     * 记录该渠道因额度过剩被回收了多少库存到共享池（水位线低于阈值时触发回收）
     * 用于贡献度分析和动态权重调整
     */
    private java.math.BigDecimal contributedToPool = java.math.BigDecimal.ZERO;

    /**
     * 渠道使用率水位线（0-100）
     * 自动计算: channel_sold / channel_quota * 100
     * HIGH_WATERMARK(80) = 渠道压力大，触发共享池借调
     * LOW_WATERMARK(30)  = 渠道空闲，触发额度回收到共享池
     */
    private java.math.BigDecimal utilizationRate = java.math.BigDecimal.ZERO;

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

    /**
     * 计算净贡献度 = 贡献量 - 借调量
     * 正数 = 净贡献者（渠道额度分配过多，有富余）
     * 负数 = 净借调者（渠道额度不够，需要从共享池借）
     * 用于下一轮权重动态调整的依据
     */
    public java.math.BigDecimal getNetContribution() {
        java.math.BigDecimal contributed = contributedToPool != null ? contributedToPool : java.math.BigDecimal.ZERO;
        java.math.BigDecimal borrowed = borrowedFromPool != null ? borrowedFromPool : java.math.BigDecimal.ZERO;
        return contributed.subtract(borrowed);
    }

    /**
     * 计算实时使用率（百分比）
     */
    public java.math.BigDecimal calcUtilizationRate() {
        if (channelQuota == null || channelQuota.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return java.math.BigDecimal.ZERO;
        }
        java.math.BigDecimal sold = channelSold != null ? channelSold : java.math.BigDecimal.ZERO;
        return sold.multiply(new java.math.BigDecimal("100"))
                .divide(channelQuota, 1, java.math.RoundingMode.HALF_UP);
    }
}
