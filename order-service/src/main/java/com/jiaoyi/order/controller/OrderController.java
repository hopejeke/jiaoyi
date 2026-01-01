package com.jiaoyi.order.controller;

import com.jiaoyi.common.annotation.PreventDuplicateSubmission;
import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.dto.*;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.Delivery;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.service.OrderService;
import com.jiaoyi.order.service.PaymentService;
import com.jiaoyi.order.service.DoorDashService;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.mapper.DeliveryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderController {
    
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productServiceClient;
    private final DoorDashService doorDashService;
    private final DeliveryMapper deliveryMapper;
    
    /**
     * 计算订单价格（预览价格，不创建订单）
     * 前端在提交订单前调用此接口获取价格预览
     */
    @PostMapping("/calculate-price")
    public ResponseEntity<ApiResponse<CalculatePriceResponse>> calculatePrice(@RequestBody CalculatePriceRequest request) {
        log.info("计算订单价格，商户ID: {}, 订单类型: {}", request.getMerchantId(), request.getOrderType());
        
        try {
            CalculatePriceResponse response = orderService.calculateOrderPrice(request);
            return ResponseEntity.ok(ApiResponse.success("价格计算成功", response));
        } catch (Exception e) {
            log.error("计算订单价格失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "价格计算失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建订单（在线点餐，保留库存锁定、优惠券等功能）
     * 支持在创建订单时一起处理支付（在同一事务中）
     * 
     * 防重复提交：基于订单内容（merchantId + userId + orderItems哈希）生成锁 key
     * 相同订单内容的重复提交会被拦截，不同订单内容可以并发创建
     */
    @PostMapping
    @PreventDuplicateSubmission(
        key = "T(com.jiaoyi.order.controller.OrderController).generateOrderLockKey(#request)", 
        expireTime = 5,
        message = "请勿重复提交相同订单"
    )
    @com.jiaoyi.order.annotation.RateLimit
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("创建在线点餐订单请求，userId: {}, paymentMethod: {}, payOnline: {}", 
                request.getUserId(), request.getPaymentMethod(), request.getPayOnline());
        
        try {
            if (request.getOrderItems() == null || request.getOrderItems().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(400, "订单项不能为空"));
            }
            
            // 验证订单项数据完整性
            for (CreateOrderRequest.OrderItemRequest item : request.getOrderItems()) {
                if (item.getProductId() == null) {
                    return ResponseEntity.ok(ApiResponse.error(400, "商品ID不能为空"));
                }
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    return ResponseEntity.ok(ApiResponse.error(400, "商品数量必须大于0"));
                }
            }
            
            // 1. 构建订单对象（从 CreateOrderRequest 转换为 Order）
            Order order = buildOrderFromRequest(request);
            
            // 2. 构建订单项列表
            List<OrderItem> orderItems = buildOrderItemsFromRequest(request);
            
            if (orderItems.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(400, "没有有效的订单项"));
            }
            
            // 3. 构建支付请求（如果需要支付）
            PaymentRequest paymentRequest = null;
            if (request.getPayOnline() != null && request.getPayOnline() && 
                request.getPaymentMethod() != null && !request.getPaymentMethod().isEmpty()) {
                paymentRequest = new PaymentRequest();
                paymentRequest.setPaymentMethod(request.getPaymentMethod().toUpperCase());
                // 金额会在 Service 层从订单中获取
            }
            
            // 4. 创建订单并处理支付（在同一事务中）
            CreateOrderResponse response = orderService.createOrderWithPayment(
                    order, 
                    orderItems,
                    request.getCouponIds(),
                    request.getCouponCodes(),
                    paymentRequest
            );
            
            return ResponseEntity.ok(ApiResponse.success("创建成功", response));
        } catch (Exception e) {
            log.error("创建在线点餐订单失败", e);
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.getClass().getSimpleName() + ": " + (e.getCause() != null ? e.getCause().getMessage() : "未知错误");
            }
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + errorMessage));
        }
    }
    
    /**
     * 从 CreateOrderRequest 构建 Order 对象
     */
    private Order buildOrderFromRequest(CreateOrderRequest request) {
        Order order = new Order();
        order.setMerchantId(request.getMerchantId());
        order.setUserId(request.getUserId());
        // 将字符串转换为枚举
        com.jiaoyi.order.enums.OrderTypeEnum orderTypeEnum = com.jiaoyi.order.enums.OrderTypeEnum.fromCode(request.getOrderType());
        if (orderTypeEnum == null) {
            throw new com.jiaoyi.common.exception.BusinessException("无效的订单类型: " + request.getOrderType());
        }
        order.setOrderType(orderTypeEnum);
        order.setStatus(1); // 已下单
        order.setLocalStatus(1);
        order.setKitchenStatus(1);
        
        // 设置客户信息（JSON格式）
        try {
            java.util.Map<String, Object> customerInfo = new java.util.HashMap<>();
            customerInfo.put("name", request.getReceiverName());
            customerInfo.put("phone", request.getReceiverPhone());
            customerInfo.put("address", request.getReceiverAddress());
            order.setCustomerInfo(objectMapper.writeValueAsString(customerInfo));
        } catch (Exception e) {
            log.warn("构建客户信息失败", e);
        }
        
        // 设置备注
        order.setNotes(request.getRemark());
        
        // 设置支付方式
        if (request.getPaymentMethod() != null) {
            order.setPaymentMethod(request.getPaymentMethod());
        }
        
        // 注意：订单价格计算已移到 OrderService.createOrder 中
        // 因为需要先构建 OrderItem（从数据库查询商品价格），然后才能计算订单价格
        // 这里先设置一个临时值，OrderService 会重新计算
        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
        
        // 构建订单价格JSON
        try {
            java.util.Map<String, Object> orderPrice = new java.util.HashMap<>();
            orderPrice.put("subtotal", subtotal);
            orderPrice.put("discount", java.math.BigDecimal.ZERO);
            orderPrice.put("charge", java.math.BigDecimal.ZERO);
            orderPrice.put("deliveryFee", java.math.BigDecimal.ZERO);
            orderPrice.put("taxTotal", java.math.BigDecimal.ZERO);
            orderPrice.put("tips", java.math.BigDecimal.ZERO);
            orderPrice.put("total", subtotal); // 暂时总价等于小计，实际需要计算税费、配送费等
            order.setOrderPrice(objectMapper.writeValueAsString(orderPrice));
        } catch (Exception e) {
            log.warn("构建订单价格失败", e);
        }
        
        return order;
    }
    
    /**
     * 从 CreateOrderRequest 构建 OrderItem 列表
     * 注意：为了安全，商品价格从数据库查询，不使用前端传来的价格
     */
    private List<OrderItem> buildOrderItemsFromRequest(CreateOrderRequest request) {
        java.util.List<OrderItem> items = new java.util.ArrayList<>();
        int index = 0;
        
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getOrderItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getQuantity() == null) {
                log.warn("订单项数据不完整，跳过，productId: {}, quantity: {}", 
                        itemRequest.getProductId(), itemRequest.getQuantity());
                continue;
            }
            
            if (itemRequest.getSkuId() == null) {
                log.error("订单项缺少skuId，商品ID: {}", itemRequest.getProductId());
                throw new com.jiaoyi.common.exception.BusinessException("订单项必须包含skuId，商品ID: " + itemRequest.getProductId());
            }
            
            // 从数据库查询商品信息（获取真实价格，使用 merchantId 和 productId，避免查询所有分片）
            com.jiaoyi.common.ApiResponse<?> productResponse = productServiceClient.getProductByMerchantIdAndId(
                    request.getMerchantId(), itemRequest.getProductId());
            if (productResponse.getCode() != 200 || productResponse.getData() == null) {
                log.error("商品不存在或查询失败，商品ID: {}", itemRequest.getProductId());
                throw new com.jiaoyi.common.exception.BusinessException("商品不存在: " + itemRequest.getProductId());
            }
            
            // 解析商品信息
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> productMap = (java.util.Map<String, Object>) 
                        objectMapper.convertValue(productResponse.getData(), java.util.Map.class);
                
                // 优先使用SKU价格
                java.math.BigDecimal unitPrice = null;
                String skuName = null;
                String skuAttributes = null;
                
                // 1. 尝试从SKU列表中获取SKU价格
                Object skusObj = productMap.get("skus");
                if (skusObj != null && skusObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> skus = (java.util.List<java.util.Map<String, Object>>) skusObj;
                    for (java.util.Map<String, Object> sku : skus) {
                        Object skuIdObj = sku.get("id");
                        if (skuIdObj != null && skuIdObj.toString().equals(itemRequest.getSkuId().toString())) {
                            Object skuPriceObj = sku.get("skuPrice");
                            if (skuPriceObj != null) {
                                unitPrice = new java.math.BigDecimal(skuPriceObj.toString());
                            }
                            if (sku.get("skuName") != null) {
                                skuName = sku.get("skuName").toString();
                            }
                            if (sku.get("skuAttributes") != null) {
                                skuAttributes = sku.get("skuAttributes").toString();
                            }
                            log.debug("找到SKU，SKU ID: {}, 价格: {}, 名称: {}", itemRequest.getSkuId(), unitPrice, skuName);
                            break;
                        }
                    }
                }
                
                // 2. 如果SKU没有价格，使用商品价格
                if (unitPrice == null) {
                    if (productMap.get("unitPrice") != null) {
                        unitPrice = new java.math.BigDecimal(productMap.get("unitPrice").toString());
                        log.debug("使用商品价格，商品ID: {}, 价格: {}", itemRequest.getProductId(), unitPrice);
                    }
                }
                
                if (unitPrice == null || unitPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.error("商品价格无效，商品ID: {}, SKU ID: {}, 价格: {}", itemRequest.getProductId(), itemRequest.getSkuId(), unitPrice);
                    throw new com.jiaoyi.common.exception.BusinessException("商品价格无效: " + itemRequest.getProductId());
                }
                
                // 获取商品名称和图片（优先使用数据库中的，如果数据库没有则使用前端传来的）
                String productName = productMap.get("productName") != null ? 
                        productMap.get("productName").toString() : itemRequest.getProductName();
                String productImage = productMap.get("productImage") != null ? 
                        productMap.get("productImage").toString() : itemRequest.getProductImage();
                
                // 创建订单项
                OrderItem item = new OrderItem();
                item.setProductId(itemRequest.getProductId());
                item.setSkuId(itemRequest.getSkuId());
                item.setSkuName(skuName);
                item.setSkuAttributes(skuAttributes);
                item.setItemName(productName != null ? productName : "商品");
                item.setProductImage(productImage);
                item.setItemPrice(unitPrice); // 使用SKU价格或商品价格
                item.setQuantity(itemRequest.getQuantity());
                
                // 设置 saleItemId：如果没有，使用 productId 作为默认值
                item.setSaleItemId(itemRequest.getProductId());
                
                // 设置 orderItemId：使用索引+1
                item.setOrderItemId((long) (index + 1));
                
                // 计算小计（使用SKU价格或商品价格）
                item.setItemPriceTotal(unitPrice.multiply(
                        java.math.BigDecimal.valueOf(itemRequest.getQuantity())));
                
                items.add(item);
                index++;
                
                log.debug("订单项构建成功，商品ID: {}, SKU ID: {}, 商品名称: {}, 单价: {}, 数量: {}, 小计: {}", 
                        itemRequest.getProductId(), itemRequest.getSkuId(), productName, unitPrice, itemRequest.getQuantity(), 
                        item.getItemPriceTotal());
                
            } catch (Exception e) {
                log.error("查询商品信息失败，商品ID: {}, SKU ID: {}", itemRequest.getProductId(), itemRequest.getSkuId(), e);
                throw new com.jiaoyi.common.exception.BusinessException("查询商品信息失败: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * 根据订单ID查询订单
     */
    /**
     * 查询订单配送状态（包含 DoorDash 详细信息：tracking_url、ETA、距离、骑手信息等）
     */
    @GetMapping("/{orderId}/delivery-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeliveryStatus(@PathVariable Long orderId) {
        log.info("查询订单配送状态，订单ID: {}", orderId);
        
        try {
            Optional<Order> orderOpt = orderService.getOrderById(orderId);
            if (!orderOpt.isPresent()) {
                return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
            }
            Order order = orderOpt.get();
            
            // 如果不是配送订单或没有 deliveryId，返回错误
            if (order.getOrderType() == null || !com.jiaoyi.order.enums.OrderTypeEnum.DELIVERY.equals(order.getOrderType()) || order.getDeliveryId() == null) {
                return ResponseEntity.ok(ApiResponse.error(400, "该订单不是 DoorDash 配送订单"));
            }
            
            // 查询 DoorDash 配送状态
            DoorDashService.DoorDashDeliveryStatus status = doorDashService.getDeliveryStatus(order.getDeliveryId());
            
            if (status == null) {
                return ResponseEntity.ok(ApiResponse.error(500, "查询配送状态失败"));
            }
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.getId());
            response.put("deliveryId", status.getDeliveryId());
            response.put("status", status.getStatus());
            response.put("trackingUrl", status.getTrackingUrl());
            response.put("distanceMiles", status.getDistanceMiles());
            response.put("etaMinutes", status.getEtaMinutes());
            response.put("dasherName", status.getDasherName());
            response.put("dasherPhone", status.getDasherPhone());
            
            // 从配送记录的 additionalData 中获取更多信息
            Delivery delivery = null;
            if (order.getDeliveryId() != null && !order.getDeliveryId().isEmpty()) {
                delivery = deliveryMapper.selectById(order.getDeliveryId());
            }
            if (delivery == null) {
                delivery = deliveryMapper.selectByOrderId(orderId);
            }
            if (delivery != null && delivery.getAdditionalData() != null && !delivery.getAdditionalData().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> additionalData = objectMapper.readValue(
                            delivery.getAdditionalData(), 
                            Map.class
                    );
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deliveryInfo = (Map<String, Object>) additionalData.get("deliveryInfo");
                    if (deliveryInfo != null) {
                        response.put("deliveryInfo", deliveryInfo);
                    }
                } catch (Exception e) {
                    log.warn("解析 additionalData 失败，订单ID: {}", orderId, e);
                }
            }
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", response));
            
        } catch (Exception e) {
            log.error("查询订单配送状态失败，订单ID: {}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询配送状态失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrderById(@PathVariable Long orderId) {
        log.info("查询订单，订单ID: {}", orderId);
        Optional<Order> order = orderService.getOrderById(orderId);
        if (order.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", order.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
    }
    
    /**
     * 根据merchantId和id查询订单（推荐，包含分片键）
     */
    @GetMapping("/merchant/{merchantId}/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrderByMerchantIdAndId(
            @PathVariable String merchantId,
            @PathVariable Long id) {
        log.info("查询订单，merchantId: {}, id: {}", merchantId, id);
        Optional<Order> order = orderService.getOrderByMerchantIdAndId(merchantId, id);
        if (order.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("查询成功", order.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
    }
    
    /**
     * 根据merchantId查询所有订单
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByMerchantId(@PathVariable String merchantId) {
        log.info("查询餐馆的所有订单，merchantId: {}", merchantId);
        List<Order> orders = orderService.getOrdersByMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", orders));
    }
    
    /**
     * 根据userId查询所有订单
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("查询用户的所有订单，userId: {}", userId);
        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", orders));
    }
    
    /**
     * 根据merchantId和status查询订单
     */
    @GetMapping("/merchant/{merchantId}/status/{status}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByMerchantIdAndStatus(
            @PathVariable String merchantId,
            @PathVariable Integer status) {
        log.info("查询订单，merchantId: {}, status: {}", merchantId, status);
        List<Order> orders = orderService.getOrdersByMerchantIdAndStatus(merchantId, status);
        return ResponseEntity.ok(ApiResponse.success("查询成功", orders));
    }
    
    /**
     * 更新订单
     */
    @PutMapping("/merchant/{merchantId}/{id}")
    public ResponseEntity<ApiResponse<Order>> updateOrder(
            @PathVariable String merchantId,
            @PathVariable Long id,
            @RequestBody Order order) {
        log.info("更新订单，merchantId: {}, id: {}", merchantId, id);
        order.setMerchantId(merchantId);
        order.setId(id);
        try {
            Order updatedOrder = orderService.updateOrder(order);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedOrder));
        } catch (Exception e) {
            log.error("更新订单失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除订单
     */
    @DeleteMapping("/merchant/{merchantId}/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(
            @PathVariable String merchantId,
            @PathVariable Long id) {
        log.info("删除订单，merchantId: {}, id: {}", merchantId, id);
        try {
            orderService.deleteOrder(merchantId, id);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除订单失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新订单状态（在线点餐：status 是 Integer）
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<Void>> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam Integer status) {
        
        log.info("更新订单状态，订单ID: {}, 状态: {}", orderId, status);
        boolean success = orderService.updateOrderStatus(orderId, status);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("订单状态更新成功", null));
        } else {
            return ResponseEntity.ok(ApiResponse.error("订单状态更新失败"));
        }
    }
    
    /**
     * 支付订单
     */
    @PostMapping("/{orderId}/pay")
    @PreventDuplicateSubmission(key = "#orderId + '_pay'", expireTime = 60, message = "请勿重复提交支付")
    public ResponseEntity<ApiResponse<PaymentResponse>> payOrder(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest request) {
        log.info("支付订单，订单ID: {}, 支付方式: {}", orderId, request.getPaymentMethod());
        
        try {
            PaymentResponse paymentResponse = paymentService.processPayment(orderId, request);
            return ResponseEntity.ok(ApiResponse.success("支付处理成功", paymentResponse));
        } catch (Exception e) {
            log.error("支付处理失败", e);
            return ResponseEntity.ok(ApiResponse.error("支付处理失败: " + e.getMessage()));
        }
    }
    
    /**
     * 取消订单
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        log.info("取消订单，订单ID: {}", orderId);
        boolean success = orderService.cancelOrder(orderId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("订单取消成功", null));
        } else {
            return ResponseEntity.ok(ApiResponse.error("订单取消失败"));
        }
    }
    
    /**
     * 商家接单（将待接单状态更新为制作中）
     */
    @PutMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptOrder(@PathVariable Long orderId) {
        log.info("商家接单，订单ID: {}", orderId);
        try {
            boolean success = orderService.acceptOrder(orderId);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("接单成功", null));
            } else {
                return ResponseEntity.ok(ApiResponse.error("接单失败：订单状态不正确或订单不存在"));
            }
        } catch (Exception e) {
            log.error("商家接单失败，订单ID: {}", orderId, e);
            return ResponseEntity.ok(ApiResponse.error("接单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 生成订单锁的 key（用于防重复提交）
     * 基于订单内容生成哈希值：merchantId + userId + orderItems(商品ID+SKU ID+数量)的哈希
     * 
     * @param request 订单请求体
     * @return 锁的 key
     */
    public static String generateOrderLockKey(CreateOrderRequest request) {
        if (request == null || request.getMerchantId() == null || request.getUserId() == null) {
            // 如果无法解析，使用默认 key（基于对象哈希）
            return "order:create:default:" + System.identityHashCode(request);
        }
        
        // 构建订单内容的字符串表示（用于哈希）
        StringBuilder content = new StringBuilder();
        content.append(request.getMerchantId()).append("|");
        content.append(request.getUserId()).append("|");
        content.append(request.getOrderType() != null ? request.getOrderType() : "").append("|");
        
        // 添加订单项的哈希（商品ID + SKU ID + 数量）
        if (request.getOrderItems() != null && !request.getOrderItems().isEmpty()) {
            java.util.List<String> itemKeys = new java.util.ArrayList<>();
            for (CreateOrderRequest.OrderItemRequest item : request.getOrderItems()) {
                if (item.getProductId() != null && item.getSkuId() != null && item.getQuantity() != null) {
                    itemKeys.add(item.getProductId() + ":" + item.getSkuId() + ":" + item.getQuantity());
                }
            }
            // 排序以确保相同订单内容生成相同的哈希
            java.util.Collections.sort(itemKeys);
            content.append(String.join(",", itemKeys));
        }
        
        // 计算哈希值
        int hashCode = content.toString().hashCode();
        
        // 生成锁 key
        return String.format("order:create:%s:%d:%d", 
            request.getMerchantId(), 
            request.getUserId(), 
            hashCode);
    }
}


