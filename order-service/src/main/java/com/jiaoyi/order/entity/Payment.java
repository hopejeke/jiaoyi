package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体类
 * 参照 OO 项目的 Payment 模型设计
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    /**
     * 支付ID
     */
    private Long id;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 交易ID（Transaction ID，用于关联交易记录）
     */
    private Long transactionId;
    
    /**
     * 商户ID
     */
    private String merchantId;
    
    /**
     * 支付状态：100-成功，200-失败
     */
    private Integer status;
    
    /**
     * 支付类型：100-扣款，200-退款
     */
    private Integer type;
    
    /**
     * 支付方式类别：1-信用卡，7-现金，8-微信支付，9-支付宝
     */
    private Integer category;
    
    /**
     * 支付服务：STRIPE, ALIPAY, WECHAT_PAY, CASH
     */
    private String paymentService;
    
    /**
     * 支付流水号
     */
    private String paymentNo;
    
    /**
     * 第三方支付平台交易号
     */
    private String thirdPartyTradeNo;
    
    /**
     * 支付金额
     */
    private BigDecimal amount;
    
    /**
     * 小费金额
     */
    private BigDecimal tipAmount;
    
    /**
     * 订单价格信息（JSON）
     */
    private String orderPrice;
    
    /**
     * 卡片信息（JSON，用于信用卡支付）
     */
    private String cardInfo;
    
    /**
     * 额外信息（JSON，存储第三方支付平台的完整响应）
     */
    private String extra;
    
    /**
     * Stripe Payment Intent ID（用于异步支付）
     */
    private String stripePaymentIntentId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 版本号（用于乐观锁）
     */
    private Integer version;
}


