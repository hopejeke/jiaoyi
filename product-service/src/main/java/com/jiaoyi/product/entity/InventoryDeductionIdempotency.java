package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存扣减幂等性日志实体类
 * 用于记录库存扣减请求的幂等性信息，防止重复处理
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductionIdempotency {
    
    private Long id;
    
    /**
     * 幂等键（唯一标识，格式：orderId + "-DEDUCT"）
     */
    private String idempotencyKey;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 分片ID（0-1023，基于storeId计算，用于分库分表路由）
     */
    private Integer productShardId;
    
    /**
     * 商品ID列表（JSON格式）
     */
    private String productIds;
    
    /**
     * SKU ID列表（JSON格式）
     */
    private String skuIds;
    
    /**
     * 数量列表（JSON格式）
     */
    private String quantities;
    
    /**
     * 状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败
     */
    private Status status;
    
    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 状态枚举
     */
    public enum Status {
        PROCESSING("处理中"),
        SUCCESS("成功"),
        FAILED("失败");
        
        private final String description;
        
        Status(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

