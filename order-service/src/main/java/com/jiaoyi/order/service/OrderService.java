package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.client.CouponServiceClient;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.dto.CalculatePriceRequest;
import com.jiaoyi.order.dto.CalculatePriceResponse;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.entity.OrderCoupon;
import com.jiaoyi.order.entity.CapabilityOfOrderConfig;
import com.jiaoyi.order.entity.CapabilityOfOrder;
import com.jiaoyi.order.entity.MerchantCapabilityConfig;
import com.jiaoyi.order.enums.OrderTypeEnum;
import com.jiaoyi.order.mapper.OrderItemMapper;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.OrderCouponMapper;
import com.jiaoyi.order.mapper.MerchantCapabilityConfigMapper;
import com.jiaoyi.order.mapper.UserOrderIndexMapper;
import com.jiaoyi.order.entity.UserOrderIndex;
import com.jiaoyi.order.service.OutboxHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCouponMapper orderCouponMapper;
    private final UserOrderIndexMapper userOrderIndexMapper;
    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final RedissonClient redissonClient;
    private final OrderTimeoutMessageService orderTimeoutMessageService;
    private final ObjectMapper objectMapper;
    private final FeeCalculationService feeCalculationService;
    private final OutboxHelper outboxHelper;
    private final DoorDashService doorDashService;
    private final DeliveryRuleService deliveryRuleService;
    private final PaymentService paymentService;
    private final PeakHourRejectionService peakHourRejectionService;
    private final MerchantCapabilityConfigMapper merchantCapabilityConfigMapper;

    /**
     * 计算订单价格（预览价格，不创建订单）
     * 用于前端在提交订单前预览价格
     */
    public CalculatePriceResponse calculateOrderPrice(CalculatePriceRequest request) {
        log.info("计算订单价格，商户ID: {}, 订单类型: {}", request.getMerchantId(), request.getOrderType());

        // 1. 构建临时订单对象（用于计算费用）
        Order tempOrder = new Order();
        tempOrder.setMerchantId(request.getMerchantId());
        tempOrder.setUserId(request.getUserId());
        // 将字符串转换为枚举
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(request.getOrderType());
        if (orderTypeEnum == null) {
            throw new BusinessException("无效的订单类型: " + request.getOrderType());
        }
        tempOrder.setOrderType(orderTypeEnum);

        // 设置配送地址信息（用于计算配送费）
        try {
            java.util.Map<String, Object> customerInfo = new java.util.HashMap<>();
            customerInfo.put("address", request.getReceiverAddress() != null ? request.getReceiverAddress() : "");
            if (request.getZipCode() != null && !request.getZipCode().isEmpty()) {
                customerInfo.put("zipCode", request.getZipCode());
            }
            if (request.getLatitude() != null && request.getLongitude() != null) {
                customerInfo.put("latitude", request.getLatitude());
                customerInfo.put("longitude", request.getLongitude());
            }
            tempOrder.setCustomerInfo(objectMapper.writeValueAsString(customerInfo));
        } catch (Exception e) {
            log.warn("构建客户信息失败", e);
        }

        // 2. 计算订单小计（从数据库查询商品价格，优先使用SKU价格）
        BigDecimal subtotal = BigDecimal.ZERO;
        for (com.jiaoyi.order.dto.CalculatePriceRequest.OrderItemRequest itemRequest : request.getOrderItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                continue;
            }
            
            if (itemRequest.getSkuId() == null) {
                log.warn("订单项缺少skuId，商户ID: {}, 商品ID: {}", request.getMerchantId(), itemRequest.getProductId());
                throw new BusinessException("订单项必须包含skuId，商品ID: " + itemRequest.getProductId());
            }

            // 从商品服务获取商品信息（使用 merchantId 和 productId，避免查询所有分片）
            log.debug("查询商品信息，商户ID: {}, 商品ID: {}, SKU ID: {}", request.getMerchantId(), itemRequest.getProductId(), itemRequest.getSkuId());
            com.jiaoyi.common.ApiResponse<?> productResponse = productServiceClient.getProductByMerchantIdAndId(
                    request.getMerchantId(), itemRequest.getProductId());
            
            if (productResponse.getCode() != 200) {
                log.warn("商品查询失败，商户ID: {}, 商品ID: {}, 响应码: {}, 消息: {}", 
                        request.getMerchantId(), itemRequest.getProductId(), 
                        productResponse.getCode(), productResponse.getMessage());
                continue;
            }
            
            if (productResponse.getData() == null) {
                log.warn("商品不存在，商户ID: {}, 商品ID: {}", request.getMerchantId(), itemRequest.getProductId());
                continue;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> productMap = (java.util.Map<String, Object>) productResponse.getData();
            
            // 优先使用SKU价格
            BigDecimal unitPrice = null;
            
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
                            unitPrice = new BigDecimal(skuPriceObj.toString());
                            log.debug("使用SKU价格，SKU ID: {}, 价格: {}", itemRequest.getSkuId(), unitPrice);
                            break;
                        }
                    }
                }
            }
            
            // 2. 如果SKU没有价格，使用商品价格
            if (unitPrice == null) {
                Object unitPriceObj = productMap.get("unitPrice");
                if (unitPriceObj != null) {
                    unitPrice = new BigDecimal(unitPriceObj.toString());
                    log.debug("使用商品价格，商品ID: {}, 价格: {}", itemRequest.getProductId(), unitPrice);
                }
            }
            
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("商品价格无效（<=0），商户ID: {}, 商品ID: {}, SKU ID: {}, 价格: {}", 
                        request.getMerchantId(), itemRequest.getProductId(), itemRequest.getSkuId(), unitPrice);
                continue;
            }
            
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            subtotal = subtotal.add(itemTotal);
            log.debug("商品价格计算成功，商户ID: {}, 商品ID: {}, SKU ID: {}, 单价: {}, 数量: {}, 小计: {}", 
                    request.getMerchantId(), itemRequest.getProductId(), itemRequest.getSkuId(), unitPrice, itemRequest.getQuantity(), itemTotal);
        }

        // 3. 处理优惠券（计算优惠金额）
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getCouponIds() != null && !request.getCouponIds().isEmpty()) {
            for (Long couponId : request.getCouponIds()) {
                try {
                    com.jiaoyi.common.ApiResponse<?> couponResponse = couponServiceClient.getCouponById(couponId);
                    if (couponResponse.getCode() == 200 && couponResponse.getData() != null) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> couponData = (java.util.Map<String, Object>) couponResponse.getData();
                        String couponCode = couponData.get("couponCode") != null ? couponData.get("couponCode").toString() : null;
                        if (couponCode != null) {
                            // 验证优惠券（不传 productIds，因为接口不支持）
                            boolean isValid = couponServiceClient.validateCoupon(couponCode, request.getUserId(), subtotal).getData();
                            if (isValid) {
                                BigDecimal discount = couponServiceClient.calculateDiscountAmount(couponCode, subtotal).getData();
                                if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
                                    discountAmount = discountAmount.add(discount);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("处理优惠券失败，优惠券ID: {}", couponId, e);
                }
            }
        } else if (request.getCouponCodes() != null && !request.getCouponCodes().isEmpty()) {
            for (String couponCode : request.getCouponCodes()) {
                try {
                    // 验证优惠券（不传 productIds，因为接口不支持）
                    boolean isValid = couponServiceClient.validateCoupon(couponCode, request.getUserId(), subtotal).getData();
                    if (isValid) {
                        BigDecimal discount = couponServiceClient.calculateDiscountAmount(couponCode, subtotal).getData();
                        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
                            discountAmount = discountAmount.add(discount);
                        }
                    }
                } catch (Exception e) {
                    log.warn("处理优惠券失败，优惠券代码: {}", couponCode, e);
                }
            }
        }

        // 4. 计算配送费
        // 流程：商家基础规则检查 → DoorDash 报价验证
        BigDecimal deliveryFee;
        BigDecimal deliveryFeeQuoted = null; // DoorDash 报价
        
         orderTypeEnum = OrderTypeEnum.fromCode(request.getOrderType());
        if (orderTypeEnum == null) {
            throw new BusinessException("无效的订单类型: " + request.getOrderType());
        }
        if (OrderTypeEnum.DELIVERY.equals(orderTypeEnum)) {
            try {
                // 【步骤1】先检查商家基础规则（距离、时段）
                // 这会快速筛选掉不符合商家规则的地址，避免不必要的 DoorDash API 调用
                java.time.LocalDateTime orderTime = java.time.LocalDateTime.now();
                deliveryRuleService.checkDeliveryRules(
                        request.getMerchantId(),
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getZipCode(),
                        orderTime
                );
                
                log.info("商家基础规则检查通过，商户ID: {}", request.getMerchantId());
                
                // 【步骤2】商家规则通过后，调用 DoorDash 报价 API 进行最终验证
                boolean useDoorDash = isDoorDashDelivery(request.getMerchantId());
                
                if (useDoorDash) {
                    try {
                        Map<String, Object> pickupAddress = getMerchantAddress(request.getMerchantId());
                        Map<String, Object> dropoffAddress = buildDropoffAddress(request);
                        
                        if (pickupAddress != null && dropoffAddress != null) {
                            // 生成外部订单ID（用于 DoorDash quote，格式：order_{orderId}）
                            // 注意：此时订单还未创建，使用临时ID，创建订单后需要更新
                            String externalDeliveryId = "order_temp_" + System.currentTimeMillis();
                            
                            DoorDashService.DoorDashQuoteResponse quote = doorDashService.quoteDelivery(
                                    externalDeliveryId,
                                    pickupAddress, 
                                    dropoffAddress, 
                                    subtotal
                            );
                            
                            deliveryFeeQuoted = quote.getQuotedFee();
                            
                            // 使用 DoorDash 报价，可能加 buffer（如 10%）以应对费用波动
                            BigDecimal buffer = deliveryFeeQuoted.multiply(new BigDecimal("0.10"));
                            deliveryFee = deliveryFeeQuoted.add(buffer);
                            
                            log.info("DoorDash 报价获取成功，quoted_fee: {}, 加 buffer 后: {}", 
                                    deliveryFeeQuoted, deliveryFee);
                        } else {
                            // 地址信息不完整，使用本地计算
                            log.warn("地址信息不完整，使用本地计算配送费");
                            deliveryFee = feeCalculationService.calculateDeliveryFee(tempOrder, subtotal);
                        }
                    } catch (Exception e) {
                        // DoorDash API 失败（可能是地址超出 DoorDash 配送范围）
                        log.warn("DoorDash 报价失败: {}", e.getMessage());
                        throw new BusinessException("DoorDash 无法配送此地址: " + e.getMessage());
                    }
                } else {
                    // 不使用 DoorDash，使用本地计算的配送费
                    deliveryFee = feeCalculationService.calculateDeliveryFee(tempOrder, subtotal);
                }
                
            } catch (BusinessException e) {
                // 商家规则检查失败或 DoorDash 拒绝，向上抛出错误
                log.warn("配送规则检查失败: {}", e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("配送费计算异常，商户ID: {}", request.getMerchantId(), e);
                throw new BusinessException("配送费计算失败: " + e.getMessage());
            }
        } else {
            // 非配送订单，配送费为 0
            deliveryFee = BigDecimal.ZERO;
        }

        // 5. 计算税费
        BigDecimal taxTotal = feeCalculationService.calculateTax(tempOrder, subtotal, discountAmount);

        // 6. 计算在线服务费
        BigDecimal charge = feeCalculationService.calculateOnlineServiceFee(tempOrder, subtotal);

        // 7. 小费（暂时不支持，设为0）
        BigDecimal tips = request.getTips() != null ? request.getTips() : BigDecimal.ZERO;

        // 8. 计算总金额
        BigDecimal total = subtotal.add(deliveryFee).add(taxTotal).add(tips).add(charge).subtract(discountAmount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        // 9. 构建响应
        com.jiaoyi.order.dto.CalculatePriceResponse response = new com.jiaoyi.order.dto.CalculatePriceResponse();
        response.setSubtotal(subtotal);
        response.setDeliveryFee(deliveryFee);
        response.setDeliveryFeeQuoted(deliveryFeeQuoted); // 保存 DoorDash 报价（如果有）
        response.setTaxTotal(taxTotal);
        response.setCharge(charge);
        response.setDiscount(discountAmount);
        response.setTips(tips);
        response.setTotal(total);

        // 构建价格明细说明
        StringBuilder priceDetail = new StringBuilder();
        priceDetail.append(String.format("小计: ¥%.2f", subtotal));
        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            priceDetail.append(String.format(", 优惠: -¥%.2f", discountAmount));
        }
        if (deliveryFee.compareTo(BigDecimal.ZERO) > 0) {
            priceDetail.append(String.format(", 配送费: ¥%.2f", deliveryFee));
        }
        if (taxTotal.compareTo(BigDecimal.ZERO) > 0) {
            priceDetail.append(String.format(", 税费: ¥%.2f", taxTotal));
        }
        if (charge.compareTo(BigDecimal.ZERO) > 0) {
            priceDetail.append(String.format(", 服务费: ¥%.2f", charge));
        }
        if (tips.compareTo(BigDecimal.ZERO) > 0) {
            priceDetail.append(String.format(", 小费: ¥%.2f", tips));
        }
        priceDetail.append(String.format(", 总计: ¥%.2f", total));
        response.setPriceDetail(priceDetail.toString());

        log.info("订单价格计算完成，商户ID: {}, 订单项数量: {}, 小计: {}, 配送费: {}, 税费: {}, 服务费: {}, 优惠: {}, 总计: {}", 
                request.getMerchantId(), request.getOrderItems().size(), subtotal, deliveryFee, taxTotal, charge, discountAmount, total);
        
        // 如果小计为0，记录警告
        if (subtotal.compareTo(BigDecimal.ZERO) == 0 && !request.getOrderItems().isEmpty()) {
            log.warn("警告：订单小计为0，但订单项不为空，商户ID: {}, 订单项: {}", 
                    request.getMerchantId(), request.getOrderItems());
        }

        return response;
    }

    /**
     * 创建订单（在线点餐，保留库存锁定、优惠券等功能）
     * 
     * @param order 订单实体（包含 merchantId, userId, orderType 等）
     * @param orderItems 订单项列表
     * @param couponIds 优惠券ID列表（可选）
     * @param couponCodes 优惠券代码列表（可选）
     * @return 创建后的订单
     */
    @Transactional
    public Order createOrder(Order order, List<OrderItem> orderItems, List<Long> couponIds, List<String> couponCodes) {
        log.info("创建在线点餐订单，merchantId: {}, userId: {}", order.getMerchantId(), order.getUserId());
        
        // 验证必要字段
        if (order.getMerchantId() == null || order.getMerchantId().isEmpty()) {
            throw new BusinessException("merchantId 不能为空");
        }
        if (order.getUserId() == null) {
            throw new BusinessException("userId 不能为空");
        }
        if (order.getOrderType() == null) {
            throw new BusinessException("orderType 不能为空");
        }
        
        // 从订单项中获取 storeId（所有订单项应该属于同一个门店）
        Long storeId = null;
        if (orderItems != null && !orderItems.isEmpty()) {
            // 从第一个订单项的商品信息中获取 storeId
            // 注意：这里假设所有订单项都属于同一个门店
            OrderItem firstItem = orderItems.get(0);
            if (firstItem.getStoreId() != null) {
                storeId = firstItem.getStoreId();
            } else if (firstItem.getProductId() != null) {
                // 如果订单项中没有 storeId，从商品服务查询
                try {
                    com.jiaoyi.common.ApiResponse<?> productResponse = productServiceClient.getProductByMerchantIdAndId(
                            order.getMerchantId(), firstItem.getProductId());
                    if (productResponse.getCode() == 200 && productResponse.getData() != null) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> productMap = (java.util.Map<String, Object>) productResponse.getData();
                        Object storeIdObj = productMap.get("storeId");
                        if (storeIdObj != null) {
                            storeId = Long.valueOf(storeIdObj.toString());
                        }
                    }
                } catch (Exception e) {
                    log.warn("查询商品信息获取 storeId 失败，商品ID: {}", firstItem.getProductId(), e);
                }
            }
        }
        
        if (storeId == null) {
            throw new BusinessException("无法获取门店ID，请确保订单项包含有效的商品信息");
        }
        
        // 设置 storeId 到订单和订单项
        order.setStoreId(storeId);
        if (orderItems != null) {
            for (OrderItem item : orderItems) {
                item.setStoreId(storeId);
            }
        }
        
        // 计算 shard_id（基于 storeId，与商品服务保持一致）
        if (order.getShardId() == null) {
            int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(storeId);
            order.setShardId(shardId);
            log.info("计算 shard_id: {} (基于 storeId: {})", shardId, storeId);
        }
        // 预生成订单ID（按渠道扣减需在 insert 前带 orderId 调用）
        if (order.getId() == null) {
            order.setId(com.jiaoyi.order.util.ShardUtil.generateOrderId());
        }

        // 高峰拒单检查（排除堂食订单）
        if (!OrderTypeEnum.SELF_DINE_IN.equals(order.getOrderType())) {
            MerchantCapabilityConfig config = merchantCapabilityConfigMapper.selectByMerchantId(order.getMerchantId());
            if (config != null && Boolean.TRUE.equals(config.getEnable())) {
                CapabilityOfOrderConfig capabilityConfig = new CapabilityOfOrderConfig();
                capabilityConfig.setEnable(config.getEnable());
                capabilityConfig.setQtyOfOrders(config.getQtyOfOrders());
                capabilityConfig.setTimeInterval(config.getTimeInterval());
                capabilityConfig.setClosingDuration(config.getClosingDuration());
                
                CapabilityOfOrder currentCapability = new CapabilityOfOrder();
                currentCapability.setNextOpenAt(config.getNextOpenAt());
                currentCapability.setReOpenAllAt(config.getReOpenAllAt());
                CapabilityOfOrder.OperateType operate = new CapabilityOfOrder.OperateType();
                operate.setPickUp(config.getOperatePickUp());
                operate.setDelivery(config.getOperateDelivery());
                operate.setTogo(config.getOperateTogo());
                operate.setSelfDineIn(config.getOperateSelfDineIn());
                currentCapability.setOperate(operate);
                
                PeakHourRejectionService.PeakHourRejectionResult result = 
                    peakHourRejectionService.judgeCapabilityOfOrder(
                        order.getMerchantId(), 
                        capabilityConfig, 
                        currentCapability
                    );
                
                if (!result.isEnableOrder()) {
                    String message = "商家当前繁忙，请稍后再试";
                    if (result.getNextOpenAt() != null) {
                        java.time.LocalDateTime nextOpenTime = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(result.getNextOpenAt()),
                            java.time.ZoneId.systemDefault()
                        );
                        message = String.format("商家当前繁忙，预计 %s 后恢复接单", 
                            nextOpenTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                    }
                    log.warn("商户 {} 触发高峰拒单，拒绝订单创建", order.getMerchantId());
                    throw new BusinessException(message);
                }
                
                // 如果触发限流，更新商户状态
                if (result.isTriggered() && result.getNextOpenAt() != null) {
                    merchantCapabilityConfigMapper.updateCapabilityStatus(
                        order.getMerchantId(),
                        result.getNextOpenAt(),
                        System.currentTimeMillis(),
                        "system",
                        "system",
                        "system",
                        config.getVersion()
                    );
                }
            }
        }
        
        // 用户级别的锁：确保同一用户同一时间只能处理一个下单请求
        String userLockKey = com.jiaoyi.order.constants.OrderConstants.ORDER_CREATE_USER_LOCK_PREFIX + order.getUserId();
        RLock userLock = redissonClient.getLock(userLockKey);

        try {
            // 尝试获取用户级别的锁，使用配置的等待时间和持有时间
            // 注意：Redisson的WatchDog机制会自动续期（每10秒续期一次）
            boolean userLockAcquired = userLock.tryLock(
                com.jiaoyi.order.constants.OrderConstants.USER_LOCK_WAIT_SECONDS,
                com.jiaoyi.order.constants.OrderConstants.USER_LOCK_LEASE_SECONDS,
                TimeUnit.SECONDS);

            if (!userLockAcquired) {
                log.warn("获取用户级别锁失败，用户正在处理其他下单请求，用户ID: {}", order.getUserId());
                throw new BusinessException("您有订单正在处理中，请稍后再试");
            }

            log.info("成功获取用户级别锁，用户ID: {}, 开始处理订单创建（锁将自动续期）", order.getUserId());
            
            // 基于订单内容生成锁 key（防重复提交相同订单）
            // 注意：Controller 层已经有 @PreventDuplicateSubmission 注解，这里作为双重保护
            // 使用更细粒度的锁：merchantId + userId + orderItems 的哈希
            StringBuilder lockContent = new StringBuilder();
            lockContent.append(order.getMerchantId()).append("|");
            lockContent.append(order.getUserId()).append("|");
            if (orderItems != null && !orderItems.isEmpty()) {
                java.util.List<String> itemKeys = new java.util.ArrayList<>();
                for (OrderItem item : orderItems) {
                    if (item.getProductId() != null && item.getSkuId() != null && item.getQuantity() != null) {
                        itemKeys.add(item.getProductId() + ":" + item.getSkuId() + ":" + item.getQuantity());
                    }
                }
                java.util.Collections.sort(itemKeys);
                lockContent.append(String.join(",", itemKeys));
            }
            int hashCode = lockContent.toString().hashCode();
            String contentLockKey = String.format("%s%s:%d:%d",
                com.jiaoyi.order.constants.OrderConstants.ORDER_CREATE_CONTENT_LOCK_PREFIX,
                order.getMerchantId(), order.getUserId(), hashCode);
            RLock contentLock = redissonClient.getLock(contentLockKey);

            try {
                    // 尝试获取订单内容级别的锁，使用配置的等待时间和持有时间
                    // 注意：Redisson的WatchDog机制会自动续期（每10秒续期一次）
                    boolean contentLockAcquired = contentLock.tryLock(
                        com.jiaoyi.order.constants.OrderConstants.CONTENT_LOCK_WAIT_SECONDS,
                        com.jiaoyi.order.constants.OrderConstants.CONTENT_LOCK_LEASE_SECONDS,
                        TimeUnit.SECONDS);

                    if (!contentLockAcquired) {
                        log.warn("获取订单内容锁失败，可能存在重复提交，商户ID: {}, 用户ID: {}",
                            order.getMerchantId(), order.getUserId());
                        throw new BusinessException("请勿重复提交相同订单");
                    }

                    log.info("成功获取订单内容锁，开始处理在线点餐订单创建（锁将自动续期），内容锁key: {}", contentLockKey);
                    
                    // 1. 按渠道扣减库存（下单即扣，取消时按订单归还）
                    List<Long> productIds = null;
                    List<Long> skuIds = null;
                    List<Integer> quantities = null;
                    boolean stockDeducted = false;

                    try {
                        if (orderItems != null && !orderItems.isEmpty()) {
                            skuIds = orderItems.stream()
                                    .map(OrderItem::getSkuId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                            productIds = orderItems.stream()
                                    .map(OrderItem::getProductId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                            quantities = orderItems.stream()
                                    .map(OrderItem::getQuantity)
                                    .collect(Collectors.toList());

                            if (!productIds.isEmpty() && !skuIds.isEmpty() && productIds.size() == skuIds.size()
                                    && order.getMerchantId() != null && order.getStoreId() != null && order.getId() != null) {
                                log.info("按渠道扣减库存，订单ID: {}, 商品数量: {}", order.getId(), skuIds.size());
                                ProductServiceClient.ChannelDeductBatchRequest deductRequest = new ProductServiceClient.ChannelDeductBatchRequest();
                                deductRequest.setBrandId(order.getMerchantId());
                                deductRequest.setStoreId(String.valueOf(order.getStoreId()));
                                deductRequest.setChannelCode("ONLINE_ORDER");
                                deductRequest.setOrderId(String.valueOf(order.getId()));
                                List<ProductServiceClient.ChannelDeductItem> items = new java.util.ArrayList<>();
                                for (int i = 0; i < skuIds.size(); i++) {
                                    ProductServiceClient.ChannelDeductItem item = new ProductServiceClient.ChannelDeductItem();
                                    item.setObjectId(skuIds.get(i));
                                    item.setQuantity(java.math.BigDecimal.valueOf(quantities.get(i)));
                                    items.add(item);
                                }
                                deductRequest.setItems(items);
                                try {
                                    productServiceClient.deductByChannelBatch(deductRequest);
                                    stockDeducted = true;
                                } catch (Exception e) {
                                    log.error("按渠道扣减库存失败", e);
                                    throw new BusinessException("库存不足或扣减失败: " + e.getMessage());
                                }
                            } else if (orderItems.stream().anyMatch(i -> i.getProductId() == null || i.getSkuId() == null)) {
                                throw new BusinessException("订单项必须包含productId和skuId");
                            }
                        }
                        
                        // 2. 设置订单默认值
                        if (order.getStatus() == null) {
                            order.setStatus(com.jiaoyi.order.constants.OrderConstants.DEFAULT_ORDER_STATUS);
                        }
                        if (order.getLocalStatus() == null) {
                            order.setLocalStatus(com.jiaoyi.order.constants.OrderConstants.DEFAULT_LOCAL_STATUS);
                        }
                        if (order.getKitchenStatus() == null) {
                            order.setKitchenStatus(com.jiaoyi.order.constants.OrderConstants.DEFAULT_KITCHEN_STATUS);
                        }
                        order.setVersion(com.jiaoyi.order.constants.OrderConstants.DEFAULT_VERSION);
                        order.setCreateTime(LocalDateTime.now());
                        order.setUpdateTime(LocalDateTime.now());
                        
                        // 3. 计算订单总金额（用于优惠券验证和计算）
                        BigDecimal orderSubtotal = calculateOrderSubtotal(orderItems);
                        log.info("订单小计: {}", orderSubtotal);
                        
                        // 4. 处理优惠券（完整实现）
                        List<OrderCoupon> orderCoupons = new ArrayList<>();
                        BigDecimal totalDiscountAmount = BigDecimal.ZERO;
                        
                        if (couponIds != null && !couponIds.isEmpty()) {
                            // 处理优惠券ID列表
                            for (Long couponId : couponIds) {
                                OrderCoupon orderCoupon = processCouponById(couponId, order.getUserId(), orderSubtotal, orderItems);
                                if (orderCoupon != null) {
                                    orderCoupons.add(orderCoupon);
                                    totalDiscountAmount = totalDiscountAmount.add(orderCoupon.getAppliedAmount());
                                }
                            }
                        } else if (couponCodes != null && !couponCodes.isEmpty()) {
                            // 处理优惠券代码列表
                            for (String couponCode : couponCodes) {
                                OrderCoupon orderCoupon = processCouponByCode(couponCode, order.getUserId(), orderSubtotal, orderItems);
                                if (orderCoupon != null) {
                                    orderCoupons.add(orderCoupon);
                                    totalDiscountAmount = totalDiscountAmount.add(orderCoupon.getAppliedAmount());
                                }
                            }
                        }
                        
                        // 5. 更新订单价格（包含优惠金额）
                        updateOrderPriceWithDiscount(order, orderSubtotal, totalDiscountAmount);
                        log.info("订单总金额: {}, 优惠金额: {}, 实际支付: {}", 
                                orderSubtotal, totalDiscountAmount, orderSubtotal.subtract(totalDiscountAmount));
                        
                        // 6. 保存订单
                        orderMapper.insert(order);
                        log.info("订单插入成功，ID: {}", order.getId());

                        // 6.4 记录订单到Redis（用于高峰拒单统计）
                        try {
                            peakHourRejectionService.recordOrder(
                                    order.getMerchantId(),
                                order.getId()
                            );
                        } catch (Exception e) {
                            log.error("记录订单到Redis失败，不影响订单创建: orderId={}", order.getId(), e);
                        }

                        // 6.5 写入用户订单索引表（用于按 userId 查询订单，避免广播查询）
                        try {
                            UserOrderIndex index = UserOrderIndex.builder()
                                    .userId(order.getUserId())
                                    .orderId(order.getId())
                                    .storeId(order.getStoreId())
                                    .merchantId(order.getMerchantId())
                                    .orderStatus(order.getStatus())
                                    .orderType(order.getOrderType() != null ? order.getOrderType().getCode() : null)
                                    .totalAmount(extractTotalAmount(order))
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            userOrderIndexMapper.insert(index);
                            log.info("用户订单索引插入成功，userId: {}, orderId: {}", order.getUserId(), order.getId());
                        } catch (Exception e) {
                            log.error("写入用户订单索引失败，orderId: {}, userId: {}", order.getId(), order.getUserId(), e);
                            // 索引表写入失败不影响订单创建，记录日志即可（可以通过补偿任务修复）
                        }

                        // 7. 创建并保存订单项
                        if (orderItems != null && !orderItems.isEmpty()) {
                            int itemIndex = 0;
                            for (OrderItem item : orderItems) {
                                item.setOrderId(order.getId());
                                item.setMerchantId(order.getMerchantId());
                                // 设置 storeId 和 shardId（与订单保持一致，确保同库同表）
                                item.setStoreId(order.getStoreId());
                                item.setShardId(order.getShardId());
                                
                                // 确保 saleItemId 不为 null（数据库要求）
                                if (item.getSaleItemId() == null) {
                                    if (item.getProductId() != null) {
                                        item.setSaleItemId(item.getProductId());
                                    } else {
                                        item.setSaleItemId(0L); // 默认值
                                    }
                                }
                                
                                // 确保 orderItemId 不为 null（数据库要求）
                                if (item.getOrderItemId() == null) {
                                    item.setOrderItemId((long) (itemIndex + 1));
                                }
                                
                                if (item.getVersion() == null) {
                                    item.setVersion(1L);
                                }
                                if (item.getCreateTime() == null) {
                                    item.setCreateTime(LocalDateTime.now());
                                }
                                if (item.getUpdateTime() == null) {
                                    item.setUpdateTime(LocalDateTime.now());
                                }
                                itemIndex++;
                            }
                            orderItemMapper.insertBatch(orderItems);
                            log.info("订单项插入成功，数量: {}", orderItems.size());
                        }
                        
                        // 8. 保存订单优惠券关联记录（如果有）
                        if (!orderCoupons.isEmpty()) {
                            orderCoupons.forEach(oc -> oc.setOrderId(order.getId()));
                            // 设置 merchantId 和 storeId
                            for (OrderCoupon coupon : orderCoupons) {
                                coupon.setMerchantId(order.getMerchantId());
                                coupon.setStoreId(order.getStoreId());
                            }
                            orderCouponMapper.batchInsert(orderCoupons);
                            
                            // 使用优惠券（调用优惠券服务）
                            for (OrderCoupon orderCoupon : orderCoupons) {
                                try {
                                    if (orderCoupon.getCouponCode() != null && !orderCoupon.getCouponCode().isEmpty()) {
                                        couponServiceClient.useCoupon(
                                                orderCoupon.getCouponCode(),
                                                order.getUserId(),
                                                order.getId(),
                                                orderCoupon.getAppliedAmount()
                                        );
                                        log.info("优惠券使用成功，优惠券代码: {}, 优惠金额: {}", 
                                                orderCoupon.getCouponCode(), orderCoupon.getAppliedAmount());
                                    }
                                } catch (Exception e) {
                                    log.error("使用优惠券失败，优惠券代码: {}, 订单ID: {}", 
                                            orderCoupon.getCouponCode(), order.getId(), e);
                                    // 不抛出异常，记录日志即可（优惠券已保存到订单，后续可以手动处理）
                                }
                            }
                        }
                        
                        // 9. 查询插入后的订单（获取version）
                        Order insertedOrder = orderMapper.selectByMerchantIdAndId(order.getMerchantId(), order.getId());
                        if (insertedOrder == null) {
                            throw new BusinessException("订单创建失败：插入后无法查询到订单记录");
                        }
                        
                        // 10. 设置订单项到订单中
                        insertedOrder.setOrderItems(orderItems);
                        
                        log.info("在线点餐订单创建完成，ID: {}, merchantId: {}, userId: {}, 总金额: {}, 优惠: {}", 
                                insertedOrder.getId(), insertedOrder.getMerchantId(), insertedOrder.getUserId(),
                                orderSubtotal, totalDiscountAmount);
                        
                        // 11. 发送订单超时延迟消息（配置的超时时间后自动取消）
                        orderTimeoutMessageService.sendOrderTimeoutMessage(
                            insertedOrder.getId(),
                            order.getUserId(),
                            com.jiaoyi.order.constants.OrderConstants.ORDER_TIMEOUT_MINUTES);
                        log.info("订单超时延迟消息已发送，订单将在{}分钟后自动取消（如果未支付）",
                            com.jiaoyi.order.constants.OrderConstants.ORDER_TIMEOUT_MINUTES);
                        
                        return insertedOrder;
                        
                    } catch (Exception e) {
                        // 如果订单创建失败，按订单归还已扣库存
                        log.error("在线点餐订单创建失败，尝试归还库存", e);
                        if (stockDeducted && order.getId() != null) {
                            int maxRetries = com.jiaoyi.order.constants.OrderConstants.STOCK_UNLOCK_MAX_RETRIES;
                            boolean returnSuccess = false;
                            for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
                                try {
                                    if (retryCount > 0) {
                                        log.info("重试归还库存，第 {} 次", retryCount);
                                        Thread.sleep(1000L * retryCount);
                                    }
                                    productServiceClient.returnByOrder(String.valueOf(order.getId()));
                                    returnSuccess = true;
                                    log.info("库存归还成功，订单ID: {}", order.getId());
                                    break;
                                } catch (Exception returnException) {
                                    log.error("归还库存失败，重试次数: {}/{}", retryCount + 1, maxRetries, returnException);
                                    if (retryCount == maxRetries - 1) {
                                        log.error("【库存泄漏警告】订单创建失败但归还库存失败，orderId: {}", order.getId());
                                    }
                                }
                            }
                            if (!returnSuccess) {
                                log.error("库存归还失败，已达最大重试次数: {}，需要人工介入", maxRetries);
                            }
                        }
                        throw e;
                    }
                } catch (InterruptedException e) {
                    // 获取订单内容锁被中断
                    log.error("获取订单内容锁被中断，用户ID: {}", order.getUserId(), e);
                    Thread.currentThread().interrupt();
                    throw new BusinessException("系统繁忙，请稍后重试");
                } finally {
                    // 释放订单内容级别的锁（无论成功还是失败都要释放）
                    if (contentLock.isHeldByCurrentThread()) {
                        contentLock.unlock();
                        log.debug("释放订单内容锁，内容锁key: {}", contentLockKey);
                    }
                }
        } catch (InterruptedException e) {
            log.error("获取用户级别锁被中断，用户ID: {}", order.getUserId(), e);
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } finally {
                // 释放用户级别的锁
                if (userLock.isHeldByCurrentThread()) {
                    userLock.unlock();
                    log.debug("释放用户级别锁，用户ID: {}", order.getUserId());
                }
            }
    }

    /**
     * 创建订单并处理支付（在同一事务中）
     * 
     * @param order 订单实体
     * @param orderItems 订单项列表
     * @param couponIds 优惠券ID列表（可选）
     * @param couponCodes 优惠券代码列表（可选）
     * @param paymentRequest 支付请求（可选，如果为 null 则不处理支付）
     * @return 创建订单响应（包含订单和支付信息）
     */
    @Transactional
    public com.jiaoyi.order.dto.CreateOrderResponse createOrderWithPayment(
            Order order, 
            List<OrderItem> orderItems, 
            List<Long> couponIds, 
            List<String> couponCodes,
            com.jiaoyi.order.dto.PaymentRequest paymentRequest) {
        
        log.info("创建订单并处理支付，merchantId: {}, userId: {}, paymentMethod: {}", 
                order.getMerchantId(), order.getUserId(), 
                paymentRequest != null ? paymentRequest.getPaymentMethod() : "无");
        
        // 1. 创建订单
        Order createdOrder = createOrder(order, orderItems, couponIds, couponCodes);
        
        // 2. 构建响应
        com.jiaoyi.order.dto.CreateOrderResponse response = new com.jiaoyi.order.dto.CreateOrderResponse();
        response.setOrder(createdOrder);
        
        // 3. 如果提供了支付请求，处理支付（在同一事务中）
        if (paymentRequest != null && paymentRequest.getPaymentMethod() != null && 
            !paymentRequest.getPaymentMethod().isEmpty()) {
            
            String paymentMethod = paymentRequest.getPaymentMethod().toUpperCase();
            
            // 从订单中获取支付金额
            java.math.BigDecimal amount = com.jiaoyi.order.util.OrderPriceUtil.parseOrderTotal(createdOrder);
            paymentRequest.setAmount(amount);
            
            // 处理支付（注意：对于信用卡支付，不在创建订单时创建 Payment Intent）
            // 因为此时用户还没有填写卡片信息，无法创建 Payment Method
            // Payment Intent 应该在用户填写卡片后，在支付页面创建
            if ("ALIPAY".equalsIgnoreCase(paymentMethod) || 
                "WECHAT_PAY".equalsIgnoreCase(paymentMethod) || 
                "WECHAT".equalsIgnoreCase(paymentMethod)) {
                
                log.info("处理支付，订单ID: {}, 支付方式: {}", createdOrder.getId(), paymentMethod);
                
                com.jiaoyi.order.dto.PaymentResponse paymentResponse = paymentService.processPayment(
                        createdOrder.getId(), paymentRequest);
                response.setPayment(paymentResponse);
                response.setPaymentUrl(paymentResponse.getPayUrl());
            }
            // 信用卡支付（CREDIT_CARD/CARD/STRIPE）不在创建订单时处理
            // 等用户填写卡片后，在支付页面调用 /api/orders/{orderId}/pay 创建 Payment Intent
        }
        
        return response;
    }
    
    // 已删除parseOrderPrice方法，使用OrderPriceUtil工具类替代

    /**
     * 根据订单ID查询订单
     */
    public Optional<Order> getOrderById(Long orderId) {
        log.info("查询订单，订单ID: {}", orderId);
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            order.setOrderItems(orderItems);
            
            // 查询订单优惠券关联记录
            List<OrderCoupon> orderCoupons = orderCouponMapper.selectByOrderId(order.getId());
            order.setOrderCoupons(orderCoupons);
            
            return Optional.of(order);
        }
        return Optional.empty();
    }
    
    /**
     * 根据merchantId和id查询订单（推荐，包含分片键）
     */
    public Optional<Order> getOrderByMerchantIdAndId(String merchantId, Long id) {
        log.info("查询订单，merchantId: {}, id: {}", merchantId, id);
        Order order = orderMapper.selectByMerchantIdAndId(merchantId, id);
        if (order != null) {
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByMerchantIdAndOrderId(merchantId, id);
            order.setOrderItems(orderItems);
            
            // 查询订单优惠券关联记录
            List<OrderCoupon> orderCoupons = orderCouponMapper.selectByOrderId(id);
            order.setOrderCoupons(orderCoupons);
            
            return Optional.of(order);
        }
        return Optional.empty();
    }
    
    /**
     * 根据merchantId查询所有订单
     */
    public List<Order> getOrdersByMerchantId(String merchantId) {
        log.info("查询餐馆的所有订单，merchantId: {}", merchantId);
        List<Order> orders = orderMapper.selectByMerchantId(merchantId);
        return orders.stream()
                .peek(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByMerchantIdAndOrderId(merchantId, order.getId());
                    order.setOrderItems(orderItems);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 根据merchantId和status查询订单
     */
    public List<Order> getOrdersByMerchantIdAndStatus(String merchantId, Integer status) {
        log.info("查询订单，merchantId: {}, status: {}", merchantId, status);
        List<Order> orders = orderMapper.selectByMerchantIdAndStatus(merchantId, status);
        return orders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByMerchantIdAndOrderId(merchantId, order.getId());
                    order.setOrderItems(orderItems);
                    return order;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从订单的 orderPrice JSON 中提取总金额
     */
    private BigDecimal extractTotalAmount(Order order) {
        try {
            if (order.getOrderPrice() != null && !order.getOrderPrice().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> priceMap = objectMapper.readValue(order.getOrderPrice(), Map.class);
                Object totalObj = priceMap.get("total");
                if (totalObj != null) {
                    if (totalObj instanceof BigDecimal) {
                        return (BigDecimal) totalObj;
                    } else if (totalObj instanceof Number) {
                        return new BigDecimal(totalObj.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析订单总金额失败，orderId: {}", order.getId(), e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 根据用户ID查询订单列表（优化版：使用索引表，避免广播查询）
     *
     * 优化前：直接查询订单表，按 userId 查询会触发广播查询（96 张表）
     * 优化后：先查索引表（精准路由），再按 store_id 批量查询订单详情（精准路由）
     */
    public List<Order> getOrdersByUserId(Long userId) {
        log.info("查询用户订单列表，用户ID: {}", userId);

        // 1. 从索引表查询用户的订单ID列表（按 user_id 分片，精准路由，只查 1 张表）
        List<UserOrderIndex> indexes = userOrderIndexMapper.selectByUserId(userId);

        if (indexes == null || indexes.isEmpty()) {
            log.info("用户没有订单，用户ID: {}", userId);
            return Collections.emptyList();
        }

        log.info("从索引表查询到 {} 个订单，用户ID: {}", indexes.size(), userId);

        // 2. 按 store_id 分组（同一个 store 的订单可以一次性查询）
        Map<Long, List<Long>> orderIdsByStore = indexes.stream()
                .collect(Collectors.groupingBy(
                        UserOrderIndex::getStoreId,
                        Collectors.mapping(UserOrderIndex::getOrderId, Collectors.toList())
                ));

        log.info("订单分布在 {} 个商户，用户ID: {}", orderIdsByStore.size(), userId);

        // 3. 批量查询订单详情（每个 store 的订单精准路由到对应分片）
        List<Order> allOrders = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : orderIdsByStore.entrySet()) {
            Long storeId = entry.getKey();
            List<Long> orderIds = entry.getValue();

            // 查询订单（带 store_id，精准路由）
            List<Order> orders = orderMapper.selectByStoreIdAndOrderIds(storeId, orderIds);

            // 填充订单项
            for (Order order : orders) {
                List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                order.setOrderItems(orderItems);
            }

            allOrders.addAll(orders);
        }

        // 4. 按创建时间倒序排序（因为索引表已经按创建时间排序，这里可以省略）
        allOrders.sort((o1, o2) -> {
            if (o1.getCreateTime() == null && o2.getCreateTime() == null) return 0;
            if (o1.getCreateTime() == null) return 1;
            if (o2.getCreateTime() == null) return -1;
            return o2.getCreateTime().compareTo(o1.getCreateTime());
        });

        log.info("查询用户订单列表完成，用户ID: {}, 订单数量: {}", userId, allOrders.size());
        return allOrders;
    }

    /**
     * 更新订单（使用乐观锁）
     */
    @Transactional
    public Order updateOrder(Order order) {
        log.info("更新订单，merchantId: {}, id: {}", order.getMerchantId(), order.getId());
        
        // 查询现有订单获取版本号
        Order existing = orderMapper.selectByMerchantIdAndId(
                order.getMerchantId(), order.getId());
        if (existing == null) {
            throw new RuntimeException("订单不存在，merchantId: " + order.getMerchantId() + 
                    ", id: " + order.getId());
        }
        
        // 设置版本号用于乐观锁
        order.setVersion(existing.getVersion());
        order.setUpdateTime(LocalDateTime.now());
        
        // 更新订单
        int affectedRows = orderMapper.update(order);
        if (affectedRows == 0) {
            throw new RuntimeException("订单更新失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        // 查询更新后的订单
        Order updatedOrder = orderMapper.selectByMerchantIdAndId(
                order.getMerchantId(), order.getId());
        if (updatedOrder == null) {
            throw new RuntimeException("订单更新失败：更新后无法查询到订单记录");
        }
        
        log.info("订单更新成功，merchantId: {}, id: {}, 新版本号: {}", 
                updatedOrder.getMerchantId(), updatedOrder.getId(), updatedOrder.getVersion());
        
        return updatedOrder;
    }
    
    /**
     * 删除订单（逻辑删除）
     */
    @Transactional
    public void deleteOrder(String merchantId, Long id) {
        log.info("删除订单，merchantId: {}, id: {}", merchantId, id);
        
        // 查询现有订单获取版本号
        Order existing = orderMapper.selectByMerchantIdAndId(merchantId, id);
        if (existing == null) {
            throw new RuntimeException("订单不存在，merchantId: " + merchantId + ", id: " + id);
        }
        
        Order order = existing;
        order.setVersion(existing.getVersion());
        
        // 逻辑删除（设置 status = -1）
        int affectedRows = orderMapper.deleteById(order);
        if (affectedRows == 0) {
            throw new RuntimeException("订单删除失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        log.info("订单删除成功，merchantId: {}, id: {}", merchantId, id);
    }

    /**
     * 更新订单状态（在线点餐：status 是 Integer）
     */
    @Transactional
    public boolean updateOrderStatus(Long orderId, Integer status) {
        log.info("更新订单状态，订单ID: {}, 新状态: {}", orderId, status);
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            Integer oldStatus = order.getStatus() instanceof Integer ? 
                (Integer) order.getStatus() : null;
            order.setStatus(status);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateStatus(orderId, status);

            // 处理库存状态变更
            handleInventoryStatusChange(order, oldStatus, status);

            log.info("订单状态更新成功，订单ID: {}, 新状态: {}", orderId, status);
            return true;
        }
        log.warn("订单不存在，订单ID: {}", orderId);
        return false;
    }

    /**
     * 取消订单（使用 OrderStatusEnum.CANCELLED）
     * 
     * 注意：
     * 1. 使用 @Transactional 确保订单状态更新、优惠券退还、库存解锁在同一事务中
     * 2. 使用分布式锁（基于 orderId）防止并发取消
     * 3. 只有 PENDING（待支付）状态的订单才能取消
     */
    @Transactional
    public boolean cancelOrder(Long orderId) {
        log.info("取消订单，订单ID: {}", orderId);
        
        // 分布式锁：防止并发取消同一个订单
        String lockKey = "order:cancel:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取分布式锁失败，订单可能正在被其他线程处理，orderId: {}", orderId);
                return false;
            }
            
            log.info("成功获取分布式锁，开始处理订单取消，orderId: {}", orderId);
            
            try {
                // 查询订单
                Order order = orderMapper.selectById(orderId);
                if (order == null) {
                    log.warn("订单不存在，订单ID: {}", orderId);
                    return false;
                }
                
                // 检查订单状态，只有待支付订单才能取消
                if (!com.jiaoyi.order.enums.OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
                    log.warn("订单状态不允许取消，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
                    return false;
                }
                
                // 双重检查：再次查询订单状态（防止在获取锁期间状态已变更）
                Order currentOrder = orderMapper.selectById(orderId);
                if (currentOrder == null || !com.jiaoyi.order.enums.OrderStatusEnum.PENDING.getCode().equals(currentOrder.getStatus())) {
                    log.warn("订单状态已变更（双重检查），订单ID: {}, 当前状态: {}", orderId, 
                            currentOrder != null ? currentOrder.getStatus() : "null");
                    return false;
                }
                
                // ========== 使用 Outbox 模式：先更新订单状态，然后异步处理资源释放 ==========
                // 1. 先更新订单状态为已取消（原子操作，使用条件更新）
                // 使用条件更新，确保只有 PENDING 状态才能取消，防止并发问题
                Integer oldStatus = order.getStatus();
                int updated = orderMapper.updateStatusIfPending(
                        orderId,
                        com.jiaoyi.order.enums.OrderStatusEnum.PENDING.getCode(),
                        com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode()
                );
                
                if (updated == 0) {
                    log.warn("订单状态更新失败（可能已被其他线程处理或状态不正确），订单ID: {}, 当前状态: {}", 
                            orderId, order.getStatus());
                    return false;
                }
                
                log.info("订单状态已更新为已取消，订单ID: {}, 原状态: {}, 新状态: {}",
                        orderId, oldStatus, com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode());

                // 1.5 从Redis移除订单（用于高峰拒单统计）
                try {
                    peakHourRejectionService.removeOrder(
                        order.getMerchantId().toString(),
                        orderId
                    );
                } catch (Exception e) {
                    log.error("从Redis移除订单失败，不影响订单取消: orderId={}", orderId, e);
                }

                // 2. 查询订单项，准备写入 outbox 任务
                List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
                if (orderItems == null || orderItems.isEmpty()) {
                    log.warn("订单项为空，无法写入取消订单 outbox 任务，订单ID: {}", orderId);
                    // 订单状态已更新，但没有订单项，直接返回成功
                    return true;
                }
                
                // 3. 写入取消订单任务到 outbox（在事务中，确保原子性）
                // Outbox 会异步处理优惠券退还和库存解锁，确保最终一致性
                boolean enqueued = outboxHelper.enqueueCancelOrderTask(orderId, orderItems);
                
                if (!enqueued) {
                    log.error("写入取消订单 outbox 任务失败，订单ID: {}, 订单状态已更新为已取消", orderId);
                    // 订单状态已更新，但 outbox 写入失败，需要人工处理或补偿机制
                    // 注意：这里不抛出异常，因为订单状态已经更新，避免回滚
                    // Outbox 服务会重试，或者可以后续手动触发补偿
                    log.warn("订单状态已更新为已取消，但 outbox 任务写入失败，需要人工处理或补偿，订单ID: {}", orderId);
                } else {
                    log.info("取消订单 outbox 任务写入成功，订单ID: {}, 将异步处理优惠券退还和库存解锁", orderId);
                }
                
                return true;
                
            } finally {
                // 释放分布式锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("释放分布式锁，orderId: {}", orderId);
                }
            }
            
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，orderId: {}", orderId, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("取消订单异常，orderId: {}", orderId, e);
            throw new RuntimeException("取消订单失败", e);
        }
    }
    
    /**
     * 商家接单（将待接单状态更新为制作中）
     */
    public boolean acceptOrder(Long orderId) {
        log.info("商家接单，订单ID: {}", orderId);
        
        // 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，订单ID: {}", orderId);
            return false;
        }
        
        // 检查订单状态，只有"待接单"或"已支付"状态的订单才能接单
        Integer currentStatus = order.getStatus() instanceof Integer ? 
            (Integer) order.getStatus() : null;
        
        if (currentStatus == null) {
            log.warn("订单状态异常，订单ID: {}, 状态: {}", orderId, order.getStatus());
            return false;
        }
        
        // 允许从"待接单"或"已支付"状态接单（兼容性处理）
        boolean canAccept = com.jiaoyi.order.enums.OrderStatusEnum.WAITING_ACCEPT.getCode().equals(currentStatus) ||
                           com.jiaoyi.order.enums.OrderStatusEnum.PAID.getCode().equals(currentStatus);
        
        if (!canAccept) {
            log.warn("订单状态不允许接单，订单ID: {}, 当前状态: {}", orderId, currentStatus);
            return false;
        }
        
        // 原子更新订单状态：从"待接单"或"已支付"更新为"制作中"
        int updated = orderMapper.updateStatusIfPending(
                orderId,
                currentStatus,
                com.jiaoyi.order.enums.OrderStatusEnum.PREPARING.getCode()
        );
        
        if (updated > 0) {
            log.info("商家接单成功，订单ID: {}, 状态: {} -> {}", 
                    orderId, currentStatus, com.jiaoyi.order.enums.OrderStatusEnum.PREPARING.getCode());
            return true;
        } else {
            log.warn("商家接单失败（可能已被其他线程处理），订单ID: {}, 当前状态: {}", orderId, currentStatus);
            return false;
        }
    }

    /**
     * 处理库存状态变更（在线点餐：status 是 Integer）
     */
    private void handleInventoryStatusChange(Order order, Integer oldStatus, Integer newStatus) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }

        List<Long> productIds = order.getOrderItems().stream()
                .filter(item -> item.getProductId() != null)
                .map(OrderItem::getProductId)
                .collect(Collectors.toList());
        List<Long> skuIds = order.getOrderItems().stream()
                .filter(item -> item.getSkuId() != null)
                .map(OrderItem::getSkuId)
                .collect(Collectors.toList());
        List<Integer> quantities = order.getOrderItems().stream()
                .map(OrderItem::getQuantity)
                .collect(Collectors.toList());

        if (productIds.isEmpty() || skuIds.isEmpty() || productIds.size() != skuIds.size()) {
            log.warn("订单项缺少productId或skuId，无法处理库存，订单ID: {}", order.getId());
            return;
        }

        // 支付成功（status = 100）：下单时已按渠道扣减，此处无需再扣
        if (oldStatus != null && oldStatus == 1 && newStatus != null && newStatus == 100) {
            log.debug("订单支付成功，库存已在创建时按渠道扣减，订单ID: {}", order.getId());
        }
        // 订单取消（status = -1）：按订单归还库存
        else if (newStatus != null && newStatus == -1) {
            log.info("订单取消，按订单归还库存，订单ID: {}", order.getId());
            try {
                productServiceClient.returnByOrder(String.valueOf(order.getId()));
            } catch (Exception ex) {
                log.error("按订单归还库存失败，订单ID: {}", order.getId(), ex);
            }
        }
    }
    
    /**
     * 计算订单小计（所有订单项的总价）
     * 使用PriceUtil统一处理精度
     */
    private BigDecimal calculateOrderSubtotal(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal subtotal = orderItems.stream()
                .map(item -> {
                    BigDecimal itemPrice = item.getItemPrice() != null ? item.getItemPrice() : BigDecimal.ZERO;
                    Integer quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                    // 使用PriceUtil统一精度
                    return com.jiaoyi.order.util.PriceUtil.multiply(itemPrice, quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 最终结果也统一精度
        return com.jiaoyi.order.util.PriceUtil.roundPrice(subtotal);
    }
    
    /**
     * 处理优惠券（通过优惠券ID）
     */
    private OrderCoupon processCouponById(Long couponId, Long userId, BigDecimal orderAmount, List<OrderItem> orderItems) {
        try {
            // 1. 获取优惠券信息
            com.jiaoyi.common.ApiResponse<?> couponResponse = couponServiceClient.getCouponById(couponId);
            if (couponResponse.getCode() != 200 || couponResponse.getData() == null) {
                throw new BusinessException("优惠券不存在或不可用: " + couponId);
            }
            
            // 2. 解析优惠券对象
            @SuppressWarnings("unchecked")
            Map<String, Object> couponMap = (Map<String, Object>) objectMapper.convertValue(couponResponse.getData(), Map.class);
            String couponCode = couponMap.get("couponCode") != null ? couponMap.get("couponCode").toString() : null;
            
            if (couponCode == null || couponCode.isEmpty()) {
                throw new BusinessException("优惠券代码为空: " + couponId);
            }
            
            // 3. 验证优惠券
            com.jiaoyi.common.ApiResponse<Boolean> validateResponse = couponServiceClient.validateCoupon(
                    couponCode, userId, orderAmount);
            if (validateResponse.getCode() != 200 || !Boolean.TRUE.equals(validateResponse.getData())) {
                throw new BusinessException("优惠券验证失败: " + couponCode);
            }
            
            // 4. 计算优惠金额
            com.jiaoyi.common.ApiResponse<BigDecimal> calculateResponse = couponServiceClient.calculateDiscountAmount(
                    couponCode, orderAmount);
            if (calculateResponse.getCode() != 200 || calculateResponse.getData() == null) {
                throw new BusinessException("优惠金额计算失败: " + couponCode);
            }
            
            BigDecimal discountAmount = calculateResponse.getData();
            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("优惠金额为0或负数，优惠券代码: {}", couponCode);
                return null;
            }
            
            // 5. 创建订单优惠券关联记录
            OrderCoupon orderCoupon = new OrderCoupon();
            orderCoupon.setOrderId(null); // 稍后设置
            // merchantId 和 storeId 稍后从订单中设置
            orderCoupon.setCouponId(couponId);
            orderCoupon.setCouponCode(couponCode);
            orderCoupon.setAppliedAmount(discountAmount);
            orderCoupon.setCreateTime(LocalDateTime.now());
            
            log.info("优惠券处理成功，优惠券ID: {}, 代码: {}, 优惠金额: {}", couponId, couponCode, discountAmount);
            return orderCoupon;
            
        } catch (Exception e) {
            log.error("处理优惠券失败，优惠券ID: {}", couponId, e);
            throw new BusinessException("优惠券处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理优惠券（通过优惠券代码）
     */
    private OrderCoupon processCouponByCode(String couponCode, Long userId, BigDecimal orderAmount, List<OrderItem> orderItems) {
        try {
            // 1. 获取优惠券信息
            com.jiaoyi.common.ApiResponse<?> couponResponse = couponServiceClient.getCouponByCode(couponCode);
            if (couponResponse.getCode() != 200 || couponResponse.getData() == null) {
                throw new BusinessException("优惠券不存在或不可用: " + couponCode);
            }
            
            // 2. 解析优惠券对象
            @SuppressWarnings("unchecked")
            Map<String, Object> couponMap = (Map<String, Object>) objectMapper.convertValue(couponResponse.getData(), Map.class);
            Long couponId = couponMap.get("id") != null ? Long.parseLong(couponMap.get("id").toString()) : null;
            
            // 3. 验证优惠券
            com.jiaoyi.common.ApiResponse<Boolean> validateResponse = couponServiceClient.validateCoupon(
                    couponCode, userId, orderAmount);
            if (validateResponse.getCode() != 200 || !Boolean.TRUE.equals(validateResponse.getData())) {
                throw new BusinessException("优惠券验证失败: " + couponCode);
            }
            
            // 4. 计算优惠金额
            com.jiaoyi.common.ApiResponse<BigDecimal> calculateResponse = couponServiceClient.calculateDiscountAmount(
                    couponCode, orderAmount);
            if (calculateResponse.getCode() != 200 || calculateResponse.getData() == null) {
                throw new BusinessException("优惠金额计算失败: " + couponCode);
            }
            
            BigDecimal discountAmount = calculateResponse.getData();
            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("优惠金额为0或负数，优惠券代码: {}", couponCode);
                return null;
            }
            
            // 5. 创建订单优惠券关联记录
            OrderCoupon orderCoupon = new OrderCoupon();
            orderCoupon.setOrderId(null); // 稍后设置
            orderCoupon.setCouponId(couponId);
            orderCoupon.setCouponCode(couponCode);
            orderCoupon.setAppliedAmount(discountAmount);
            orderCoupon.setCreateTime(LocalDateTime.now());
            
            log.info("优惠券处理成功，优惠券代码: {}, ID: {}, 优惠金额: {}", couponCode, couponId, discountAmount);
            return orderCoupon;
            
        } catch (Exception e) {
            log.error("处理优惠券失败，优惠券代码: {}", couponCode, e);
            throw new BusinessException("优惠券处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新订单价格（包含优惠金额）
     * 注意：所有价格都由后端计算，不使用前端传来的值
     */
    private void updateOrderPriceWithDiscount(Order order, BigDecimal subtotal, BigDecimal discountAmount) {
        try {
            // 所有费用都由后端计算，不使用前端传来的值（为了安全）
            BigDecimal deliveryFee = feeCalculationService.calculateDeliveryFee(order, subtotal);
            BigDecimal taxTotal = feeCalculationService.calculateTax(order, subtotal, discountAmount);
            BigDecimal tips = BigDecimal.ZERO; // 小费通常由用户在前端选择，但这里暂时设为0
            BigDecimal charge = feeCalculationService.calculateOnlineServiceFee(order, subtotal); // 在线服务费

            // 使用PriceUtil统一精度
            subtotal = com.jiaoyi.order.util.PriceUtil.roundPrice(subtotal);
            discountAmount = com.jiaoyi.order.util.PriceUtil.roundPrice(discountAmount);
            deliveryFee = com.jiaoyi.order.util.PriceUtil.roundPrice(deliveryFee);
            taxTotal = com.jiaoyi.order.util.PriceUtil.roundPrice(taxTotal);
            tips = com.jiaoyi.order.util.PriceUtil.roundPrice(tips);
            charge = com.jiaoyi.order.util.PriceUtil.roundPrice(charge);

            // 计算总金额（小计 + 配送费 + 税费 + 小费 + 其他费用 - 优惠金额）
            BigDecimal total = com.jiaoyi.order.util.PriceUtil.add(subtotal, deliveryFee, taxTotal, tips, charge);
            total = com.jiaoyi.order.util.PriceUtil.subtract(total, discountAmount);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO; // 确保总金额不为负数
            }

            // 生成价格签名（防篡改）
            String priceSignature = com.jiaoyi.order.util.PriceSignatureUtil.generateSignature(
                    subtotal, discountAmount, deliveryFee, taxTotal, tips, total);

            // 构建订单价格JSON（所有值都由后端计算）
            Map<String, Object> orderPrice = new HashMap<>();
            orderPrice.put("subtotal", subtotal);
            orderPrice.put("discount", discountAmount);
            orderPrice.put("deliveryFee", deliveryFee);
            orderPrice.put("taxTotal", taxTotal);
            orderPrice.put("tips", tips);
            orderPrice.put("charge", charge);
            orderPrice.put("total", total);
            orderPrice.put("priceSignature", priceSignature); // 添加价格签名

            order.setOrderPrice(objectMapper.writeValueAsString(orderPrice));

            log.info("订单价格更新完成，小计: {}, 配送费: {}, 税费: {}, 在线服务费: {}, 优惠: {}, 总金额: {}, 签名: {}",
                    subtotal, deliveryFee, taxTotal, charge, discountAmount, total, priceSignature);

        } catch (Exception e) {
            log.error("更新订单价格失败，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 判断是否为 DoorDash 配送（根据商户配置）
     */
    private boolean isDoorDashDelivery(String merchantId) {
        // TODO: 从商户配置中读取是否使用 DoorDash
        // 暂时返回 false，实际实现时需要查询商户配置
        return true;
    }
    
    /**
     * 获取商户地址（用于 DoorDash 报价和创建配送）
     */
    private Map<String, Object> getMerchantAddress(String merchantId) {
        // TODO: 从商户信息中获取地址
        // 暂时返回 null，实际实现时需要查询商户信息
        Map<String, Object> address = new HashMap<>();
        address.put("street_address", ""); // 从商户信息获取
        address.put("city", "");
        address.put("state", "");
        address.put("zip_code", "");
        address.put("lat", 0.0);
        address.put("lng", 0.0);
        return address;
    }
    
    /**
     * 构建用户配送地址（用于 DoorDash 报价和创建配送）
     */
    private Map<String, Object> buildDropoffAddress(CalculatePriceRequest request) {
        Map<String, Object> address = new HashMap<>();
        
        // 解析地址字符串（格式：address1, city, state zipCode）
        String fullAddress = request.getReceiverAddress();
        if (fullAddress != null && !fullAddress.isEmpty()) {
            address.put("street_address", fullAddress);
        }
        
        // 如果有邮编
        if (request.getZipCode() != null && !request.getZipCode().isEmpty()) {
            address.put("zip_code", request.getZipCode());
        }
        
        // 如果有坐标
        if (request.getLatitude() != null && request.getLongitude() != null) {
            address.put("lat", request.getLatitude());
            address.put("lng", request.getLongitude());
        }
        
        return address;
    }
}


