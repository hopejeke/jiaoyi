package com.jiaoyi.order.dto;

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
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
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
     * 订单项请求DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        
        /**
         * 商品ID
         */
        @NotNull(message = "商品ID不能为空")
        private Long productId;
        
        /**
         * 商品名称
         */
        @NotBlank(message = "商品名称不能为空")
        private String productName;
        
        /**
         * 商品图片
         */
        private String productImage;
        
        /**
         * 商品单价
         */
        @NotNull(message = "商品单价不能为空")
        private java.math.BigDecimal unitPrice;
        
        /**
         * 购买数量
         */
        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
    }
}


