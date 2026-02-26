package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道库存实体（与 inventory 绑定分片）
 * 对应 inventory_channels 分片表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryChannel {

    private Long id;

    /**
     * 关联 inventory.id
     */
    private Long inventoryId;

    /**
     * 门店ID（分片键，与 inventory.storeId 一致）
     */
    private Long storeId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 分片路由键（同 inventory.productShardId）
     */
    private Integer productShardId;

    /**
     * 渠道代码：POS / KIOSK / ONLINE_ORDER
     */
    private String channelCode;

    /**
     * 渠道分配额度（加权配额模式）
     */
    private BigDecimal channelQuota = BigDecimal.ZERO;

    /**
     * 渠道已售数量
     */
    private BigDecimal channelSold = BigDecimal.ZERO;

    /**
     * 渠道分配权重（0-1）
     */
    private BigDecimal channelWeight = new BigDecimal("0.33");

    /**
     * 渠道可售上限（方案一：单池+渠道上限，0=不设上限）
     */
    private BigDecimal channelMax = BigDecimal.ZERO;

    /**
     * 渠道优先级（方案二：安全线保护，数值越大优先级越高）
     */
    private Integer channelPriority = 0;

    /**
     * 渠道安全线（方案二：当总库存低于高优先级渠道安全线时，锁定低优先级渠道）
     */
    private BigDecimal safetyStock = BigDecimal.ZERO;

    /**
     * 库存状态：1-可售, 2-售罄
     */
    private Integer stockStatus;

    /**
     * 库存类型：1-不限量, 2-限量
     */
    private Integer stockType;

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
    public BigDecimal getChannelRemaining() {
        if (channelQuota == null || channelSold == null) return BigDecimal.ZERO;
        return channelQuota.subtract(channelSold);
    }
}
