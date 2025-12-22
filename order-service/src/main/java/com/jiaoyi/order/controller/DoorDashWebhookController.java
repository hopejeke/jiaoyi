package com.jiaoyi.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.Delivery;
import com.jiaoyi.order.entity.DoorDashWebhookLog;
import com.jiaoyi.order.enums.OrderStatusEnum;
import com.jiaoyi.order.enums.DoorDashWebhookLogStatusEnum;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.DeliveryMapper;
import com.jiaoyi.order.mapper.DoorDashWebhookLogMapper;
import com.jiaoyi.order.service.DoorDashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * DoorDash Webhook 回调控制器
 * 处理 DoorDash 的配送状态更新回调
 */
@RestController
@RequestMapping("/api/doordash/webhook")
@RequiredArgsConstructor
@Slf4j
public class DoorDashWebhookController {
    
    private final OrderMapper orderMapper;
    private final DeliveryMapper deliveryMapper;
    private final DoorDashWebhookLogMapper webhookLogMapper;
    private final DoorDashService doorDashService;
    private final ObjectMapper objectMapper;
    
    /**
     * Mock Webhook 回调（用于测试）
     * 可以手动触发不同状态的 webhook 回调来测试订单状态更新
     * 
     * @param orderId 订单ID
     * @param eventType 事件类型：delivery.created, delivery.assigned, delivery.picked_up, delivery.delivered, delivery.cancelled
     * @return 响应结果
     */
    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> mockWebhook(
            @RequestParam Long orderId,
            @RequestParam(required = false, defaultValue = "delivery.picked_up") String eventType) {
        
        log.info("========== Mock DoorDash Webhook 回调 ==========");
        log.info("订单ID: {}, 事件类型: {}", orderId, eventType);
        
        try {
            // 1. 查询订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("订单不存在，订单ID: {}", orderId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "订单不存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 如果没有 deliveryId，自动生成一个 Mock deliveryId（用于测试）
            String deliveryId = order.getDeliveryId();
            if (deliveryId == null || deliveryId.isEmpty()) {
                log.info("订单没有 deliveryId，自动生成 Mock deliveryId，订单ID: {}", orderId);
                deliveryId = "MOCK_DD_" + System.currentTimeMillis();
                
                // 更新订单的 deliveryId
                orderMapper.updateDeliveryId(orderId, deliveryId);
                log.info("已为订单生成 Mock deliveryId: {}", deliveryId);
            }
            
            // 2. 构建 Mock Webhook 数据
            Map<String, Object> mockPayload = buildMockWebhookPayload(order, deliveryId, eventType);
            
            // 3. 直接处理 webhook 数据（不通过 handleWebhook，避免 request 为 null 的问题）
            try {
                processWebhookPayload(mockPayload, order);
            } catch (Exception e) {
                log.error("处理 webhook 数据失败，订单ID: {}", orderId, e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "处理失败: " + e.getMessage());
                return ResponseEntity.status(500).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Mock webhook 处理完成");
            response.put("orderId", orderId);
            response.put("eventType", eventType);
            response.put("deliveryId", deliveryId);
            response.put("orderStatusBefore", order.getStatus());
            
            // 重新查询订单获取更新后的状态
            Order updatedOrder = orderMapper.selectById(orderId);
            response.put("orderStatusAfter", updatedOrder != null ? updatedOrder.getStatus() : null);
            
            log.info("Mock webhook 处理完成，订单ID: {}, deliveryId: {}, 状态: {} -> {}", 
                    orderId, deliveryId, order.getStatus(), 
                    updatedOrder != null ? updatedOrder.getStatus() : "未知");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Mock webhook 处理失败，订单ID: {}", orderId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "处理失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 构建 Mock Webhook 数据
     */
    private Map<String, Object> buildMockWebhookPayload(Order order, String deliveryId, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_type", eventType);
        
        Map<String, Object> data = new HashMap<>();
        data.put("id", deliveryId);
        data.put("external_delivery_id", "order_" + order.getId());
        data.put("status", getStatusFromEventType(eventType));
        
        // 添加 tracking_url
        data.put("tracking_url", "https://track.doordash.com/delivery/" + deliveryId);
        
        // 添加距离信息
        Map<String, Object> distance = new HashMap<>();
        distance.put("value", 2.5); // 模拟 2.5 英里
        distance.put("unit", "miles");
        data.put("distance", distance);
        
        // 添加 ETA
        Map<String, Object> eta = new HashMap<>();
        eta.put("estimated_minutes", 30); // 模拟 30 分钟
        data.put("estimated_dropoff_time", eta);
        
        // 如果是已分配、已取货或已送达，添加骑手信息
        if ("delivery.assigned".equals(eventType) || 
            "delivery.picked_up".equals(eventType) || 
            "delivery.delivered".equals(eventType)) {
            Map<String, Object> dasher = new HashMap<>();
            dasher.put("name", "Mock Dasher");
            dasher.put("phone", "+1-555-0123");
            data.put("dasher", dasher);
        }
        
        payload.put("data", data);
        
        return payload;
    }
    
    /**
     * 根据事件类型获取状态
     */
    private String getStatusFromEventType(String eventType) {
        switch (eventType) {
            case "delivery.created":
                return "CREATED";
            case "delivery.assigned":
                return "ASSIGNED";
            case "delivery.picked_up":
                return "PICKED_UP";
            case "delivery.delivered":
                return "DELIVERED";
            case "delivery.cancelled":
                return "CANCELLED";
            case "delivery.failed":
                return "FAILED";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * 处理 DoorDash Webhook 回调
     * DoorDash 会回调这个接口，通知配送状态变化
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-DoorDash-Signature", required = false) String signature,
            HttpServletRequest request) {
        
        log.info("========== 收到 DoorDash Webhook 回调 ==========");
        if (request != null) {
            log.info("请求URL: {}", request.getRequestURL());
        }
        log.info("请求体: {}", payload);
        log.info("签名: {}", signature != null ? "已提供" : "未提供");
        
        try {
            // 1. 验证签名（如果配置了 webhook secret）
            // if (!verifySignature(payload, signature)) {
            //     log.error("DoorDash Webhook 签名验证失败");
            //     return ResponseEntity.status(401).body("Invalid signature");
            // }
            
            // 2. 解析回调数据
            String eventType = (String) payload.get("event_type");
            String eventId = (String) payload.get("event_id"); // DoorDash 事件ID（用于幂等性）
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            
            if (data == null) {
                log.warn("DoorDash Webhook 数据为空");
                return ResponseEntity.badRequest().body("Data is empty");
            }
            
            // ========== 幂等性检查：先检查 Webhook 日志表（基于 event_id 去重） ==========
            DoorDashWebhookLog existingLog = null;
            if (eventId != null && !eventId.isEmpty()) {
                existingLog = webhookLogMapper.selectByEventId(eventId);
                if (existingLog != null) {
                    // 如果已处理成功，直接返回
                    if (DoorDashWebhookLogStatusEnum.SUCCESS.equals(existingLog.getStatus())) {
                        log.info("Webhook 事件已处理（幂等性检查），event_id: {}, 事件类型: {}, 订单ID: {}, 处理时间: {}", 
                                eventId, eventType, existingLog.getOrderId(), existingLog.getProcessedAt());
                        return ResponseEntity.ok("OK");
                    }
                    // 如果正在处理中，等待或返回（避免并发处理）
                    if (DoorDashWebhookLogStatusEnum.PROCESSING.equals(existingLog.getStatus())) {
                        log.warn("Webhook 事件正在处理中（可能并发调用），event_id: {}, 事件类型: {}, 订单ID: {}", 
                                eventId, eventType, existingLog.getOrderId());
                        // 可以选择等待或直接返回，让 DoorDash 重试
                        return ResponseEntity.ok("OK");
                    }
                    // 如果之前处理失败，可以重试
                    log.info("Webhook 事件之前处理失败，将重试，event_id: {}, 事件类型: {}, 订单ID: {}, 错误: {}", 
                            eventId, eventType, existingLog.getOrderId(), existingLog.getErrorMessage());
                }
            }
            
            // 3. 提取配送ID和外部订单ID
            String deliveryId = (String) data.get("id");
            String externalDeliveryId = (String) data.get("external_delivery_id");
            String status = (String) data.get("status");
            
            log.info("DoorDash Webhook - 事件类型: {}, event_id: {}, delivery_id: {}, external_delivery_id: {}, 状态: {}", 
                    eventType, eventId, deliveryId, externalDeliveryId, status);
            
            // 4. 从 external_delivery_id 提取订单ID（格式：order_123）
            if (externalDeliveryId == null || !externalDeliveryId.startsWith("order_")) {
                log.warn("无效的 external_delivery_id: {}", externalDeliveryId);
                return ResponseEntity.badRequest().body("Invalid external_delivery_id");
            }
            
            Long orderId = Long.parseLong(externalDeliveryId.substring(6)); // 去掉 "order_" 前缀
            
            // 5. 查询订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("订单不存在，订单ID: {}", orderId);
                return ResponseEntity.badRequest().body("Order not found");
            }
            
            // 创建或更新 Webhook 日志（标记为处理中）
            DoorDashWebhookLog webhookLog = null;
            if (existingLog != null) {
                webhookLog = existingLog;
                // 更新状态为处理中
                webhookLogMapper.updateStatus(
                        webhookLog.getId(), 
                        DoorDashWebhookLogStatusEnum.PROCESSING, 
                        null, 
                        null,
                        webhookLog.getRetryCount() != null ? webhookLog.getRetryCount() + 1 : 1
                );
            } else {
                // 创建新的 Webhook 日志
                webhookLog = new DoorDashWebhookLog();
                webhookLog.setEventId(eventId != null ? eventId : "NO_EVENT_ID_" + System.currentTimeMillis());
                webhookLog.setOrderId(orderId);
                webhookLog.setDeliveryId(deliveryId);
                webhookLog.setExternalDeliveryId(externalDeliveryId);
                // 将字符串转换为枚举
                com.jiaoyi.order.enums.DoorDashEventTypeEnum eventTypeEnum = com.jiaoyi.order.enums.DoorDashEventTypeEnum.fromCode(eventType);
                if (eventTypeEnum != null) {
                    webhookLog.setEventType(eventTypeEnum);
                } else {
                    log.warn("未知的事件类型: {}，订单ID: {}", eventType, orderId);
                }
                webhookLog.setStatus(DoorDashWebhookLogStatusEnum.PROCESSING);
                webhookLog.setRetryCount(0);
                webhookLog.setCreateTime(java.time.LocalDateTime.now());
                try {
                    // 保存完整的 payload
                    webhookLog.setPayload(objectMapper.writeValueAsString(payload));
                    webhookLogMapper.insert(webhookLog);
                } catch (Exception e) {
                    // 如果插入失败（可能是并发插入导致唯一键冲突），查询已存在的记录
                    log.warn("插入 Webhook 日志失败（可能是并发插入），event_id: {}, 错误: {}", eventId, e.getMessage());
                    if (eventId != null && !eventId.isEmpty()) {
                        existingLog = webhookLogMapper.selectByEventId(eventId);
                        if (existingLog != null && DoorDashWebhookLogStatusEnum.SUCCESS.equals(existingLog.getStatus())) {
                            log.info("并发插入时发现已处理成功的记录，event_id: {}", eventId);
                            return ResponseEntity.ok("OK");
                        }
                    }
                    // 如果插入失败且没有已存在的记录，继续处理（可能是数据库问题）
                    webhookLog = null;
                }
            }
            
            // 6. 处理 webhook 数据
            try {
                processWebhookPayload(payload, order);
                
                // 更新 Webhook 日志为成功
                if (webhookLog != null) {
                    try {
                        Map<String, Object> result = new HashMap<>();
                        result.put("orderId", orderId);
                        result.put("deliveryId", deliveryId);
                        result.put("eventType", eventType);
                        result.put("orderStatus", order.getStatus());
                        result.put("processedAt", java.time.LocalDateTime.now().toString());
                        
                        webhookLogMapper.updateStatus(
                                webhookLog.getId(), 
                                DoorDashWebhookLogStatusEnum.SUCCESS, 
                                objectMapper.writeValueAsString(result), 
                                null,
                                webhookLog.getRetryCount()
                        );
                    } catch (Exception e) {
                        log.warn("更新 Webhook 日志失败，但不影响主流程，event_id: {}", eventId, e);
                    }
                }
            } catch (Exception e) {
                log.error("处理 Webhook 数据失败，event_id: {}, 订单ID: {}", eventId, orderId, e);
                
                // 更新 Webhook 日志为失败
                if (webhookLog != null) {
                    try {
                        webhookLogMapper.updateStatus(
                                webhookLog.getId(), 
                                DoorDashWebhookLogStatusEnum.FAILED, 
                                null, 
                                e.getMessage(),
                                webhookLog.getRetryCount()
                        );
                    } catch (Exception logError) {
                        log.warn("更新 Webhook 日志失败，event_id: {}", eventId, logError);
                    }
                }
                
                // 不抛出异常，返回 200 OK，避免 DoorDash 重复回调
                // 失败的事件可以通过定时任务重试
                return ResponseEntity.ok("OK");
            }
            
            // 注意：这里可以根据需要调用 doorDashService 查询最新状态
            
            // 7. 返回成功响应
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("处理 DoorDash Webhook 失败", e);
            // 返回 200 OK，避免 DoorDash 重复回调
            // 异常已经在内部处理并记录到日志表
            return ResponseEntity.ok("OK");
        }
    }
    
    /**
     * 处理 Webhook 数据（提取公共逻辑）
     */
    private void processWebhookPayload(Map<String, Object> payload, Order order) {
        String eventType = (String) payload.get("event_type");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        
        if (data == null) {
            log.warn("Webhook 数据为空，订单ID: {}", order.getId());
            return;
        }
        
        String deliveryId = (String) data.get("id");
        
        // 处理不同的事件类型
        switch (eventType) {
            case "delivery.created":
                handleDeliveryCreated(order, deliveryId, data);
                break;
            case "delivery.assigned":
                handleDeliveryAssigned(order, deliveryId, data);
                break;
            case "delivery.picked_up":
                handleDeliveryPickedUp(order, deliveryId, data);
                break;
            case "delivery.delivered":
                handleDeliveryDelivered(order, deliveryId, data);
                break;
            case "delivery.cancelled":
                handleDeliveryCancelled(order, deliveryId, data);
                break;
            case "delivery.failed":
                handleDeliveryFailed(order, deliveryId, data);
                break;
            default:
                log.warn("未知的事件类型: {}", eventType);
        }
    }
    
    /**
     * 处理配送创建事件
     */
    private void handleDeliveryCreated(Order order, String deliveryId, Map<String, Object> data) {
        log.info("配送订单已创建，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData，保存 tracking_url 等信息
            updateAdditionalData(order, data);
        } catch (Exception e) {
            log.error("处理配送创建事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 处理配送已分配骑手事件
     */
    private void handleDeliveryAssigned(Order order, String deliveryId, Map<String, Object> data) {
        log.info("配送订单已分配骑手，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData，保存骑手信息
            updateAdditionalData(order, data);
            
            // 注意：骑手接单和商家制作是独立的过程，不改变订单状态
            // 商家可能在骑手接单前就已经开始制作了，也可能在骑手接单后还在制作
            // 所以这里只更新骑手信息，不改变订单状态
            
            // 可以在这里发送推送通知给用户："骑手已接单"
            // TODO: 集成推送服务
        } catch (Exception e) {
            log.error("处理配送已分配骑手事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 处理配送已取货事件
     */
    private void handleDeliveryPickedUp(Order order, String deliveryId, Map<String, Object> data) {
        log.info("配送订单已取货，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData
            updateAdditionalData(order, data);
            
            // 骑手已取货 = 商家已经制作完成，骑手正在配送
            // 更新订单状态为"配送中"
            orderMapper.updateStatus(order.getId(), OrderStatusEnum.DELIVERING.getCode());
            log.info("订单状态已更新为配送中（骑手已取货，正在配送），订单ID: {}", order.getId());
            
            // 可以在这里发送推送通知给用户："骑手已取货，正在配送中"
            // TODO: 集成推送服务
        } catch (Exception e) {
            log.error("处理配送已取货事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 处理配送已完成事件
     */
    private void handleDeliveryDelivered(Order order, String deliveryId, Map<String, Object> data) {
        log.info("配送订单已完成，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData
            updateAdditionalData(order, data);
            
            // 更新订单状态为"已完成"
            orderMapper.updateStatus(order.getId(), OrderStatusEnum.COMPLETED.getCode());
            log.info("订单状态已更新为已完成，订单ID: {}", order.getId());
            
            // 可以在这里发送推送通知给用户："订单已送达"
            // TODO: 集成推送服务
        } catch (Exception e) {
            log.error("处理配送已完成事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 处理配送已取消事件
     */
    private void handleDeliveryCancelled(Order order, String deliveryId, Map<String, Object> data) {
        log.info("配送订单已取消，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData
            updateAdditionalData(order, data);
            
            // 更新订单状态为"已取消"
            // 注意：如果订单已经完成，不改为取消（可能是送达后的问题）
            if (order.getStatus() != null && 
                !order.getStatus().equals(OrderStatusEnum.COMPLETED.getCode()) &&
                !order.getStatus().equals(OrderStatusEnum.CANCELLED.getCode())) {
                orderMapper.updateStatus(order.getId(), OrderStatusEnum.CANCELLED.getCode());
                log.info("订单状态已更新为已取消，订单ID: {}", order.getId());
            }
            
            // TODO: 处理取消逻辑
            // 1. 检查是否可以退款（根据订单状态和取消原因）
            // 2. 如果需要退款，调用退款服务
            // 3. 发送通知给用户
        } catch (Exception e) {
            log.error("处理配送已取消事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 处理配送失败事件
     */
    private void handleDeliveryFailed(Order order, String deliveryId, Map<String, Object> data) {
        log.warn("配送订单失败，订单ID: {}, delivery_id: {}", order.getId(), deliveryId);
        
        try {
            // 更新 additionalData
            updateAdditionalData(order, data);
            
            // TODO: 处理失败逻辑
            // 1. 检查是否可以重新分配或退款
            // 2. 发送通知给用户和商家
        } catch (Exception e) {
            log.error("处理配送失败事件失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 更新配送记录的 additionalData，保存 tracking_url、骑手信息、距离、ETA 等
     */
    private void updateAdditionalData(Order order, Map<String, Object> data) {
        try {
            // 获取配送记录
            Delivery delivery = null;
            if (order.getDeliveryId() != null && !order.getDeliveryId().isEmpty()) {
                delivery = deliveryMapper.selectById(order.getDeliveryId());
            }
            if (delivery == null) {
                delivery = deliveryMapper.selectByOrderId(order.getId());
            }
            if (delivery == null) {
                log.warn("配送记录不存在，无法更新 additionalData，订单ID: {}", order.getId());
                return;
            }
            
            Map<String, Object> additionalData = new HashMap<>();
            
            // 如果配送记录已有 additionalData，先解析它
            if (delivery.getAdditionalData() != null && !delivery.getAdditionalData().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingData = objectMapper.readValue(
                            delivery.getAdditionalData(), 
                            Map.class
                    );
                    additionalData.putAll(existingData);
                } catch (Exception e) {
                    log.warn("解析现有 additionalData 失败，订单ID: {}", order.getId(), e);
                }
            }
            
            // 更新 deliveryInfo
            Map<String, Object> deliveryInfo = (Map<String, Object>) additionalData.getOrDefault("deliveryInfo", new HashMap<>());
            deliveryInfo.put("status", data.get("status"));
            deliveryInfo.put("trackingUrl", data.get("tracking_url"));
            
            // 更新 Delivery 实体的 status 字段（将字符串状态转换为枚举）
            String statusStr = (String) data.get("status");
            if (statusStr != null && !statusStr.isEmpty()) {
                com.jiaoyi.order.enums.DeliveryStatusEnum statusEnum = com.jiaoyi.order.enums.DeliveryStatusEnum.fromCode(statusStr);
                if (statusEnum != null) {
                    delivery.setStatus(statusEnum);
                } else {
                    log.warn("未知的配送状态: {}，订单ID: {}", statusStr, order.getId());
                }
            }
            
            // 距离信息
            @SuppressWarnings("unchecked")
            Map<String, Object> distanceInfo = (Map<String, Object>) data.get("distance");
            if (distanceInfo != null) {
                deliveryInfo.put("distanceMiles", distanceInfo.get("value"));
            }
            
            // ETA
            @SuppressWarnings("unchecked")
            Map<String, Object> etaInfo = (Map<String, Object>) data.get("estimated_dropoff_time");
            if (etaInfo != null) {
                deliveryInfo.put("etaMinutes", etaInfo.get("estimated_minutes"));
            }
            
            // 骑手信息
            @SuppressWarnings("unchecked")
            Map<String, Object> dasherInfo = (Map<String, Object>) data.get("dasher");
            if (dasherInfo != null) {
                Map<String, Object> dasher = new HashMap<>();
                dasher.put("name", dasherInfo.get("name"));
                dasher.put("phone", dasherInfo.get("phone"));
                deliveryInfo.put("dasher", dasher);
            }
            
            additionalData.put("deliveryInfo", deliveryInfo);
            
            // 保存更新后的 additionalData 到配送记录
            String updatedAdditionalData = objectMapper.writeValueAsString(additionalData);
            delivery.setAdditionalData(updatedAdditionalData);
            deliveryMapper.update(delivery);
            
            log.info("配送记录 additionalData 更新成功，订单ID: {}, 配送ID: {}", order.getId(), delivery.getId());
            
        } catch (Exception e) {
            log.error("更新配送记录 additionalData 失败，订单ID: {}", order.getId(), e);
        }
    }
}

