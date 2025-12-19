package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.service.DoorDashBillingService.DoorDashBillItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 配送费差额归因服务
 * 负责计算配送费差额，并按规则归因（商户/用户/平台）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryFeeVarianceService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 计算并归因配送费差额
     * 
     * @param order 订单
     * @param billedFee DoorDash 账单费用
     * @param waitingFee 等待费用（商户出餐慢）
     * @param extraFee 额外费用（用户改址等）
     * @param cancellationFee 取消费用
     * @return 总差额
     */
    public BigDecimal calculateAndAttributeVariance(
            Order order,
            BigDecimal billedFee,
            BigDecimal waitingFee,
            BigDecimal extraFee,
            BigDecimal cancellationFee) {
        
        BigDecimal quotedFee = order.getDeliveryFeeQuoted();
        if (quotedFee == null) {
            quotedFee = BigDecimal.ZERO;
        }
        
        // 计算总差额
        BigDecimal totalVariance = billedFee.subtract(quotedFee);
        
        log.info("计算配送费差额，订单ID: {}, quoted_fee: {}, billed_fee: {}, variance: {}", 
                order.getId(), quotedFee, billedFee, totalVariance);
        
        return totalVariance;
    }
    
    /**
     * 构建差额归因 JSON
     * 
     * @param order 订单
     * @param billedFee DoorDash 账单费用
     * @param billItem 账单项
     * @return 差额归因 JSON 字符串
     */
    public String buildVarianceJson(Order order, BigDecimal billedFee, DoorDashBillItem billItem) {
        try {
            Map<String, Object> variance = new HashMap<>();
            
            BigDecimal quotedFee = order.getDeliveryFeeQuoted();
            if (quotedFee == null) {
                quotedFee = BigDecimal.ZERO;
            }
            
            // 等待费用 → 商户承担
            if (billItem.getWaitingFee() != null && billItem.getWaitingFee().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> waitingFeeInfo = new HashMap<>();
                waitingFeeInfo.put("amount", billItem.getWaitingFee());
                waitingFeeInfo.put("attribution", "MERCHANT");
                variance.put("waitingFee", waitingFeeInfo);
            }
            
            // 额外费用 → 用户承担（或平台兜底）
            if (billItem.getExtraFee() != null && billItem.getExtraFee().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> extraFeeInfo = new HashMap<>();
                extraFeeInfo.put("amount", billItem.getExtraFee());
                // TODO: 根据业务规则决定是用户承担还是平台兜底
                extraFeeInfo.put("attribution", "USER"); // 或 "PLATFORM"
                variance.put("extraFee", extraFeeInfo);
            }
            
            // 取消费用 → 根据取消原因归因
            if (billItem.getCancellationFee() != null && billItem.getCancellationFee().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> cancellationFeeInfo = new HashMap<>();
                cancellationFeeInfo.put("amount", billItem.getCancellationFee());
                // TODO: 根据取消原因决定归因（商户/用户/平台）
                cancellationFeeInfo.put("attribution", "PLATFORM");
                variance.put("cancellationFee", cancellationFeeInfo);
            }
            
            // 基础费用差额（如果 billedFee > quotedFee，且没有异常费用）
            BigDecimal baseVariance = billedFee.subtract(quotedFee);
            if (billItem.getWaitingFee() != null) {
                baseVariance = baseVariance.subtract(billItem.getWaitingFee());
            }
            if (billItem.getExtraFee() != null) {
                baseVariance = baseVariance.subtract(billItem.getExtraFee());
            }
            if (billItem.getCancellationFee() != null) {
                baseVariance = baseVariance.subtract(billItem.getCancellationFee());
            }
            
            if (baseVariance.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> baseVarianceInfo = new HashMap<>();
                baseVarianceInfo.put("amount", baseVariance);
                // 基础费用差额通常由平台承担（营销成本）
                baseVarianceInfo.put("attribution", "PLATFORM");
                variance.put("baseVariance", baseVarianceInfo);
            }
            
            return objectMapper.writeValueAsString(variance);
            
        } catch (Exception e) {
            log.error("构建差额归因 JSON 失败，订单ID: {}", order.getId(), e);
            return "{}";
        }
    }
}


