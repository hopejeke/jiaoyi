package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.dto.*;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.enums.RefundSubject;
import com.jiaoyi.order.enums.RefundType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 退款计算器
 * 负责计算退款明细，包括商品退款、税费退款、折扣退款等
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundCalculator {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 计算退款明细
     */
    public RefundCalculationResult calculateRefund(
            Order order, 
            List<OrderItem> orderItems,
            RefundRequest request) {
        
        // 1. 解析订单价格
        Map<String, Object> orderPrice = parseOrderPrice(order);
        BigDecimal subtotal = getBigDecimal(orderPrice, "subtotal");
        BigDecimal taxTotal = getBigDecimal(orderPrice, "taxTotal");
        BigDecimal deliveryFee = getBigDecimal(orderPrice, "deliveryFee");
        BigDecimal tips = getBigDecimal(orderPrice, "tips");
        BigDecimal charge = getBigDecimal(orderPrice, "charge");
        BigDecimal discount = getBigDecimal(orderPrice, "discount");
        
        List<RefundItemDetail> refundItems = new ArrayList<>();
        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        
        if (request.getRefundType() == RefundType.BY_ITEMS) {
            // 按商品退款
            if (request.getRefundItems() == null || request.getRefundItems().isEmpty()) {
                throw new RuntimeException("按商品退款时，必须指定退款商品列表");
            }
            
            for (RefundItemRequest itemRequest : request.getRefundItems()) {
                OrderItem orderItem = orderItems.stream()
                    .filter(item -> item.getId().equals(itemRequest.getOrderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("订单项不存在: " + itemRequest.getOrderItemId()));
                
                // 检查退款数量
                if (itemRequest.getRefundQuantity() == null || itemRequest.getRefundQuantity() <= 0) {
                    throw new RuntimeException("退款数量必须大于0");
                }
                if (itemRequest.getRefundQuantity() > orderItem.getQuantity()) {
                    throw new RuntimeException(
                        String.format("退款数量(%d)超过订单数量(%d)", 
                            itemRequest.getRefundQuantity(), orderItem.getQuantity())
                    );
                }
                
                // 计算该商品的退款金额
                ItemRefundAmount itemRefundAmount = calculateItemRefund(
                    orderItem, 
                    itemRequest.getRefundQuantity(),
                    subtotal,
                    taxTotal,
                    discount
                );
                
                refundItems.add(RefundItemDetail.builder()
                    .orderItemId(orderItem.getId())
                    .subject(RefundSubject.ITEM.getCode())
                    .refundQty(itemRequest.getRefundQuantity())
                    .refundAmount(itemRefundAmount.getItemAmount())
                    .taxRefund(itemRefundAmount.getTaxAmount())
                    .discountRefund(itemRefundAmount.getDiscountAmount())
                    .build());
                
                totalRefundAmount = totalRefundAmount.add(itemRefundAmount.getTotalAmount());
            }
        } else {
            // 按金额退款：自动分配到商品（按比例）
            if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("按金额退款时，退款金额必须大于0");
            }
            
            BigDecimal remainingAmount = request.getRefundAmount();
            BigDecimal totalItemAmount = subtotal;
            
            if (totalItemAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("订单商品金额为0，无法按金额退款");
            }
            
            for (OrderItem orderItem : orderItems) {
                if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                
                // 按商品金额比例分配退款
                BigDecimal itemRatio = orderItem.getItemPriceTotal()
                    .divide(totalItemAmount, 4, RoundingMode.HALF_UP);
                BigDecimal itemRefundAmount = request.getRefundAmount()
                    .multiply(itemRatio)
                    .setScale(2, RoundingMode.HALF_UP);
                
                if (itemRefundAmount.compareTo(remainingAmount) > 0) {
                    itemRefundAmount = remainingAmount;
                }
                
                // 计算该商品的退款明细
                ItemRefundAmount itemRefund = calculateItemRefundByAmount(
                    orderItem,
                    itemRefundAmount,
                    subtotal,
                    taxTotal,
                    discount
                );
                
                refundItems.add(RefundItemDetail.builder()
                    .orderItemId(orderItem.getId())
                    .subject(RefundSubject.ITEM.getCode())
                    .refundQty(orderItem.getQuantity())  // 按金额退款时，默认退全部数量
                    .refundAmount(itemRefund.getItemAmount())
                    .taxRefund(itemRefund.getTaxAmount())
                    .discountRefund(itemRefund.getDiscountAmount())
                    .build());
                
                remainingAmount = remainingAmount.subtract(itemRefund.getTotalAmount());
                totalRefundAmount = totalRefundAmount.add(itemRefund.getTotalAmount());
            }
        }
        
        // 2. 处理配送费、小费、服务费退款
        if (Boolean.TRUE.equals(request.getRefundDeliveryFee()) && 
            deliveryFee.compareTo(BigDecimal.ZERO) > 0) {
            refundItems.add(RefundItemDetail.builder()
                .subject(RefundSubject.DELIVERY_FEE.getCode())
                .refundAmount(deliveryFee)
                .build());
            totalRefundAmount = totalRefundAmount.add(deliveryFee);
        }
        
        if (Boolean.TRUE.equals(request.getRefundTips()) && 
            tips.compareTo(BigDecimal.ZERO) > 0) {
            refundItems.add(RefundItemDetail.builder()
                .subject(RefundSubject.TIPS.getCode())
                .refundAmount(tips)
                .build());
            totalRefundAmount = totalRefundAmount.add(tips);
        }
        
        if (Boolean.TRUE.equals(request.getRefundCharge()) && 
            charge.compareTo(BigDecimal.ZERO) > 0) {
            refundItems.add(RefundItemDetail.builder()
                .subject(RefundSubject.CHARGE.getCode())
                .refundAmount(charge)
                .build());
            totalRefundAmount = totalRefundAmount.add(charge);
        }
        
        log.info("退款计算完成，订单ID: {}, 退款类型: {}, 退款总金额: {}, 退款明细数量: {}", 
            order.getId(), request.getRefundType(), totalRefundAmount, refundItems.size());
        
        return RefundCalculationResult.builder()
            .refundItems(refundItems)
            .totalRefundAmount(totalRefundAmount)
            .build();
    }
    
    /**
     * 计算商品退款金额（按数量）
     */
    private ItemRefundAmount calculateItemRefund(
            OrderItem orderItem,
            Integer refundQty,
            BigDecimal orderSubtotal,
            BigDecimal orderTaxTotal,
            BigDecimal orderDiscount) {
        
        // 商品退款金额 = (商品单价 * 退款数量)
        BigDecimal itemUnitPrice = orderItem.getItemPriceTotal()
            .divide(BigDecimal.valueOf(orderItem.getQuantity()), 2, RoundingMode.HALF_UP);
        BigDecimal itemRefundAmount = itemUnitPrice.multiply(BigDecimal.valueOf(refundQty));
        
        // 税费退款 = 商品退款金额 / 订单小计 * 订单税费
        BigDecimal taxRefund = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            taxRefund = itemRefundAmount
                .divide(orderSubtotal, 4, RoundingMode.HALF_UP)
                .multiply(orderTaxTotal)
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        // 折扣退款 = 商品退款金额 / 订单小计 * 订单折扣
        BigDecimal discountRefund = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0 && orderDiscount.compareTo(BigDecimal.ZERO) > 0) {
            discountRefund = itemRefundAmount
                .divide(orderSubtotal, 4, RoundingMode.HALF_UP)
                .multiply(orderDiscount)
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        // 总退款 = 商品退款 + 税费退款 - 折扣退款（折扣是负数，所以减去）
        BigDecimal totalRefund = itemRefundAmount.add(taxRefund).subtract(discountRefund);
        
        return ItemRefundAmount.builder()
            .itemAmount(itemRefundAmount)
            .taxAmount(taxRefund)
            .discountAmount(discountRefund)
            .totalAmount(totalRefund)
            .build();
    }
    
    /**
     * 计算商品退款金额（按金额）
     */
    private ItemRefundAmount calculateItemRefundByAmount(
            OrderItem orderItem,
            BigDecimal refundAmount,
            BigDecimal orderSubtotal,
            BigDecimal orderTaxTotal,
            BigDecimal orderDiscount) {
        
        // 商品退款金额 = refundAmount（已按比例分配）
        BigDecimal itemRefundAmount = refundAmount;
        
        // 税费退款 = 商品退款金额 / 订单小计 * 订单税费
        BigDecimal taxRefund = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            taxRefund = itemRefundAmount
                .divide(orderSubtotal, 4, RoundingMode.HALF_UP)
                .multiply(orderTaxTotal)
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        // 折扣退款 = 商品退款金额 / 订单小计 * 订单折扣
        BigDecimal discountRefund = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0 && orderDiscount.compareTo(BigDecimal.ZERO) > 0) {
            discountRefund = itemRefundAmount
                .divide(orderSubtotal, 4, RoundingMode.HALF_UP)
                .multiply(orderDiscount)
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        // 总退款 = 商品退款 + 税费退款 - 折扣退款
        BigDecimal totalRefund = itemRefundAmount.add(taxRefund).subtract(discountRefund);
        
        return ItemRefundAmount.builder()
            .itemAmount(itemRefundAmount)
            .taxAmount(taxRefund)
            .discountAmount(discountRefund)
            .totalAmount(totalRefund)
            .build();
    }
    
    /**
     * 解析订单价格 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOrderPrice(Order order) {
        if (order.getOrderPrice() == null) {
            throw new RuntimeException("订单价格信息为空");
        }
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr.startsWith("{")) {
                return (Map<String, Object>) objectMapper.readValue(orderPriceStr, Map.class);
            }
            throw new RuntimeException("订单价格格式错误");
        } catch (Exception e) {
            throw new RuntimeException("解析订单价格失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 Map 中获取 BigDecimal
     */
    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}

