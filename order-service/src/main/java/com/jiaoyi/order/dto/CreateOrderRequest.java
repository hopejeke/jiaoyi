package com.jiaoyi.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaoyi.order.config.LongStringDeserializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建订单请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    /**
     * 商户ID（餐馆ID）
     */
    @NotBlank(message = "商户ID不能为空")
    private String merchantId;
    
    /**
     * 用户ID
     * 支持从字符串类型反序列化，避免 JavaScript 大整数精度丢失问题
     */
    @NotNull(message = "用户ID不能为空")
    @JsonDeserialize(using = LongStringDeserializer.class)
    private Long userId;
    
    /**
     * 订单类型：PICKUP-自取，DELIVERY-配送，SELF_DINE_IN-堂食
     */
    @NotBlank(message = "订单类型不能为空")
    private String orderType;
    
    /**
     * 收货人姓名
     */
    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;
    
    /**
     * 收货人电话
     */
    @NotBlank(message = "收货人电话不能为空")
    private String receiverPhone;
    
    /**
     * 收货地址
     */
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 优惠券ID列表
     */
    private List<Long> couponIds;
    
    /**
     * 优惠券代码列表
     */
    private List<String> couponCodes;
    
    /**
     * 订单项列表
     */
    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<OrderItemRequest> orderItems;
    
    /**
     * 支付方式：ALIPAY-支付宝，WECHAT_PAY-微信，CREDIT_CARD-信用卡，CASH-现金
     */
    private String paymentMethod;
    
    /**
     * 是否在线支付（true-在线支付，false-货到付款/现金支付）
     */
    private Boolean payOnline = false;
    
    /**
     * 支付信息（JSON格式，包含支付方式相关参数）
     * 例如：{"paymentMethodId": "pm_xxx"} 用于保存的卡片
     */
    private String paymentInfo;
    
    /**
     * 订单项请求DTO
     * 注意：为了安全，商品价格由后端从数据库查询，前端不传 unitPrice
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        
        /**
         * 商品ID
         * 支持从字符串类型反序列化，避免 JavaScript 大整数精度丢失问题
         */
        @NotNull(message = "商品ID不能为空")
        @JsonDeserialize(using = LongStringDeserializer.class)
        private Long productId;
        
        /**
         * 商品名称（可选，后端会从数据库查询，但前端可以传用于显示）
         */
        private String productName;
        
        /**
         * 商品图片（可选，后端会从数据库查询，但前端可以传用于显示）
         */
        private String productImage;
        
        /**
         * 购买数量
         */
        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
        
        /**
         * 商品单价（已废弃，为了安全，价格由后端从数据库查询）
         * 保留此字段是为了向后兼容，但后端会忽略此值
         */
        @Deprecated
        private java.math.BigDecimal unitPrice;
    }
}


