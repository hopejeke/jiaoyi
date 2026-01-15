package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.dto.OrderReconciliationResponse;
import com.jiaoyi.order.entity.*;
import com.jiaoyi.order.enums.RefundStatus;
import com.jiaoyi.order.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对账服务
 * 提供订单资金分解和退款分解功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {
    
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundMapper refundMapper;
    private final RefundItemMapper refundItemMapper;
    private final OrderCouponMapper orderCouponMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取订单资金分解（对账）
     * 
     * @param orderId 订单ID
     * @return 订单对账响应
     */
    public OrderReconciliationResponse getOrderReconciliation(Long orderId) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        
        // 2. 解析订单价格信息
        OrderReconciliationResponse.FinancialBreakdown breakdown = parseOrderPriceBreakdown(order);
        
        // 3. 查询退款分解
        List<OrderReconciliationResponse.RefundBreakdown> refundBreakdowns = getRefundBreakdowns(orderId);
        
        // 4. 构建响应
        OrderReconciliationResponse response = new OrderReconciliationResponse();
        response.setOrderId(orderId);
        response.setOrderTotal(breakdown.getItemsSubtotal()
            .add(breakdown.getTax())
            .add(breakdown.getDeliveryFee())
            .add(breakdown.getTips())
            .subtract(breakdown.getCouponDiscount()));
        response.setBreakdown(breakdown);
        response.setRefundBreakdowns(refundBreakdowns);
        
        return response;
    }
    
    /**
     * 解析订单价格分解
     */
    @SuppressWarnings("unchecked")
    private OrderReconciliationResponse.FinancialBreakdown parseOrderPriceBreakdown(Order order) {
        OrderReconciliationResponse.FinancialBreakdown breakdown = 
            new OrderReconciliationResponse.FinancialBreakdown();
        
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr == null || !orderPriceStr.startsWith("{")) {
                throw new BusinessException("订单价格信息格式错误");
            }
            
            Map<String, Object> orderPrice = (Map<String, Object>) objectMapper.readValue(orderPriceStr, Map.class);
            
            // 解析各项金额
            breakdown.setItemsSubtotal(parseDecimal(orderPrice.get("subtotal")));
            breakdown.setTax(parseDecimal(orderPrice.get("tax")));
            breakdown.setDeliveryFee(parseDecimal(orderPrice.get("deliveryFee")));
            breakdown.setTips(parseDecimal(orderPrice.get("tips")));
            breakdown.setPlatformCharge(parseDecimal(orderPrice.get("charge")));
            
            // 计算优惠券折扣
            BigDecimal couponDiscount = calculateCouponDiscount(order.getId());
            breakdown.setCouponDiscount(couponDiscount);
            
            // 计算商户实收金额 = 订单总额 - 平台抽成
            BigDecimal orderTotal = breakdown.getItemsSubtotal()
                .add(breakdown.getTax())
                .add(breakdown.getDeliveryFee())
                .add(breakdown.getTips())
                .subtract(couponDiscount);
            breakdown.setMerchantReceivable(orderTotal.subtract(breakdown.getPlatformCharge()));
            breakdown.setPlatformRevenue(breakdown.getPlatformCharge());
            
        } catch (Exception e) {
            log.error("解析订单价格分解失败，订单ID: {}", order.getId(), e);
            throw new BusinessException("解析订单价格失败: " + e.getMessage());
        }
        
        return breakdown;
    }
    
    /**
     * 获取退款分解列表
     */
    private List<OrderReconciliationResponse.RefundBreakdown> getRefundBreakdowns(Long orderId) {
        List<Refund> refunds = refundMapper.selectByOrderId(orderId);
        
        return refunds.stream()
            .map(this::parseRefundBreakdown)
            .collect(Collectors.toList());
    }
    
    /**
     * 解析退款分解
     */
    private OrderReconciliationResponse.RefundBreakdown parseRefundBreakdown(Refund refund) {
        OrderReconciliationResponse.RefundBreakdown refundBreakdown = 
            new OrderReconciliationResponse.RefundBreakdown();
        
        refundBreakdown.setRefundId(refund.getRefundId());
        refundBreakdown.setRequestNo(refund.getRequestNo());
        refundBreakdown.setRefundAmount(refund.getRefundAmount());
        refundBreakdown.setRefundTime(refund.getProcessedAt() != null ? refund.getProcessedAt() : refund.getUpdatedAt());
        refundBreakdown.setStatus(refund.getStatus());
        
        // 查询退款明细
        List<RefundItem> refundItems = refundItemMapper.selectByRefundId(refund.getRefundId());
        
        // 解析退款资金分解
        OrderReconciliationResponse.RefundFinancialBreakdown financialBreakdown = 
            parseRefundFinancialBreakdown(refund, refundItems);
        refundBreakdown.setBreakdown(financialBreakdown);
        
        return refundBreakdown;
    }
    
    /**
     * 解析退款资金分解
     */
    private OrderReconciliationResponse.RefundFinancialBreakdown parseRefundFinancialBreakdown(
            Refund refund, List<RefundItem> refundItems) {
        
        OrderReconciliationResponse.RefundFinancialBreakdown breakdown = 
            new OrderReconciliationResponse.RefundFinancialBreakdown();
        
        BigDecimal totalRefund = BigDecimal.ZERO;
        
        // 汇总退款明细
        for (RefundItem item : refundItems) {
            BigDecimal itemAmount = item.getRefundAmount();
            totalRefund = totalRefund.add(itemAmount);
            
            // 根据退款科目分类
            switch (item.getSubject()) {
                case "ITEM":
                    breakdown.setItemsRefund(
                        breakdown.getItemsRefund() != null 
                            ? breakdown.getItemsRefund().add(itemAmount)
                            : itemAmount);
                    break;
                case "TAX":
                    breakdown.setTaxRefund(
                        breakdown.getTaxRefund() != null 
                            ? breakdown.getTaxRefund().add(itemAmount)
                            : itemAmount);
                    break;
                case "DELIVERY_FEE":
                    breakdown.setDeliveryFeeRefund(
                        breakdown.getDeliveryFeeRefund() != null 
                            ? breakdown.getDeliveryFeeRefund().add(itemAmount)
                            : itemAmount);
                    break;
                case "TIPS":
                    breakdown.setTipsRefund(
                        breakdown.getTipsRefund() != null 
                            ? breakdown.getTipsRefund().add(itemAmount)
                            : itemAmount);
                    break;
                case "CHARGE":
                    breakdown.setCommissionReversal(
                        breakdown.getCommissionReversal() != null 
                            ? breakdown.getCommissionReversal().add(itemAmount)
                            : itemAmount);
                    break;
                case "DISCOUNT":
                    breakdown.setCouponRefund(
                        breakdown.getCouponRefund() != null 
                            ? breakdown.getCouponRefund().add(itemAmount)
                            : itemAmount);
                    break;
            }
        }
        
        // 设置默认值
        if (breakdown.getItemsRefund() == null) breakdown.setItemsRefund(BigDecimal.ZERO);
        if (breakdown.getTaxRefund() == null) breakdown.setTaxRefund(BigDecimal.ZERO);
        if (breakdown.getDeliveryFeeRefund() == null) breakdown.setDeliveryFeeRefund(BigDecimal.ZERO);
        if (breakdown.getTipsRefund() == null) breakdown.setTipsRefund(BigDecimal.ZERO);
        if (breakdown.getCommissionReversal() == null) breakdown.setCommissionReversal(BigDecimal.ZERO);
        if (breakdown.getCouponRefund() == null) breakdown.setCouponRefund(BigDecimal.ZERO);
        
        // 设置抽成回补（从退款单中获取）
        if (refund.getCommissionReversal() != null) {
            breakdown.setCommissionReversal(refund.getCommissionReversal());
        }
        
        // 计算商户应退金额 = 退款总额 - 平台抽成回补
        breakdown.setMerchantRefundable(
            refund.getRefundAmount().subtract(breakdown.getCommissionReversal()));
        breakdown.setPlatformRefundable(breakdown.getCommissionReversal());
        
        return breakdown;
    }
    
    /**
     * 计算优惠券折扣
     */
    private BigDecimal calculateCouponDiscount(Long orderId) {
        List<OrderCoupon> orderCoupons = orderCouponMapper.selectByOrderId(orderId);
        return orderCoupons.stream()
            .map(OrderCoupon::getAppliedAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 解析 Decimal 值
     */
    private BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}




