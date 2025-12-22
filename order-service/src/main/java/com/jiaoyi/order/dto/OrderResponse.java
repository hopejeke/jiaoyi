package com.jiaoyi.order.dto;

import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.entity.OrderCoupon;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    
    private Long id;
    private String merchantId;
    private Long userId;
    private Integer status; // 在线点餐订单状态：1=已下单，2=已支付，3=制作中，4=已完成，5=已取消
    private String orderType; // 订单类型：PICKUP/DELIVERY/SELF_DINE_IN
    private String orderPrice; // 订单价格JSON字符串
    private String notes; // 备注
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<OrderItemResponse> orderItems;
    
    /**
     * 从Order实体转换为OrderResponse（在线点餐）
     */
    public static OrderResponse fromOrder(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setMerchantId(order.getMerchantId());
        response.setUserId(order.getUserId());
        response.setStatus(order.getStatus()); // Integer 类型
        response.setOrderType(order.getOrderType() != null ? order.getOrderType().getCode() : null);
        response.setOrderPrice(order.getOrderPrice());
        response.setNotes(order.getNotes());
        response.setCreateTime(order.getCreateTime());
        response.setUpdateTime(order.getUpdateTime());
        
        if (order.getOrderItems() != null) {
            response.setOrderItems(
                order.getOrderItems().stream()
                    .map(OrderItemResponse::fromOrderItem)
                    .collect(Collectors.toList())
            );
        }
        
        return response;
    }
    
    /**
     * 订单项响应DTO（在线点餐）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String itemName; // 在线点餐使用 itemName
        private String productImage;
        private BigDecimal itemPrice; // 在线点餐使用 itemPrice
        private Integer quantity;
        private BigDecimal itemPriceTotal; // 在线点餐使用 itemPriceTotal
        
        /**
         * 从OrderItem实体转换为OrderItemResponse
         */
        public static OrderItemResponse fromOrderItem(OrderItem orderItem) {
            OrderItemResponse response = new OrderItemResponse();
            response.setId(orderItem.getId());
            response.setProductId(orderItem.getProductId());
            response.setItemName(orderItem.getItemName());
            response.setProductImage(orderItem.getProductImage());
            response.setItemPrice(orderItem.getItemPrice());
            response.setQuantity(orderItem.getQuantity());
            response.setItemPriceTotal(orderItem.getItemPriceTotal());
            return response;
        }
    }
    
    /**
     * 订单优惠券响应DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCouponResponse {
        private Long id;
        private Long orderId;
        private Long couponId;
        private String couponCode;
        private BigDecimal appliedAmount;
        private LocalDateTime createTime;
        
        /**
         * 从OrderCoupon实体转换为OrderCouponResponse
         */
        public static OrderCouponResponse fromOrderCoupon(OrderCoupon orderCoupon) {
            OrderCouponResponse response = new OrderCouponResponse();
            response.setId(orderCoupon.getId());
            response.setOrderId(orderCoupon.getOrderId());
            response.setCouponId(orderCoupon.getCouponId());
            response.setCouponCode(orderCoupon.getCouponCode());
            response.setAppliedAmount(orderCoupon.getAppliedAmount());
            response.setCreateTime(orderCoupon.getCreateTime());
            return response;
        }
    }
}


