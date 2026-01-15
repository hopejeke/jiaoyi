package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Refund {
    
    /**
     * 退款ID
     */
    private Long refundId;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 关联的支付记录ID
     */
    private Long paymentId;
    
    /**
     * 商户ID
     */
    private String merchantId;
    
    /**
     * 门店ID（用于分片，与商品服务保持一致）
     */
    private Long storeId;
    
    /**
     * 分片ID（0-1023，基于 storeId 计算，用于分库分表路由）
     * 注意：此字段必须与 storeId 一起设置，确保分片一致性
     */
    private Integer shardId;
    
    /**
     * 退款请求号（幂等键）
     */
    private String requestNo;
    
    /**
     * 退款总金额
     */
    private BigDecimal refundAmount;
    
    /**
     * 退款原因
     */
    private String reason;
    
    /**
     * 退款状态：CREATED, PROCESSING, SUCCEEDED, FAILED, CANCELED
     */
    private String status;
    
    /**
     * 第三方退款ID（Stripe refund_id 或支付宝退款单号）
     */
    private String thirdPartyRefundId;
    
    /**
     * 失败原因
     */
    private String errorMessage;
    
    /**
     * 版本号（乐观锁）
     */
    private Long version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 处理完成时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 抽成回补金额（平台手续费回补）
     * 计算公式：回补抽成 = (退款金额 / 原订单总额) × 总平台抽成
     */
    private BigDecimal commissionReversal;
}

