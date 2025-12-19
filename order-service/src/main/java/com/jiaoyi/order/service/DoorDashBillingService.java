package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DoorDash 账单对账服务
 * 负责从 DoorDash 获取账单，与系统内订单对账，并更新 delivery_fee_billed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoorDashBillingService {
    
    private final RestTemplate restTemplate;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final DeliveryFeeVarianceService varianceService;
    
    @Value("${doordash.api.base-url:https://openapi.doordash.com}")
    private String doordashApiBaseUrl;
    
    @Value("${doordash.api.key:}")
    private String doordashApiKey;
    
    /**
     * 创建通用的 HTTP 头（避免被 Cloudflare 拦截）
     */
    private HttpHeaders createCommonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + doordashApiKey);
        // 添加 User-Agent 和其他必要的头，避免被 Cloudflare 拦截
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Origin", "https://openapi.doordash.com");
        headers.set("Referer", "https://openapi.doordash.com/");
        return headers;
    }
    
    /**
     * 获取 DoorDash 账单（按日期范围）
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 账单信息
     */
    public DoorDashBillResponse getBill(LocalDate startDate, LocalDate endDate) {
        log.info("获取 DoorDash 账单，开始日期: {}, 结束日期: {}", startDate, endDate);
        
        try {
            HttpHeaders headers = createCommonHeaders();
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String url = doordashApiBaseUrl + "/v2/billing/statements" +
                    "?start_date=" + startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                    "&end_date=" + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                DoorDashBillResponse billResponse = new DoorDashBillResponse();
                billResponse.setStartDate(startDate);
                billResponse.setEndDate(endDate);
                
                // 解析账单项
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
                if (items != null) {
                    List<DoorDashBillItem> billItems = new ArrayList<>();
                    for (Map<String, Object> item : items) {
                        DoorDashBillItem billItem = new DoorDashBillItem();
                        billItem.setDeliveryId((String) item.get("delivery_id"));
                        billItem.setExternalDeliveryId((String) item.get("external_delivery_id"));
                        
                        // 解析费用
                        @SuppressWarnings("unchecked")
                        Map<String, Object> feeInfo = (Map<String, Object>) item.get("fee");
                        if (feeInfo != null) {
                            Object totalObj = feeInfo.get("total");
                            if (totalObj instanceof Number) {
                                Integer totalInCents = ((Number) totalObj).intValue();
                                billItem.setDeliveryFee(new BigDecimal(totalInCents).divide(new BigDecimal("100")));
                            }
                        }
                        
                        // 解析异常费用
                        Object waitingFeeObj = item.get("waiting_fee");
                        if (waitingFeeObj instanceof Number) {
                            billItem.setWaitingFee(new BigDecimal(waitingFeeObj.toString()));
                        }
                        
                        Object extraFeeObj = item.get("extra_fee");
                        if (extraFeeObj instanceof Number) {
                            billItem.setExtraFee(new BigDecimal(extraFeeObj.toString()));
                        }
                        
                        Object cancellationFeeObj = item.get("cancellation_fee");
                        if (cancellationFeeObj instanceof Number) {
                            billItem.setCancellationFee(new BigDecimal(cancellationFeeObj.toString()));
                        }
                        
                        billItems.add(billItem);
                    }
                    billResponse.setItems(billItems);
                }
                
                // 解析总金额
                @SuppressWarnings("unchecked")
                Map<String, Object> totalInfo = (Map<String, Object>) responseBody.get("total");
                if (totalInfo != null) {
                    Object totalAmountObj = totalInfo.get("amount");
                    if (totalAmountObj instanceof Number) {
                        Integer totalInCents = ((Number) totalAmountObj).intValue();
                        billResponse.setTotalAmount(new BigDecimal(totalInCents).divide(new BigDecimal("100")));
                    }
                }
                
                log.info("DoorDash 账单获取成功，账单项数量: {}, 总金额: {}", 
                        billResponse.getItems().size(), billResponse.getTotalAmount());
                
                return billResponse;
            } else {
                log.error("DoorDash 账单 API 返回错误状态码: {}", response.getStatusCode());
                throw new RuntimeException("DoorDash 账单 API 调用失败: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("获取 DoorDash 账单失败", e);
            throw new RuntimeException("获取 DoorDash 账单失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 对账并更新订单的 delivery_fee_billed
     * 
     * @param billResponse DoorDash 账单
     */
    public void reconcileBill(DoorDashBillResponse billResponse) {
        log.info("开始对账，账单项数量: {}", billResponse.getItems().size());
        
        int matchedCount = 0;
        int unmatchedCount = 0;
        
        for (DoorDashBillItem billItem : billResponse.getItems()) {
            try {
                // 从 external_delivery_id 提取订单ID
                String externalDeliveryId = billItem.getExternalDeliveryId();
                if (externalDeliveryId == null || !externalDeliveryId.startsWith("order_")) {
                    log.warn("无效的 external_delivery_id: {}", externalDeliveryId);
                    unmatchedCount++;
                    continue;
                }
                
                Long orderId = Long.parseLong(externalDeliveryId.substring(6));
                
                // 查询订单
                Order order = orderMapper.selectById(orderId);
                if (order == null) {
                    log.warn("订单不存在，订单ID: {}", orderId);
                    unmatchedCount++;
                    continue;
                }
                
                // 更新 delivery_fee_billed
                BigDecimal billedFee = billItem.getDeliveryFee();
                if (billItem.getWaitingFee() != null) {
                    billedFee = billedFee.add(billItem.getWaitingFee());
                }
                if (billItem.getExtraFee() != null) {
                    billedFee = billedFee.add(billItem.getExtraFee());
                }
                if (billItem.getCancellationFee() != null) {
                    billedFee = billedFee.add(billItem.getCancellationFee());
                }
                
                // 计算差额并归因
                BigDecimal variance = varianceService.calculateAndAttributeVariance(
                        order,
                        billedFee,
                        billItem.getWaitingFee(),
                        billItem.getExtraFee(),
                        billItem.getCancellationFee()
                );
                
                // 更新订单
                orderMapper.updateDeliveryFeeInfo(
                        orderId,
                        order.getDeliveryFeeQuoted(),
                        order.getDeliveryFeeChargedToUser(),
                        billedFee,
                        varianceService.buildVarianceJson(order, billedFee, billItem)
                );
                
                matchedCount++;
                log.info("对账成功，订单ID: {}, billed_fee: {}, variance: {}", 
                        orderId, billedFee, variance);
                
            } catch (Exception e) {
                log.error("对账失败，billItem: {}", billItem, e);
                unmatchedCount++;
            }
        }
        
        log.info("对账完成，匹配: {}, 未匹配: {}", matchedCount, unmatchedCount);
    }
    
    /**
     * DoorDash 账单响应
     */
    public static class DoorDashBillResponse {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<DoorDashBillItem> items;
        private BigDecimal totalAmount;
        
        // Getters and Setters
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        
        public List<DoorDashBillItem> getItems() { return items; }
        public void setItems(List<DoorDashBillItem> items) { this.items = items; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }
    
    /**
     * DoorDash 账单项
     */
    public static class DoorDashBillItem {
        private String deliveryId;
        private String externalDeliveryId;
        private BigDecimal deliveryFee;
        private BigDecimal waitingFee; // 等待费用（商户出餐慢）
        private BigDecimal extraFee; // 额外费用（用户改址等）
        private BigDecimal cancellationFee; // 取消费用
        
        // Getters and Setters
        public String getDeliveryId() { return deliveryId; }
        public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
        
        public String getExternalDeliveryId() { return externalDeliveryId; }
        public void setExternalDeliveryId(String externalDeliveryId) { this.externalDeliveryId = externalDeliveryId; }
        
        public BigDecimal getDeliveryFee() { return deliveryFee; }
        public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
        
        public BigDecimal getWaitingFee() { return waitingFee; }
        public void setWaitingFee(BigDecimal waitingFee) { this.waitingFee = waitingFee; }
        
        public BigDecimal getExtraFee() { return extraFee; }
        public void setExtraFee(BigDecimal extraFee) { this.extraFee = extraFee; }
        
        public BigDecimal getCancellationFee() { return cancellationFee; }
        public void setCancellationFee(BigDecimal cancellationFee) { this.cancellationFee = cancellationFee; }
    }
}

