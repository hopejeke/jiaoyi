package com.jiaoyi.dto;

import com.jiaoyi.entity.Order;
import com.jiaoyi.entity.OrderItem;
import com.jiaoyi.entity.OrderStatus;
import com.jiaoyi.entity.OrderCoupon;
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
    private String orderNo;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal totalDiscountAmount;
    private List<OrderCouponResponse> orderCoupons;
    private BigDecimal actualAmount;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<OrderItemResponse> orderItems;
    
    /**
     * 从Order实体转换为OrderResponse
     */
    public static OrderResponse fromOrder(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setTotalDiscountAmount(order.getTotalDiscountAmount());
        
        if (order.getOrderCoupons() != null) {
            response.setOrderCoupons(
                order.getOrderCoupons().stream()
                    .map(OrderCouponResponse::fromOrderCoupon)
                    .collect(Collectors.toList())
            );
        }
        response.setActualAmount(order.getActualAmount());
        response.setReceiverName(order.getReceiverName());
        response.setReceiverPhone(order.getReceiverPhone());
        response.setReceiverAddress(order.getReceiverAddress());
        response.setRemark(order.getRemark());
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
     * 订单项响应DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal subtotal;
        
        /**
         * 从OrderItem实体转换为OrderItemResponse
         */
        public static OrderItemResponse fromOrderItem(OrderItem orderItem) {
            OrderItemResponse response = new OrderItemResponse();
            response.setId(orderItem.getId());
            response.setProductId(orderItem.getProductId());
            response.setProductName(orderItem.getProductName());
            response.setProductImage(orderItem.getProductImage());
            response.setUnitPrice(orderItem.getUnitPrice());
            response.setQuantity(orderItem.getQuantity());
            response.setSubtotal(orderItem.getSubtotal());
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
