package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存超卖记录实体（与 inventory 绑定分片）
 * 对应 inventory_oversell_records 分片表
 *
 * 状态流转：PENDING → CONFIRMED / REFUND → RESOLVED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOversellRecord {

    public enum Status {
        PENDING("待处理"),
        CONFIRMED("已确认可做"),
        REFUND("需退款"),
        RESOLVED("已解决");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private Long id;

    /**
     * 关联 inventory.id
     */
    private Long inventoryId;

    /**
     * 门店ID（分片键）
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
     * 分片路由键
     */
    private Integer productShardId;

    /**
     * 超卖数量
     */
    private BigDecimal oversellQuantity;

    /**
     * 来源：POS_OFFLINE 等
     */
    private String source;

    /**
     * 处理状态：PENDING / CONFIRMED / REFUND / RESOLVED
     */
    private String status;

    /**
     * 处理人
     */
    private String resolvedBy;

    /**
     * 处理时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
