package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    
    /**
     * 库存模式枚举
     */
    public enum StockMode {
        /**
         * 无限库存（不设库存限制，默认可售无限）
         */
        UNLIMITED,

        /**
         * 有限库存（需要管控库存数量）
         */
        LIMITED;

        /**
         * JSON 序列化时使用枚举名称
         */
        @com.fasterxml.jackson.annotation.JsonValue
        public String toValue() {
            return this.name();
        }
    }

    /**
     * 库存恢复模式枚举
     */
    public enum RestoreMode {
        /**
         * 手动恢复（默认）
         */
        MANUAL("手动恢复"),

        /**
         * 次日自动恢复（每天凌晨0点）
         */
        TOMORROW("次日自动恢复"),

        /**
         * 指定时间恢复
         */
        SCHEDULED("指定时间恢复");

        private final String description;

        RestoreMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String toValue() {
            return this.name();
        }
    }
    
    private Long id;
    
    /**
     * 店铺ID
     */
    private Long storeId;
    
    /**
     * 分片ID（0-1023，基于storeId计算，用于分库分表路由）
     */
    private Integer productShardId;
    
    /**
     * 商品ID（关联store_products.id）
     */
    private Long productId;
    
    /**
     * SKU ID（关联product_sku.id）
     * 如果为null，表示商品级别的库存（兼容旧数据）
     */
    private Long skuId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * SKU名称（如果存在SKU）
     */
    private String skuName;
    
    /**
     * 库存模式：UNLIMITED（无限库存）或 LIMITED（有限库存）
     * 默认：UNLIMITED（不设库存限制，类似美团商家不设库存的场景）
     */
    private StockMode stockMode = StockMode.UNLIMITED;
    
    /**
     * 当前库存数量
     * 仅在 stockMode = LIMITED 时有效
     */
    private Integer currentStock;
    
    /**
     * 锁定库存数量（已下单但未支付）
     * 仅在 stockMode = LIMITED 时有效
     */
    private Integer lockedStock = 0;
    
    /**
     * 最低库存预警线
     * 仅在 stockMode = LIMITED 时有效
     */
    private Integer minStock = 0;
    
    /**
     * 最大库存容量
     * 仅在 stockMode = LIMITED 时有效
     */
    private Integer maxStock;

    /**
     * 库存恢复模式
     * MANUAL - 手动恢复（默认）
     * TOMORROW - 次日自动恢复
     * SCHEDULED - 指定时间恢复
     */
    private RestoreMode restoreMode = RestoreMode.MANUAL;

    /**
     * 恢复时间（仅在 restoreMode = SCHEDULED 时有效）
     */
    private LocalDateTime restoreTime;

    /**
     * 恢复后的库存数量
     * null表示恢复为不限库存（UNLIMITED模式）
     * 非null表示恢复为指定数量（LIMITED模式）
     */
    private Integer restoreStock;

    /**
     * 上次恢复时间（记录最近一次自动恢复的时间）
     */
    private LocalDateTime lastRestoreTime;

    /**
     * 是否启用自动恢复
     */
    private Boolean restoreEnabled = false;

    // ========================= POI 渠道库存扩展字段 =========================

    /**
     * 计划库存（POS设置值）
     */
    private BigDecimal planQuantity = BigDecimal.ZERO;

    /**
     * 渠道分配模式：WEIGHTED_QUOTA=加权配额+共享池, SAFETY_STOCK=安全线保护
     */
    private String allocationMode = "WEIGHTED_QUOTA";

    /**
     * 共享池库存（不分配给任何渠道的弹性库存）
     */
    private BigDecimal sharedPoolQuantity = BigDecimal.ZERO;

    /**
     * 最后手动绝对设置时间（冲突合并用）
     */
    private LocalDateTime lastManualSetTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}


