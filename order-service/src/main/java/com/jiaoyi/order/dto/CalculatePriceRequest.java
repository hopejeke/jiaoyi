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
 * 计算订单价格请求DTO
 * 用于前端在提交订单前预览价格
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatePriceRequest {

    @NotBlank(message = "商户ID不能为空")
    private String merchantId;

    /**
     * 用户ID
     * 支持从字符串类型反序列化，避免 JavaScript 大整数精度丢失问题
     */
    @NotNull(message = "用户ID不能为空")
    @JsonDeserialize(using = LongStringDeserializer.class)
    private Long userId;

    @NotBlank(message = "订单类型不能为空")
    private String orderType; // PICKUP, DELIVERY, SELF_DINE_IN

    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<OrderItemRequest> orderItems;

    /**
     * 收货地址（用于计算配送费）
     */
    private String receiverAddress;

    /**
     * 邮编（用于 ZONE_RATE 配送费计算）
     */
    private String zipCode;

    /**
     * 配送地址纬度（用于 VARIABLE_RATE 配送费计算）
     */
    private Double latitude;

    /**
     * 配送地址经度（用于 VARIABLE_RATE 配送费计算）
     */
    private Double longitude;

    /**
     * 优惠券ID列表（可选）
     */
    private List<Long> couponIds;

    /**
     * 优惠券代码列表（可选）
     */
    private List<String> couponCodes;

    /**
     * 小费（可选，暂时不支持）
     */
    private java.math.BigDecimal tips;

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
         * SKU ID（如果商品有SKU，必须提供）
         * 支持从字符串类型反序列化，避免 JavaScript 大整数精度丢失问题
         */
        @JsonDeserialize(using = LongStringDeserializer.class)
        private Long skuId;
        
        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
    }
}

