package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 超卖记录实体
 * 
 * 记录超卖事件，供店长确认处理：
 * - PENDING: 等待店长确认
 * - CONFIRMED: 店长确认可以制作，继续履约
 * - REFUND: 无法制作，需要退款
 * - RESOLVED: 已处理完成
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OversellRecord {
    
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
    private String brandId;
    private String storeId;
    private Long stockId;
    private Long objectId;
    private BigDecimal oversellQuantity;
    private String source;
    private String status;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String remark;
    private LocalDateTime createdAt;
}
