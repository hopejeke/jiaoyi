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
import com.jiaoyi.order.mapper.OrderItemMapper;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.OrderCouponMapper;
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
    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final RedissonClient redissonClient;
    private final OrderTimeoutMessageService orderTimeoutMessageService;
    private final ObjectMapper objectMapper;
    private final FeeCalculationService feeCalculationService;
    private final DoorDashService doorDashService;
    private final DeliveryRuleService deliveryRuleService;

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

        // 2. 计算订单小计（从数据库查询商品价格）
        BigDecimal subtotal = BigDecimal.ZERO;
        for (com.jiaoyi.order.dto.CalculatePriceRequest.OrderItemRequest itemRequest : request.getOrderItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                continue;
            }

            // 从商品服务获取商品价格（使用 merchantId 和 productId，避免查询所有分片）
            log.debug("查询商品信息，商户ID: {}, 商品ID: {}", request.getMerchantId(), itemRequest.getProductId());
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
            
            // 检查 unitPrice 字段
            Object unitPriceObj = productMap.get("unitPrice");
            if (unitPriceObj == null) {
                log.warn("商品数据中缺少 unitPrice 字段，商户ID: {}, 商品ID: {}, 商品数据: {}", 
                        request.getMerchantId(), itemRequest.getProductId(), productMap);
                continue;
            }
            
            BigDecimal unitPrice = new BigDecimal(unitPriceObj.toString());
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("商品价格无效（<=0），商户ID: {}, 商品ID: {}, 价格: {}", 
                        request.getMerchantId(), itemRequest.getProductId(), unitPrice);
                continue;
            }
            
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            subtotal = subtotal.add(itemTotal);
            log.debug("商品价格计算成功，商户ID: {}, 商品ID: {}, 单价: {}, 数量: {}, 小计: {}", 
                    request.getMerchantId(), itemRequest.getProductId(), unitPrice, itemRequest.getQuantity(), itemTotal);
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
        
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(request.getOrderType());
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
        
        // 同一个用户同一时间只能下一个单
        String lockKey = "order:create:" + order.getUserId();
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取分布式锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取分布式锁失败，可能存在重复提交，用户ID: {}", order.getUserId());
                throw new BusinessException("请勿重复提交相同订单");
            }
            
            log.info("成功获取分布式锁，开始处理在线点餐订单创建");
            
            // 1. 检查并锁定库存（如果有 productId 或 saleItemId）
            List<Long> productIds = null;
            List<Integer> quantities = null;
            if (orderItems != null && !orderItems.isEmpty()) {
                productIds = orderItems.stream()
                        .filter(item -> item.getProductId() != null || item.getSaleItemId() != null)
                        .map(item -> item.getProductId() != null ? item.getProductId() : item.getSaleItemId())
                        .collect(Collectors.toList());
                quantities = orderItems.stream()
                        .map(OrderItem::getQuantity)
                        .collect(Collectors.toList());
                
                if (!productIds.isEmpty()) {
                    log.info("检查并锁定库存，商品数量: {}", productIds.size());
                    ProductServiceClient.LockStockBatchRequest lockRequest = new ProductServiceClient.LockStockBatchRequest();
                    lockRequest.setProductIds(productIds);
                    lockRequest.setQuantities(quantities);
                    try {
                        productServiceClient.lockStockBatch(lockRequest);
                    } catch (Exception e) {
                        log.error("锁定库存失败", e);
                        throw new BusinessException("库存不足或锁定失败: " + e.getMessage());
                    }
                }
            }
            
            try {
                // 2. 设置订单默认值
                if (order.getStatus() == null) {
                    order.setStatus(1); // 默认已下单
                }
                if (order.getLocalStatus() == null) {
                    order.setLocalStatus(1); // 默认已下单
                }
                if (order.getKitchenStatus() == null) {
                    order.setKitchenStatus(1); // 默认待送厨
                }
                order.setVersion(1L);
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
                
                // 7. 创建并保存订单项
                if (orderItems != null && !orderItems.isEmpty()) {
                    int itemIndex = 0;
                    for (OrderItem item : orderItems) {
                        item.setOrderId(order.getId());
                        item.setMerchantId(order.getMerchantId());
                        
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
                
                // 11. 发送订单超时延迟消息（30分钟后自动取消）
                orderTimeoutMessageService.sendOrderTimeoutMessage(insertedOrder.getId(), order.getUserId(), 30);
                log.info("订单超时延迟消息已发送，订单将在30分钟后自动取消（如果未支付）");
                
                return insertedOrder;
                
            } catch (Exception e) {
                // 如果订单创建失败，解锁已锁定的库存
                log.error("在线点餐订单创建失败，解锁库存", e);
                if (productIds != null && quantities != null && !productIds.isEmpty()) {
                    try {
                        ProductServiceClient.UnlockStockBatchRequest unlockRequest = new ProductServiceClient.UnlockStockBatchRequest();
                        unlockRequest.setProductIds(productIds);
                        unlockRequest.setQuantities(quantities);
                        unlockRequest.setOrderId(null);
                        productServiceClient.unlockStockBatch(unlockRequest);
                    } catch (Exception unlockException) {
                        log.error("解锁库存失败", unlockException);
                    }
                }
                throw e;
            }
            
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，用户ID: {}", order.getUserId(), e);
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

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
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByMerchantIdAndOrderId(merchantId, order.getId());
                    order.setOrderItems(orderItems);
                    return order;
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
     * 根据用户ID查询订单列表
     */
    public List<Order> getOrdersByUserId(Long userId) {
        log.info("查询用户订单列表，用户ID: {}", userId);
        List<Order> orders = orderMapper.selectByUserId(userId);
        return orders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return order;
                })
                .collect(Collectors.toList());
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
     */
    @Transactional
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
    
    public boolean cancelOrder(Long orderId) {
        log.info("取消订单，订单ID: {}", orderId);
        
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

        // 退款优惠券
        try {
            couponServiceClient.refundCouponByOrderId(orderId);
        } catch (Exception e) {
            log.error("退还优惠券失败，订单ID: {}", orderId, e);
            // 继续执行，不因为优惠券退还失败而阻止订单取消
        }

        // 更新订单状态为已取消
        Integer oldStatus = order.getStatus();
        boolean success = updateOrderStatus(orderId, com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode());
        
        if (success) {
            log.info("订单取消成功，订单ID: {}, 原状态: {}, 新状态: {}", 
                    orderId, oldStatus, com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode());
        } else {
            log.warn("订单取消失败，订单ID: {}", orderId);
        }
        
        return success;
    }


    /**
     * 处理库存状态变更（在线点餐：status 是 Integer）
     */
    private void handleInventoryStatusChange(Order order, Integer oldStatus, Integer newStatus) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }

        List<Long> productIds = order.getOrderItems().stream()
                .filter(item -> item.getProductId() != null || item.getSaleItemId() != null)
                .map(item -> item.getProductId() != null ? item.getProductId() : item.getSaleItemId())
                .collect(Collectors.toList());
        List<Integer> quantities = order.getOrderItems().stream()
                .map(OrderItem::getQuantity)
                .collect(Collectors.toList());

        // 支付成功（status = 100）：扣减库存
        if (oldStatus != null && oldStatus == 1 && newStatus != null && newStatus == 100) {
            log.info("订单支付成功，扣减库存，订单ID: {}", order.getId());
            ProductServiceClient.DeductStockBatchRequest deductRequest = new ProductServiceClient.DeductStockBatchRequest();
            deductRequest.setProductIds(productIds);
            deductRequest.setQuantities(quantities);
            deductRequest.setOrderId(order.getId());
            productServiceClient.deductStockBatch(deductRequest);
        }
            // 订单取消（status = -1）：解锁库存
        else if (newStatus != null && newStatus == -1) {
            log.info("订单取消，解锁库存，订单ID: {}", order.getId());
            ProductServiceClient.UnlockStockBatchRequest unlockRequest = new ProductServiceClient.UnlockStockBatchRequest();
            unlockRequest.setProductIds(productIds);
            unlockRequest.setQuantities(quantities);
            unlockRequest.setOrderId(order.getId());
            productServiceClient.unlockStockBatch(unlockRequest);
        }
    }
    
    /**
     * 计算订单小计（所有订单项的总价）
     */
    private BigDecimal calculateOrderSubtotal(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return orderItems.stream()
                .map(item -> {
                    BigDecimal itemPrice = item.getItemPrice() != null ? item.getItemPrice() : BigDecimal.ZERO;
                    Integer quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                    return itemPrice.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            
            // 计算总金额（小计 + 配送费 + 税费 + 小费 + 其他费用 - 优惠金额）
            BigDecimal total = subtotal.add(deliveryFee).add(taxTotal).add(tips).add(charge).subtract(discountAmount);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO; // 确保总金额不为负数
            }
            
            // 构建订单价格JSON（所有值都由后端计算）
            Map<String, Object> orderPrice = new HashMap<>();
            orderPrice.put("subtotal", subtotal);
            orderPrice.put("discount", discountAmount);
            orderPrice.put("deliveryFee", deliveryFee);
            orderPrice.put("taxTotal", taxTotal);
            orderPrice.put("tips", tips);
            orderPrice.put("charge", charge);
            orderPrice.put("total", total);
            
            order.setOrderPrice(objectMapper.writeValueAsString(orderPrice));
            
            log.info("订单价格更新完成，小计: {}, 配送费: {}, 税费: {}, 在线服务费: {}, 优惠: {}, 总金额: {}", 
                    subtotal, deliveryFee, taxTotal, charge, discountAmount, total);
            
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


