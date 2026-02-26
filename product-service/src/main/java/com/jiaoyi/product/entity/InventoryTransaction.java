package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变动记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {
    
    private Long id;

    /**
     * 关联 inventory.id（POI渠道扣减用）
     */
    private Long inventoryId;

    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * SKU ID（关联product_sku.id）
     * 如果为null，表示商品级别的库存变动（兼容旧数据）
     */
    private Long skuId;
    
    /**
     * 分片ID（0-1023，基于 storeId 计算，用于分库分表路由）
     */
    private Integer productShardId;
    
    /**
     * 订单ID（订单相关变动必须有订单ID）
     */
    private Long orderId;
    
    /**
     * 变动类型：IN-入库，OUT-出库，LOCK-锁定，UNLOCK-解锁
     */
    private TransactionType transactionType;
    
    /**
     * 变动数量（正数为增加，负数为减少）
     */
    private Integer quantity;
    
    /**
     * 变动前库存
     */
    private Integer beforeStock;
    
    /**
     * 变动后库存
     */
    private Integer afterStock;
    
    /**
     * 变动前锁定库存
     */
    private Integer beforeLocked = 0;
    
    /**
     * 变动后锁定库存
     */
    private Integer afterLocked = 0;
    
    /**
     * 备注
     */
    private String remark;

    // ========================= POI 渠道库存扩展字段 =========================

    /**
     * BigDecimal版变更量（POI渠道扣减用）
     * 负数表示扣减，正数表示补货
     */
    private BigDecimal delta;

    /**
     * 扣减来源：FROM_CHANNEL / FROM_SHARED_POOL / FROM_SAFETY_STOCK / FROM_POOL
     */
    private String deductSource;

    /**
     * 渠道代码：POS / KIOSK / ONLINE_ORDER
     */
    private String channelCode;

    /**
     * POI变更类型：ABSOLUTE_SET / RELATIVE_DELTA / STATUS_CHANGE
     */
    private String changeTypePoi;

    /**
     * 来源：POS / CLOUD / POS_OFFLINE
     */
    private String sourcePoi;

    /**
     * JSON快照（记录操作时的请求内容）
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 库存变动类型枚举
     */
    public enum TransactionType {
        IN("入库"),
        OUT("出库"),
        LOCK("锁定"),
        UNLOCK("解锁"),
        ADJUST("调整");
        
        private final String description;
        
        TransactionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}


