package com.jiaoyi.order.controller;

import com.jiaoyi.common.annotation.PreventDuplicateSubmission;
import com.jiaoyi.common.ApiResponse;
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
     * 参照 OO 项目的 checkoutOrder，支持在创建订单时一起处理支付
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(@RequestBody Object requestBody) {
        log.info("创建订单请求，请求体类型: {}", requestBody.getClass().getSimpleName());
        
        try {
            CreateOrderRequest request;
            
            // 兼容旧格式：{order: {...}, orderItems: [...]}
            if (requestBody instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) requestBody;
                
                if (map.containsKey("order") && map.containsKey("orderItems")) {
                    // 旧格式，转换为新格式
                    log.info("检测到旧格式订单请求，进行转换");
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> orderMap = (java.util.Map<String, Object>) map.get("order");
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> orderItemsList = 
                        (java.util.List<java.util.Map<String, Object>>) map.get("orderItems");
                    
                    request = convertOldFormatToNewFormat(orderMap, orderItemsList);
                } else {
                    // 尝试直接转换为 CreateOrderRequest
                    request = objectMapper.convertValue(requestBody, CreateOrderRequest.class);
                }
            } else if (requestBody instanceof CreateOrderRequest) {
                request = (CreateOrderRequest) requestBody;
            } else {
                // 尝试通过 ObjectMapper 转换
                request = objectMapper.convertValue(requestBody, CreateOrderRequest.class);
            }
            
            log.info("创建在线点餐订单请求，userId: {}, paymentMethod: {}, payOnline: {}", 
                    request.getUserId(), request.getPaymentMethod(), request.getPayOnline());
            
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
            
            // 3. 创建订单
            Order createdOrder = orderService.createOrder(
                    order, 
                    orderItems,
                    request.getCouponIds(),
                    request.getCouponCodes()
            );
            
            // 4. 如果是在线支付，处理支付（参照 OO 项目的 checkoutOnlineOrder）
            CreateOrderResponse response = new CreateOrderResponse();
            response.setOrder(createdOrder);
            
            // 注意：对于信用卡支付，不在创建订单时创建 Payment Intent
            // 因为此时用户还没有填写卡片信息，无法创建 Payment Method
            // Payment Intent 应该在用户填写卡片后，在支付页面创建
            if (request.getPayOnline() != null && request.getPayOnline() && 
                request.getPaymentMethod() != null && !request.getPaymentMethod().isEmpty()) {
                
                String paymentMethod = request.getPaymentMethod().toUpperCase();
                
                // 只有非信用卡支付（支付宝、微信）在创建订单时处理
                // 信用卡支付需要等用户填写卡片后再创建 Payment Intent
                if ("ALIPAY".equalsIgnoreCase(paymentMethod)) {
                    log.info("处理支付宝支付，订单ID: {}", createdOrder.getId());
                    
                    PaymentRequest paymentRequest = new PaymentRequest();
                    paymentRequest.setPaymentMethod("ALIPAY");
                    java.math.BigDecimal amount = parseOrderPrice(createdOrder);
                    paymentRequest.setAmount(amount);
                    
                    PaymentResponse paymentResponse = paymentService.processPayment(createdOrder.getId(), paymentRequest);
                    response.setPayment(paymentResponse);
                    response.setPaymentUrl(paymentResponse.getPayUrl());
                    
                } else if ("WECHAT_PAY".equalsIgnoreCase(paymentMethod) || 
                           "WECHAT".equalsIgnoreCase(paymentMethod)) {
                    log.info("处理微信支付，订单ID: {}", createdOrder.getId());
                    
                    PaymentRequest paymentRequest = new PaymentRequest();
                    paymentRequest.setPaymentMethod("WECHAT_PAY");
                    java.math.BigDecimal amount = parseOrderPrice(createdOrder);
                    paymentRequest.setAmount(amount);
                    
                    PaymentResponse paymentResponse = paymentService.processPayment(createdOrder.getId(), paymentRequest);
                    response.setPayment(paymentResponse);
                    response.setPaymentUrl(paymentResponse.getPayUrl());
                }
                // 信用卡支付（CREDIT_CARD/CARD/STRIPE）不在创建订单时处理
                // 等用户填写卡片后，在支付页面调用 /api/orders/{orderId}/pay 创建 Payment Intent
            }
            
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
        order.setOrderType(request.getOrderType());
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
                
                // 获取商品价格（从数据库查询，不使用前端传来的价格）
                java.math.BigDecimal unitPrice = null;
                if (productMap.get("unitPrice") != null) {
                    unitPrice = new java.math.BigDecimal(productMap.get("unitPrice").toString());
                }
                
                if (unitPrice == null || unitPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.error("商品价格无效，商品ID: {}, 价格: {}", itemRequest.getProductId(), unitPrice);
                    throw new com.jiaoyi.common.exception.BusinessException("商品价格无效: " + itemRequest.getProductId());
                }
                
                // 验证前端传来的价格（如果传了）是否与数据库价格一致（用于检测前端篡改）
                if (itemRequest.getUnitPrice() != null) {
                    java.math.BigDecimal frontendPrice = itemRequest.getUnitPrice();
                    if (frontendPrice.compareTo(unitPrice) != 0) {
                        log.warn("前端传来的商品价格与数据库不一致，商品ID: {}, 前端价格: {}, 数据库价格: {}", 
                                itemRequest.getProductId(), frontendPrice, unitPrice);
                        // 不抛出异常，但记录警告，使用数据库价格
                    }
                }
                
                // 获取商品名称和图片（优先使用数据库中的，如果数据库没有则使用前端传来的）
                String productName = productMap.get("productName") != null ? 
                        productMap.get("productName").toString() : itemRequest.getProductName();
                String productImage = productMap.get("productImage") != null ? 
                        productMap.get("productImage").toString() : itemRequest.getProductImage();
                
                // 创建订单项
                OrderItem item = new OrderItem();
                item.setProductId(itemRequest.getProductId());
                item.setItemName(productName != null ? productName : "商品");
                item.setProductImage(productImage);
                item.setItemPrice(unitPrice); // 使用数据库查询的价格
                item.setQuantity(itemRequest.getQuantity());
                
                // 设置 saleItemId：如果没有，使用 productId 作为默认值
                item.setSaleItemId(itemRequest.getProductId());
                
                // 设置 orderItemId：使用索引+1
                item.setOrderItemId((long) (index + 1));
                
                // 计算小计（使用数据库价格）
                item.setItemPriceTotal(unitPrice.multiply(
                        java.math.BigDecimal.valueOf(itemRequest.getQuantity())));
                
                items.add(item);
                index++;
                
                log.debug("订单项构建成功，商品ID: {}, 商品名称: {}, 单价: {}, 数量: {}, 小计: {}", 
                        itemRequest.getProductId(), productName, unitPrice, itemRequest.getQuantity(), 
                        item.getItemPriceTotal());
                
            } catch (Exception e) {
                log.error("查询商品信息失败，商品ID: {}", itemRequest.getProductId(), e);
                throw new com.jiaoyi.common.exception.BusinessException("查询商品信息失败: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * 将旧格式转换为新格式
     * 旧格式：{order: {merchantId, userId, ...}, orderItems: [{saleItemId, itemName, itemPrice, ...}]}
     * 新格式：CreateOrderRequest
     */
    private CreateOrderRequest convertOldFormatToNewFormat(
            java.util.Map<String, Object> orderMap, 
            java.util.List<java.util.Map<String, Object>> orderItemsList) {
        
        CreateOrderRequest request = new CreateOrderRequest();
        
        // 转换订单基本信息
        request.setMerchantId((String) orderMap.get("merchantId"));
        Object userIdObj = orderMap.get("userId");
        if (userIdObj instanceof Number) {
            request.setUserId(((Number) userIdObj).longValue());
        }
        request.setOrderType((String) orderMap.get("orderType"));
        
        // 从 customerInfo 中提取收货信息（如果有）
        Object customerInfoObj = orderMap.get("customerInfo");
        if (customerInfoObj instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> customerInfo = objectMapper.readValue(
                    (String) customerInfoObj, java.util.Map.class);
                request.setReceiverName((String) customerInfo.get("name"));
                request.setReceiverPhone((String) customerInfo.get("phone"));
                request.setReceiverAddress((String) customerInfo.get("address"));
            } catch (Exception e) {
                log.warn("解析 customerInfo 失败", e);
            }
        }
        
        // 如果没有 customerInfo，尝试从 orderMap 中直接获取
        if (request.getReceiverName() == null) {
            request.setReceiverName((String) orderMap.get("receiverName"));
            request.setReceiverPhone((String) orderMap.get("receiverPhone"));
            request.setReceiverAddress((String) orderMap.get("receiverAddress"));
        }
        
        // 如果还是没有，设置默认值
        if (request.getReceiverName() == null) {
            request.setReceiverName("未填写");
            request.setReceiverPhone("未填写");
            request.setReceiverAddress("未填写");
        }
        
        request.setRemark((String) orderMap.get("notes"));
        
        // 转换订单项
        java.util.List<CreateOrderRequest.OrderItemRequest> orderItems = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> itemMap : orderItemsList) {
            CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
            
            // 旧格式使用 saleItemId，新格式使用 productId
            Object productIdObj = itemMap.get("productId");
            if (productIdObj == null) {
                productIdObj = itemMap.get("saleItemId");
            }
            if (productIdObj != null) {
                if (productIdObj instanceof Number) {
                    itemRequest.setProductId(((Number) productIdObj).longValue());
                } else if (productIdObj instanceof String) {
                    // 支持字符串类型的 productId，避免 JavaScript 大整数精度丢失
                    try {
                        itemRequest.setProductId(Long.parseLong((String) productIdObj));
                    } catch (NumberFormatException e) {
                        log.warn("productId 格式错误，无法解析为 Long: {}", productIdObj);
                    }
                }
            }
            
            // 旧格式使用 itemName，新格式使用 productName
            String productName = (String) itemMap.get("productName");
            if (productName == null) {
                productName = (String) itemMap.get("itemName");
            }
            itemRequest.setProductName(productName);
            
            itemRequest.setProductImage((String) itemMap.get("productImage"));
            
            // 旧格式使用 itemPrice，新格式使用 unitPrice
            Object priceObj = itemMap.get("unitPrice");
            if (priceObj == null) {
                priceObj = itemMap.get("itemPrice");
            }
            if (priceObj != null) {
                if (priceObj instanceof Number) {
                    itemRequest.setUnitPrice(java.math.BigDecimal.valueOf(
                        ((Number) priceObj).doubleValue()));
                } else if (priceObj instanceof String) {
                    itemRequest.setUnitPrice(new java.math.BigDecimal((String) priceObj));
                }
            }
            
            // quantity
            Object quantityObj = itemMap.get("quantity");
            if (quantityObj instanceof Number) {
                itemRequest.setQuantity(((Number) quantityObj).intValue());
            }
            
            orderItems.add(itemRequest);
        }
        request.setOrderItems(orderItems);
        
        return request;
    }
    
    /**
     * 从订单价格JSON中解析总金额
     */
    private java.math.BigDecimal parseOrderPrice(Order order) {
        if (order.getOrderPrice() == null) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr.startsWith("{")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> orderPrice = objectMapper.readValue(orderPriceStr, java.util.Map.class);
                Object totalObj = orderPrice.get("total");
                if (totalObj != null) {
                    return new java.math.BigDecimal(totalObj.toString());
                }
            }
        } catch (Exception e) {
            log.error("解析订单价格失败，订单ID: {}", order.getId(), e);
        }
        return java.math.BigDecimal.ZERO;
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
            if (!"DELIVERY".equalsIgnoreCase(order.getOrderType()) || order.getDeliveryId() == null) {
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
}


