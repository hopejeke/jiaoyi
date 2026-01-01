package com.jiaoyi.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    
    /**
     * 退款ID
     */
    private Long refundId;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 退款请求号
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
     * 退款状态
     */
    private String status;
    
    /**
     * 第三方退款ID
     */
    private String thirdPartyRefundId;
    
    /**
     * 失败原因
     */
    private String errorMessage;
    
    /**
     * 抽成回补金额（平台手续费回补）
     */
    private BigDecimal commissionReversal;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 处理完成时间
     */
    private LocalDateTime processedAt;
    
    /**
     * 退款明细列表
     */
    private List<RefundItemResponse> refundItems;
}

