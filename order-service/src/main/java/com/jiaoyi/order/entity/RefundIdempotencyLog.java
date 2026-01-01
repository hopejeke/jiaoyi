package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 退款幂等性日志表
 * 用于记录退款请求的幂等性信息，防止重复处理
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundIdempotencyLog {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 退款请求号（幂等键，唯一索引）
     */
    private String requestNo;
    
    /**
     * 退款ID（关联到 refunds 表）
     */
    private Long refundId;
    
    /**
     * 商户ID（分片键）
     */
    private String merchantId;
    
    /**
     * 请求指纹（MD5(requestNo + orderId + amount + timestamp)）
     * 用于额外的幂等性校验
     */
    private String fingerprint;
    
    /**
     * 请求参数（JSON格式，用于调试和审计）
     */
    private String requestParams;
    
    /**
     * 处理结果（SUCCESS/FAILED）
     */
    private String result;
    
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
}




