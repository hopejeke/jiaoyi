package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品中心库存主表实体类
 * 对应 poi_item_stock 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoiItemStock {
    
    /**
     * 对象类型枚举
     */
    public enum ObjectType {
        SPU(1, "SPU"),
        SKU(2, "SKU");
        
        private final int code;
        private final String description;
        
        ObjectType(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static ObjectType fromCode(int code) {
            for (ObjectType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown ObjectType code: " + code);
        }
    }
    
    /**
     * 库存状态枚举
     */
    public enum StockStatus {
        IN_STOCK(1, "可售"),
        OUT_OF_STOCK(2, "售罄");
        
        private final int code;
        private final String description;
        
        StockStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static StockStatus fromCode(int code) {
            for (StockStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown StockStatus code: " + code);
        }
    }
    
    /**
     * 库存类型枚举
     */
    public enum StockType {
        UNLIMITED(1, "不限量"),
        LIMITED(2, "限量");
        
        private final int code;
        private final String description;
        
        StockType(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static StockType fromCode(int code) {
            for (StockType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown StockType code: " + code);
        }
    }
    
    /**
     * 自动恢复类型枚举
     */
    public enum AutoRestoreType {
        AUTO_RESTORE(1, "自动恢复"),
        NO_AUTO_RESTORE(2, "不自动恢复");
        
        private final int code;
        private final String description;
        
        AutoRestoreType(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static AutoRestoreType fromCode(int code) {
            for (AutoRestoreType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown AutoRestoreType code: " + code);
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
    private String storeId;
    
    /**
     * 对象类型：1-SPU, 2-SKU
     */
    private Integer objectType;
    
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
    private Integer stockType = 0;
    
    /**
     * 计划库存份数
     */
    private BigDecimal planQuantity = BigDecimal.ZERO;
    
    /**
     * 实时库存
     */
    private BigDecimal realQuantity = BigDecimal.ZERO;
    
    /**
     * 自动恢复类型：1-自动恢复, 2-不自动恢复
     */
    private Integer autoRestoreType = 0;
    
    /**
     * 恢复时间
     */
    private LocalDateTime autoRestoreAt;
    
    /**
     * 共享池库存（不分配给任何渠道的弹性库存）
     */
    private BigDecimal sharedPoolQuantity = BigDecimal.ZERO;
    
    /**
     * 最后一次手动设置（ABSOLUTE_SET）时间
     * 用于冲突合并：绝对设置 vs 相对变更
     */
    private LocalDateTime lastManualSetTime;
    
    /**
     * 渠道库存分配模式：WEIGHTED_QUOTA=加权配额+共享池, SAFETY_STOCK=安全线保护
     */
    private String allocationMode = "WEIGHTED_QUOTA";
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
