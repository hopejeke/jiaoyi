package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * DoorDash 配送服务
 * 负责与 DoorDash API 交互：报价、创建配送订单、查询配送状态、处理 Webhook 回调
 * 
 * 行业主流做法：
 * 1. 下单前/下单中：调用报价 API，获取 quoted_fee
 * 2. 用户支付：按 quoted_fee（+buffer/补贴策略）收钱
 * 3. 履约中：Webhook 驱动状态流转
 * 4. 结算：DoorDash 出账单，对账后统一付款
 */
@Service
@Slf4j
public class DoorDashService {
    
    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    @Value("${doordash.api.base-url:https://openapi.doordash.com}")
    private String doordashApiBaseUrl;
    
    @Value("${doordash.api.key:}")
    private String doordashApiKey;
    
    @Value("${doordash.api.secret:}")
    private String doordashApiSecret;
    
    @Value("${doordash.mock.enabled:true}")
    private boolean mockEnabled;
    
    @Value("${doordash.mock.delivery-fee-base:5.00}")
    private BigDecimal mockDeliveryFeeBase;
    
    @Value("${doordash.mock.delivery-fee-per-km:1.50}")
    private BigDecimal mockDeliveryFeePerKm;
    
    /**
     * 检查是否应该使用 Mock 模式
     */
    private boolean shouldUseMock() {
        return mockEnabled || doordashApiKey == null || doordashApiKey.isEmpty();
    }
    
    /**
     * 获取 DoorDash 配送报价（下单前/下单中调用）
     * DoorDash Drive API: POST /drive/v2/quotes
     * 这是 DoorDash 官方推荐的标准流程：先算价（拿到费用/预计时间）→ 再在有效期内确认使用这次价格
     * 
     * @param externalDeliveryId 外部订单ID（用于标识这次配送，如 "order_123"）
     * @param pickupAddress 商户地址
     * @param dropoffAddress 用户地址
     * @param orderValue 订单金额（用于计算配送费）
     * @return DoorDash 报价信息，包含 quoted_fee、quote_id、有效期（通常 5 分钟）
     */
    public DoorDashQuoteResponse quoteDelivery(
            String externalDeliveryId,
            Map<String, Object> pickupAddress,
            Map<String, Object> dropoffAddress,
            BigDecimal orderValue) {
        
        // 如果启用 Mock 模式或没有 API Key，使用模拟数据
        if (shouldUseMock()) {
            return quoteDeliveryMock(externalDeliveryId, pickupAddress, dropoffAddress, orderValue);
        }
        
        log.info("获取 DoorDash 配送报价，订单金额: {}", orderValue);
        
        try {
            // 构建报价请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("external_delivery_id", externalDeliveryId); // 外部订单ID
            requestBody.put("pickup_address", pickupAddress);
            requestBody.put("dropoff_address", dropoffAddress);
            
            // 订单信息（用于报价计算）
            Map<String, Object> orderInfo = new HashMap<>();
            orderInfo.put("order_value", orderValue.multiply(new BigDecimal("100")).intValue()); // 转换为分
            orderInfo.put("currency", "USD");
            requestBody.put("order_info", orderInfo);
            
            // 调用 DoorDash 报价 API
            HttpHeaders headers = createCommonHeaders();
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // DoorDash Drive API: POST /drive/v2/quotes
            // 获取报价（费用、ETA等），返回 quote_id 和有效期（通常 5 分钟）
            String url = doordashApiBaseUrl + "/drive/v2/quotes";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                DoorDashQuoteResponse quoteResponse = new DoorDashQuoteResponse();
                
                // 解析报价ID
                Object quoteIdObj = responseBody.get("id") != null ? responseBody.get("id") : responseBody.get("quote_id");
                if (quoteIdObj != null) {
                    quoteResponse.setQuoteId(quoteIdObj.toString());
                }
                
                // 解析报价费用
                @SuppressWarnings("unchecked")
                Map<String, Object> feeInfo = (Map<String, Object>) responseBody.get("fee");
                if (feeInfo != null) {
                    Object quotedFeeObj = feeInfo.get("total");
                    if (quotedFeeObj instanceof Number) {
                        Integer quotedFeeInCents = ((Number) quotedFeeObj).intValue();
                        quoteResponse.setQuotedFee(new BigDecimal(quotedFeeInCents).divide(new BigDecimal("100")));
                    }
                }
                
                // 解析预计送达时间
                Object estimatedPickupTimeObj = responseBody.get("estimated_pickup_time");
                if (estimatedPickupTimeObj != null) {
                    quoteResponse.setEstimatedPickupTime(estimatedPickupTimeObj.toString());
                }
                
                Object estimatedDropoffTimeObj = responseBody.get("estimated_dropoff_time");
                if (estimatedDropoffTimeObj != null) {
                    quoteResponse.setEstimatedDropoffTime(estimatedDropoffTimeObj.toString());
                }
                
                // 设置报价有效期（通常为报价时间 + 10 分钟）
                // 如果 API 返回了 expires_at，使用 API 的值；否则使用默认值（10 分钟）
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                Object expiresAtObj = responseBody.get("expires_at");
                if (expiresAtObj != null) {
                    // 解析 API 返回的过期时间（ISO 8601 格式）
                    try {
                        quoteResponse.setExpiresAt(java.time.LocalDateTime.parse(expiresAtObj.toString()));
                    } catch (Exception e) {
                        log.warn("无法解析报价过期时间，使用默认值（10分钟），quote_id: {}", quoteResponse.getQuoteId());
                        quoteResponse.setExpiresAt(now.plusMinutes(10));
                    }
                } else {
                    // 默认有效期：10 分钟
                    quoteResponse.setExpiresAt(now.plusMinutes(10));
                }
                
                log.info("DoorDash 配送报价获取成功，quote_id: {}, quoted_fee: {}, 有效期至: {}, 预计取货时间: {}, 预计送达时间: {}", 
                        quoteResponse.getQuoteId(),
                        quoteResponse.getQuotedFee(), 
                        quoteResponse.getExpiresAt(),
                        quoteResponse.getEstimatedPickupTime(),
                        quoteResponse.getEstimatedDropoffTime());
                
                return quoteResponse;
            } else {
                log.error("DoorDash 报价 API 返回错误状态码: {}, 响应: {}", 
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("DoorDash 报价 API 调用失败: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("获取 DoorDash 配送报价失败", e);
            throw new RuntimeException("获取 DoorDash 配送报价失败: " + e.getMessage(), e);
        }
    }
    
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
     * 接受 DoorDash 报价（支付成功后，创建 delivery 前调用）
     * DoorDash Drive API: POST /drive/v2/quotes/{external_delivery_id}/accept
     * 根据 DoorDash 文档，quote 需要在 5 分钟内 accept
     * 
     * @param externalDeliveryId 外部订单ID（用于标识这次配送，如 "order_123"）
     * @param quoteId 报价 ID（从 quoteDelivery 返回，可选，如果 API 需要）
     * @param tip 小费（可选，accept 时可以修改）
     * @param dropoffPhoneNumber 收货人电话（可选，accept 时可以修改）
     * @return 接受报价后的响应，包含 delivery_id、tracking_url 等
     */
    public DoorDashDeliveryResponse acceptQuote(String externalDeliveryId, String quoteId, BigDecimal tip, String dropoffPhoneNumber) {
        // 如果启用 Mock 模式或没有 API Key，使用模拟数据
        if (shouldUseMock()) {
            return acceptQuoteMock(quoteId != null ? quoteId : externalDeliveryId);
        }
        
        log.info("接受 DoorDash 报价，external_delivery_id: {}, quote_id: {}", externalDeliveryId, quoteId);
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            
            // Accept 时可以修改 tip 和 dropoff_phone_number
            if (tip != null && tip.compareTo(BigDecimal.ZERO) > 0) {
                requestBody.put("tip", tip.multiply(new BigDecimal("100")).intValue()); // 转换为分
            }
            if (dropoffPhoneNumber != null && !dropoffPhoneNumber.isEmpty()) {
                requestBody.put("dropoff_phone_number", dropoffPhoneNumber);
            }
            
            // 如果 API 需要 quote_id，也传递
            if (quoteId != null && !quoteId.isEmpty()) {
                requestBody.put("quote_id", quoteId);
            }
            
            HttpHeaders headers = createCommonHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // DoorDash Drive API: POST /drive/v2/quotes/{external_delivery_id}/accept
            String url = doordashApiBaseUrl + "/drive/v2/quotes/" + externalDeliveryId + "/accept";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody == null) {
                    throw new RuntimeException("DoorDash API 返回空响应");
                }
                
                DoorDashDeliveryResponse deliveryResponse = parseDeliveryResponse(responseBody);
                
                log.info("DoorDash 报价接受成功，delivery_id: {}, tracking_url: {}", 
                        deliveryResponse.getDeliveryId(), deliveryResponse.getTrackingUrl());
                
                return deliveryResponse;
            } else {
                log.error("DoorDash 接受报价 API 返回错误状态码: {}, 响应: {}", 
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("DoorDash 接受报价 API 调用失败: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("接受 DoorDash 报价失败，quote_id: {}", quoteId, e);
            throw new RuntimeException("接受 DoorDash 报价失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建 DoorDash 配送订单（支付成功后调用）
     * 注意：如果使用了 quote/accept 流程，应该调用 acceptQuote 而不是这个方法
     * 
     * @param order 订单信息
     * @param pickupAddress 商户地址
     * @param dropoffAddress 用户地址
     * @param tip 小费（可选）
     * @param delivery 配送记录（可选，用于获取quoteId）
     * @return DoorDash 配送信息，包含 delivery_id、tracking_url、ETA 等
     */
    public DoorDashDeliveryResponse createDelivery(
            Order order,
            Map<String, Object> pickupAddress,
            Map<String, Object> dropoffAddress,
            BigDecimal tip,
            com.jiaoyi.order.entity.Delivery delivery) {
        
        // 如果启用 Mock 模式或没有 API Key，使用模拟数据
        if (shouldUseMock()) {
            return createDeliveryMock(order, pickupAddress, dropoffAddress, tip, delivery);
        }
        
        log.info("创建 DoorDash 配送订单，订单ID: {}, 商户ID: {}", order.getId(), order.getMerchantId());
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("external_delivery_id", "order_" + order.getId()); // 外部订单ID
            requestBody.put("pickup_address", pickupAddress);
            
            // 确保 dropoff_address 包含必填字段 phone_number
            Map<String, Object> dropoffAddrWithPhone = new HashMap<>(dropoffAddress);
            if (!dropoffAddrWithPhone.containsKey("phone_number")) {
                log.warn("dropoff_address 缺少 phone_number，订单ID: {}", order.getId());
                // 尝试从 customerInfo 中获取
                // 如果还是没有，会由 DoorDash API 返回错误
            }
            requestBody.put("dropoff_address", dropoffAddrWithPhone);
            
            // 订单项信息
            Map<String, Object> orderInfo = new HashMap<>();
            BigDecimal totalPrice = parseTotalPriceFromOrder(order);
            orderInfo.put("order_value", totalPrice.multiply(new BigDecimal("100")).intValue()); // 订单总金额（分）
            orderInfo.put("currency", "USD");
            requestBody.put("order_info", orderInfo);
            
            // 小费（可选）
            if (tip != null && tip.compareTo(BigDecimal.ZERO) > 0) {
                requestBody.put("tip", tip.multiply(new BigDecimal("100")).intValue()); // 转换为分
            }
            
            // 特殊说明
            if (order.getNotes() != null && !order.getNotes().isEmpty()) {
                requestBody.put("special_instructions", order.getNotes());
            }
            
            // 如果配送记录有 quote_id，在创建配送时传递 quote_id 来锁定价格
            // 注意：这需要 DoorDash API 支持在 createDelivery 时传递 quote_id
            // 如果不支持，应该使用 acceptQuote 方法
            String quoteId = null;
            if (delivery != null) {
                quoteId = delivery.getDeliveryFeeQuoteId();
            }
            if (quoteId != null && !quoteId.isEmpty()) {
                requestBody.put("quote_id", quoteId);
                log.info("创建配送订单时传递 quote_id 以锁定价格，订单ID: {}, quote_id: {}", order.getId(), quoteId);
            }
            
            // 调用 DoorDash API
            HttpHeaders headers = createCommonHeaders();
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String url = doordashApiBaseUrl + "/v2/deliveries";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody == null) {
                    throw new RuntimeException("DoorDash API 返回空响应");
                }
                
                DoorDashDeliveryResponse deliveryResponse = parseDeliveryResponse(responseBody);
                
                log.info("DoorDash 配送订单创建成功，订单ID: {}, delivery_id: {}, tracking_url: {}", 
                        order.getId(), deliveryResponse.getDeliveryId(), deliveryResponse.getTrackingUrl());
                
                return deliveryResponse;
            } else {
                log.error("DoorDash API 返回错误状态码: {}, 响应: {}", 
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("DoorDash API 调用失败: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("创建 DoorDash 配送订单失败，订单ID: {}", order.getId(), e);
            throw new RuntimeException("创建 DoorDash 配送订单失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析 DoorDash 配送响应（从 API 返回的 Map 中提取信息）
     */
    private DoorDashDeliveryResponse parseDeliveryResponse(Map<String, Object> responseBody) {
        DoorDashDeliveryResponse deliveryResponse = new DoorDashDeliveryResponse();
        
        // 基本信息
        deliveryResponse.setDeliveryId((String) responseBody.get("id"));
        deliveryResponse.setStatus((String) responseBody.get("status"));
        
        // Tracking URL（用于地图视图）
        deliveryResponse.setTrackingUrl((String) responseBody.get("tracking_url"));
        
        // 距离信息
        @SuppressWarnings("unchecked")
        Map<String, Object> distanceInfo = (Map<String, Object>) responseBody.get("distance");
        if (distanceInfo != null) {
            Object distanceValue = distanceInfo.get("value");
            if (distanceValue instanceof Number) {
                deliveryResponse.setDistanceMiles(new BigDecimal(distanceValue.toString()));
            }
        }
        
        // ETA（预计送达时间）
        @SuppressWarnings("unchecked")
        Map<String, Object> etaInfo = (Map<String, Object>) responseBody.get("estimated_dropoff_time");
        if (etaInfo != null) {
            Object etaValue = etaInfo.get("estimated_minutes");
            if (etaValue instanceof Number) {
                deliveryResponse.setEtaMinutes(((Number) etaValue).intValue());
            }
        }
        
        // 如果没有从 estimated_dropoff_time 获取，尝试从其他字段获取
        if (deliveryResponse.getEtaMinutes() == null) {
            Object etaObj = responseBody.get("eta_minutes");
            if (etaObj instanceof Number) {
                deliveryResponse.setEtaMinutes(((Number) etaObj).intValue());
            }
        }
        
        return deliveryResponse;
    }
    
    /**
     * 查询配送状态
     */
    public DoorDashDeliveryStatus getDeliveryStatus(String deliveryId) {
        // 如果启用 Mock 模式或没有 API Key，使用模拟数据
        if (shouldUseMock()) {
            return getDeliveryStatusMock(deliveryId);
        }
        
        try {
            HttpHeaders headers = createCommonHeaders();
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String url = doordashApiBaseUrl + "/v2/deliveries/" + deliveryId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                DoorDashDeliveryStatus status = new DoorDashDeliveryStatus();
                status.setDeliveryId(deliveryId);
                status.setStatus((String) responseBody.get("status"));
                
                // 解析详细信息
                status.setTrackingUrl((String) responseBody.get("tracking_url"));
                
                // 距离信息
                @SuppressWarnings("unchecked")
                Map<String, Object> distanceInfo = (Map<String, Object>) responseBody.get("distance");
                if (distanceInfo != null) {
                    Object distanceValue = distanceInfo.get("value");
                    if (distanceValue instanceof Number) {
                        status.setDistanceMiles(new BigDecimal(distanceValue.toString()));
                    }
                }
                
                // ETA
                @SuppressWarnings("unchecked")
                Map<String, Object> etaInfo = (Map<String, Object>) responseBody.get("estimated_dropoff_time");
                if (etaInfo != null) {
                    Object etaValue = etaInfo.get("estimated_minutes");
                    if (etaValue instanceof Number) {
                        status.setEtaMinutes(((Number) etaValue).intValue());
                    }
                }
                
                // 骑手信息
                @SuppressWarnings("unchecked")
                Map<String, Object> dasherInfo = (Map<String, Object>) responseBody.get("dasher");
                if (dasherInfo != null) {
                    status.setDasherName((String) dasherInfo.get("name"));
                    status.setDasherPhone((String) dasherInfo.get("phone"));
                }
                
                return status;
            }
            
            return null;
        } catch (Exception e) {
            log.error("查询 DoorDash 配送状态失败，delivery_id: {}", deliveryId, e);
            return null;
        }
    }
    
    /**
     * 取消配送订单
     */
    public boolean cancelDelivery(String deliveryId, String reason) {
        // 如果启用 Mock 模式或没有 API Key，使用模拟数据
        if (shouldUseMock()) {
            return cancelDeliveryMock(deliveryId, reason);
        }
        
        try {
            HttpHeaders headers = createCommonHeaders();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cancellation_reason", reason);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String url = doordashApiBaseUrl + "/v2/deliveries/" + deliveryId + "/cancel";
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Void.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("取消 DoorDash 配送订单失败，delivery_id: {}", deliveryId, e);
            return false;
        }
    }
    
    // ==================== Mock 模式实现 ====================
    
    /**
     * Mock 模式：模拟 DoorDash 报价 API
     */
    private DoorDashQuoteResponse quoteDeliveryMock(
            String externalDeliveryId,
            Map<String, Object> pickupAddress,
            Map<String, Object> dropoffAddress,
            BigDecimal orderValue) {
        
        log.info("【MOCK】模拟 DoorDash 报价 API 调用");
        log.info("【MOCK】商户地址: {}", pickupAddress);
        log.info("【MOCK】用户地址: {}", dropoffAddress);
        log.info("【MOCK】订单金额: ${}", orderValue);
        
        // 模拟计算配送费（基于订单金额）
        BigDecimal quotedFee;
        if (orderValue.compareTo(new BigDecimal("50")) >= 0) {
            // 大订单，配送费较低
            quotedFee = mockDeliveryFeeBase;
        } else if (orderValue.compareTo(new BigDecimal("20")) >= 0) {
            // 中等订单
            quotedFee = mockDeliveryFeeBase.add(new BigDecimal("2.00"));
        } else {
            // 小订单，配送费较高
            quotedFee = mockDeliveryFeeBase.add(new BigDecimal("4.00"));
        }
        
        // 添加一些随机波动（模拟真实情况）
        double randomFactor = 0.9 + Math.random() * 0.2; // 0.9 - 1.1
        quotedFee = quotedFee.multiply(BigDecimal.valueOf(randomFactor));
        quotedFee = quotedFee.setScale(2, RoundingMode.HALF_UP);
        
        DoorDashQuoteResponse response = new DoorDashQuoteResponse();
        
        // 生成模拟的 quote_id
        String mockQuoteId = "MOCK_QUOTE_" + System.currentTimeMillis();
        response.setQuoteId(mockQuoteId);
        response.setQuotedFee(quotedFee);
        response.setCurrency("USD");
        response.setEstimatedDeliveryTime(30); // 模拟预计 30 分钟送达
        
        // 设置报价有效期（10 分钟）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        response.setExpiresAt(now.plusMinutes(10));
        
        log.info("【MOCK】DoorDash 报价: quote_id={}, quoted_fee=${}, 有效期至: {}", 
                mockQuoteId, quotedFee, response.getExpiresAt());
        
        return response;
    }
    
    /**
     * Mock 模式：模拟创建 DoorDash 配送订单
     */
    private DoorDashDeliveryResponse createDeliveryMock(
            Order order,
            Map<String, Object> pickupAddress,
            Map<String, Object> dropoffAddress,
            BigDecimal tip,
            com.jiaoyi.order.entity.Delivery delivery) {
        
        log.info("【MOCK】模拟创建 DoorDash 配送订单，订单ID: {}", order.getId());
        log.info("【MOCK】商户地址: {}", pickupAddress);
        log.info("【MOCK】用户地址: {}", dropoffAddress);
        if (tip != null) {
            log.info("【MOCK】小费: ${}", tip);
        }
        
        // 生成模拟的 delivery_id
        String mockDeliveryId = "MOCK_DD_" + System.currentTimeMillis();
        
        DoorDashDeliveryResponse response = new DoorDashDeliveryResponse();
        response.setDeliveryId(mockDeliveryId);
        response.setStatus("CREATED"); // 初始状态为 CREATED
        response.setTrackingUrl("https://track.doordash.com/delivery/" + mockDeliveryId); // 模拟跟踪 URL
        response.setDistanceMiles(new BigDecimal("2.5")); // 模拟距离 2.5 英里
        response.setEtaMinutes(30); // 模拟 ETA 30 分钟
        
        log.info("【MOCK】DoorDash 配送订单创建成功，delivery_id: {}, tracking_url: {}, distance: {} miles, ETA: {} minutes", 
                mockDeliveryId, response.getTrackingUrl(), response.getDistanceMiles(), response.getEtaMinutes());
        
        return response;
    }
    
    /**
     * Mock 模式：模拟查询配送状态
     */
    private DoorDashDeliveryStatus getDeliveryStatusMock(String deliveryId) {
        log.info("【MOCK】模拟查询 DoorDash 配送状态，delivery_id: {}", deliveryId);
        
        // 模拟返回状态（随机返回一个状态）
        String[] statuses = {"CREATED", "ASSIGNED", "PICKED_UP", "DELIVERED"};
        String mockStatus = statuses[(int) (Math.random() * statuses.length)];
        
        DoorDashDeliveryStatus status = new DoorDashDeliveryStatus();
        status.setDeliveryId(deliveryId);
        status.setStatus(mockStatus);
        status.setTrackingUrl("https://track.doordash.com/delivery/" + deliveryId);
        status.setDistanceMiles(new BigDecimal("2.5"));
        status.setEtaMinutes(30);
        
        // 如果已分配骑手，添加骑手信息
        if ("ASSIGNED".equals(mockStatus) || "PICKED_UP".equals(mockStatus) || "DELIVERED".equals(mockStatus)) {
            status.setDasherName("Mock Dasher");
            status.setDasherPhone("+1-555-0123");
        }
        
        log.info("【MOCK】DoorDash 配送状态: {}, ETA: {} 分钟, 距离: {} 英里", 
                mockStatus, status.getEtaMinutes(), status.getDistanceMiles());
        
        return status;
    }
    
    /**
     * Mock 模式：模拟取消配送订单
     */
    private boolean cancelDeliveryMock(String deliveryId, String reason) {
        log.info("【MOCK】模拟取消 DoorDash 配送订单，delivery_id: {}, 原因: {}", deliveryId, reason);
        log.info("【MOCK】DoorDash 配送订单取消成功");
        return true;
    }
    
    /**
     * Mock 模式：模拟接受报价
     */
    private DoorDashDeliveryResponse acceptQuoteMock(String quoteId) {
        log.info("【MOCK】模拟接受 DoorDash 报价，quote_id: {}", quoteId);
        
        String mockDeliveryId = "MOCK_DD_" + System.currentTimeMillis();
        
        DoorDashDeliveryResponse response = new DoorDashDeliveryResponse();
        response.setDeliveryId(mockDeliveryId);
        response.setStatus("CREATED");
        response.setTrackingUrl("https://track.doordash.com/delivery/" + mockDeliveryId);
        response.setDistanceMiles(new BigDecimal("2.5"));
        response.setEtaMinutes(30);
        
        log.info("【MOCK】DoorDash 报价接受成功，delivery_id: {}, tracking_url: {}", 
                mockDeliveryId, response.getTrackingUrl());
        
        return response;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 从订单的 orderPrice JSON 中解析总金额
     */
    private BigDecimal parseTotalPriceFromOrder(Order order) {
        try {
            if (order.getOrderPrice() != null && !order.getOrderPrice().isEmpty() && objectMapper != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderPriceMap = objectMapper.readValue(
                        order.getOrderPrice(), 
                        Map.class
                );
                Object totalObj = orderPriceMap.get("total");
                if (totalObj instanceof Number) {
                    return new BigDecimal(totalObj.toString());
                }
            }
        } catch (Exception e) {
            log.warn("解析订单总金额失败，订单ID: {}", order.getId(), e);
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * DoorDash 报价响应
     */
    public static class DoorDashQuoteResponse {
        private String quoteId; // 报价ID（用于接受报价）
        private BigDecimal quotedFee; // 报价费用
        private String estimatedPickupTime; // 预计取货时间
        private String estimatedDropoffTime; // 预计送达时间
        private String currency; // 货币
        private Integer estimatedDeliveryTime; // 预计送达时间（分钟）
        private java.time.LocalDateTime expiresAt; // 报价有效期（通常为报价时间 + 10 分钟）
        
        // Getters and Setters
        public String getQuoteId() { return quoteId; }
        public void setQuoteId(String quoteId) { this.quoteId = quoteId; }
        
        public BigDecimal getQuotedFee() { return quotedFee; }
        public void setQuotedFee(BigDecimal quotedFee) { this.quotedFee = quotedFee; }
        
        public String getEstimatedPickupTime() { return estimatedPickupTime; }
        public void setEstimatedPickupTime(String estimatedPickupTime) { this.estimatedPickupTime = estimatedPickupTime; }
        
        public String getEstimatedDropoffTime() { return estimatedDropoffTime; }
        public void setEstimatedDropoffTime(String estimatedDropoffTime) { this.estimatedDropoffTime = estimatedDropoffTime; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public Integer getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
        public void setEstimatedDeliveryTime(Integer estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
        
        public java.time.LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(java.time.LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
    
    /**
     * DoorDash 配送响应
     */
    public static class DoorDashDeliveryResponse {
        private String deliveryId;
        private String status;
        private String trackingUrl; // 跟踪 URL
        private BigDecimal distanceMiles; // 距离（英里）
        private Integer etaMinutes; // 预计送达时间（分钟）
        
        // Getters and Setters
        public String getDeliveryId() { return deliveryId; }
        public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getTrackingUrl() { return trackingUrl; }
        public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
        
        public BigDecimal getDistanceMiles() { return distanceMiles; }
        public void setDistanceMiles(BigDecimal distanceMiles) { this.distanceMiles = distanceMiles; }
        
        public Integer getEtaMinutes() { return etaMinutes; }
        public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }
    }
    
    /**
     * DoorDash 配送状态
     */
    public static class DoorDashDeliveryStatus {
        private String deliveryId;
        private String status; // CREATED, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED
        private String trackingUrl; // 跟踪 URL（用于地图视图）
        private BigDecimal distanceMiles; // 距离（英里）
        private Integer etaMinutes; // 预计送达时间（分钟）
        private String dasherName; // 骑手姓名
        private String dasherPhone; // 骑手电话
        
        // Getters and Setters
        public String getDeliveryId() { return deliveryId; }
        public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getTrackingUrl() { return trackingUrl; }
        public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
        
        public BigDecimal getDistanceMiles() { return distanceMiles; }
        public void setDistanceMiles(BigDecimal distanceMiles) { this.distanceMiles = distanceMiles; }
        
        public Integer getEtaMinutes() { return etaMinutes; }
        public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }
        
        public String getDasherName() { return dasherName; }
        public void setDasherName(String dasherName) { this.dasherName = dasherName; }
        
        public String getDasherPhone() { return dasherPhone; }
        public void setDasherPhone(String dasherPhone) { this.dasherPhone = dasherPhone; }
    }
}
