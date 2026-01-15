package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DoorDash 创建失败补偿任务实体
 * 用于记录支付成功但 DoorDash 订单创建失败的订单，支持自动重试和人工介入
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoorDashRetryTask {
    
    /**
     * 任务ID（主键）
     */
    private Long id;
    
    /**
     * 订单ID（关联 orders.id）
     */
    private Long orderId;
    
    /**
     * 商户ID
     */
    private String merchantId;
    
    /**
     * 门店ID（用于分片，与商品服务保持一致）
     */
    private Long storeId;
    
    /**
     * 支付ID（关联 payments.id）
     */
    private Long paymentId;
    
    /**
     * 任务状态：PENDING-待重试，RETRYING-重试中，SUCCESS-成功，FAILED-失败（超过最大重试次数），MANUAL-需要人工介入
     */
    private String status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数（默认3次）
     */
    private Integer maxRetryCount;
    
    /**
     * 错误信息（最后一次失败的错误信息）
     */
    private String errorMessage;
    
    /**
     * 错误堆栈（最后一次失败的错误堆栈）
     */
    private String errorStack;
    
    /**
     * 下次重试时间（用于延迟重试）
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * 最后重试时间
     */
    private LocalDateTime lastRetryTime;
    
    /**
     * 成功时间（创建成功时记录）
     */
    private LocalDateTime successTime;
    
    /**
     * 人工介入时间（标记为需要人工介入时记录）
     */
    private LocalDateTime manualInterventionTime;
    
    /**
     * 人工介入备注
     */
    private String manualInterventionNote;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING("PENDING", "待重试"),
        RETRYING("RETRYING", "重试中"),
        SUCCESS("SUCCESS", "成功"),
        FAILED("FAILED", "失败（超过最大重试次数）"),
        MANUAL("MANUAL", "需要人工介入");
        
        private final String code;
        private final String description;
        
        TaskStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
}






