package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.dto.PaymentRequest;
import com.jiaoyi.order.dto.PaymentResponse;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.Payment;
import com.jiaoyi.order.enums.*;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.PaymentMapper;
import com.jiaoyi.order.mapper.PaymentCallbackLogMapper;
import com.jiaoyi.order.mapper.DeliveryMapper;
import com.jiaoyi.order.entity.Delivery;
import com.jiaoyi.order.entity.PaymentCallbackLog;
import com.jiaoyi.order.config.StripeConfig;
import com.jiaoyi.order.entity.MerchantStripeConfig;
import com.jiaoyi.order.mapper.MerchantStripeConfigMapper;
import com.jiaoyi.order.service.AlipayService;
import com.jiaoyi.order.service.StripeService;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.util.OrderPriceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支付服务
 * 参照 OO 项目的支付实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    // 支付状态常量（已废弃，使用 PaymentStatusEnum）
    @Deprecated
    public static final int PAYMENT_STATUS_SUCCESS = PaymentStatusEnum.SUCCESS.getCode();
    @Deprecated
    public static final int PAYMENT_STATUS_FAILED = PaymentStatusEnum.FAILED.getCode();
    @Deprecated
    public static final int PAYMENT_STATUS_PENDING = PaymentStatusEnum.PENDING.getCode();
    
    // 支付类型常量（已废弃，使用 PaymentTypeEnum）
    @Deprecated
    public static final int PAYMENT_TYPE_CHARGE = PaymentTypeEnum.CHARGE.getCode();
    @Deprecated
    public static final int PAYMENT_TYPE_REFUND = PaymentTypeEnum.REFUND.getCode();
    
    // 支付方式类别常量（已废弃，使用 PaymentCategoryEnum）
    @Deprecated
    public static final int PAYMENT_CATEGORY_CREDIT_CARD = PaymentCategoryEnum.CREDIT_CARD.getCode();
    @Deprecated
    public static final int PAYMENT_CATEGORY_CASH = PaymentCategoryEnum.CASH.getCode();
    @Deprecated
    public static final int PAYMENT_CATEGORY_WECHAT_PAY = PaymentCategoryEnum.WECHAT_PAY.getCode();
    @Deprecated
    public static final int PAYMENT_CATEGORY_ALIPAY = PaymentCategoryEnum.ALIPAY.getCode();
    
    // 支付服务常量
    public static final String PAYMENT_SERVICE_ALIPAY = "ALIPAY";
    public static final String PAYMENT_SERVICE_WECHAT_PAY = "WECHAT_PAY";
    public static final String PAYMENT_SERVICE_CASH = "CASH";
    
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentCallbackLogMapper paymentCallbackLogMapper;
    private final DeliveryMapper deliveryMapper;
    private final AlipayService alipayService;
    private final StripeService stripeService;
    private final ObjectMapper objectMapper;
    private final MerchantStripeConfigMapper merchantStripeConfigMapper;
    private final DoorDashService doorDashService;
    private final StripeConfig stripeConfig;
    private final ProductServiceClient productServiceClient;
    private final DoorDashRetryService doorDashRetryService;
    private final RedissonClient redissonClient;
    
    @Value("${order.timeout.minutes:40}")
    private int orderTimeoutMinutes;
    
    /**
     * 处理支付（参照 OO 项目的支付流程）
     * 
     * 并发控制：
     * 1. 使用分布式锁（基于 orderId）防止并发创建支付记录
     * 2. 双重检查：获取锁后再次检查是否已有待支付记录
     * 3. 数据库唯一索引 uk_order_type_pending 作为最后一道防线
     */
    @Transactional
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        log.info("处理支付，订单ID: {}, 支付方式: {}", orderId, request.getPaymentMethod());
        
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        
        // 2. 检查订单状态（在线点餐：PENDING 表示已下单待支付）
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            if (OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
                log.warn("订单已支付，订单ID: {}", orderId);
                throw new RuntimeException("订单已支付，请勿重复支付");
            }
            throw new RuntimeException("订单状态不正确，无法支付");
        }
        
        // 3. 验证支付金额（从 orderPrice JSON 中解析）
        BigDecimal expectedAmount = com.jiaoyi.order.util.OrderPriceUtil.parseOrderTotal(order);
        if (expectedAmount.compareTo(request.getAmount()) != 0) {
            log.error("支付金额不匹配，订单ID: {}, 期望金额: {}, 实际金额: {}", orderId, expectedAmount, request.getAmount());
            throw new RuntimeException("支付金额不匹配");
        }

        // 4. 验证价格签名（防篡改）
        if (order.getOrderPrice() != null && !order.getOrderPrice().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> priceMap = mapper.readValue(order.getOrderPrice(), java.util.Map.class);

                String signature = priceMap.get("priceSignature") != null ?
                        priceMap.get("priceSignature").toString() : null;

                if (signature != null && !signature.isEmpty()) {
                    boolean verified = com.jiaoyi.order.util.PriceSignatureUtil.verifySignature(
                            order.getOrderPrice(), signature);

                    if (!verified) {
                        log.error("价格签名验证失败，订单ID: {}, 价格可能被篡改", orderId);
                        throw new RuntimeException("价格签名验证失败，请重新下单");
                    }
                    log.info("价格签名验证通过，订单ID: {}", orderId);
                } else {
                    log.warn("订单价格缺少签名，订单ID: {} (可能是旧订单)", orderId);
                }
            } catch (RuntimeException e) {
                throw e; // 重新抛出业务异常
            } catch (Exception e) {
                log.error("验证价格签名时发生异常，订单ID: {}", orderId, e);
                // 签名验证异常不阻塞支付流程（向后兼容）
            }
        }
        
        // ========== 并发控制：分布式锁 ==========
        String lockKey = com.jiaoyi.order.constants.OrderConstants.PAYMENT_CREATE_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，使用配置的等待时间和持有时间
            boolean lockAcquired = lock.tryLock(
                com.jiaoyi.order.constants.OrderConstants.PAYMENT_LOCK_WAIT_SECONDS,
                com.jiaoyi.order.constants.OrderConstants.PAYMENT_LOCK_LEASE_SECONDS,
                TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取支付创建锁失败，订单可能正在被其他请求处理，订单ID: {}", orderId);
                throw new BusinessException("支付请求正在处理中，请勿重复提交");
            }
            
            try {
                // ========== 双重检查：获取锁后再次检查是否已有待支付记录 ==========
                Payment existingPayment = paymentMapper.selectByOrderId(orderId).stream()
                        .filter(p -> p.getType() != null && p.getType().equals(PaymentTypeEnum.CHARGE.getCode()) && 
                                     p.getStatus() != null && p.getStatus().equals(PaymentStatusEnum.PENDING.getCode()))
                        .findFirst()
                        .orElse(null);
                
                if (existingPayment != null) {
                    log.info("订单已有待支付记录（双重检查），返回已有支付信息，订单ID: {}, 支付ID: {}", 
                            orderId, existingPayment.getId());
                    return convertPaymentToResponse(existingPayment);
                }
                
                // 5. 创建支付记录
                Payment payment = createPaymentRecord(order, request, expectedAmount);
                
                // 6. 调用第三方支付平台
                PaymentResponse paymentResponse = callThirdPartyPayment(order, request, payment);
                
                // 8. 更新支付记录
                if (paymentResponse.getThirdPartyTradeNo() != null) {
                    payment.setThirdPartyTradeNo(paymentResponse.getThirdPartyTradeNo());
                }
                if (paymentResponse.getQrCode() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> extra = Map.of("qrCode", paymentResponse.getQrCode());
                        payment.setExtra(objectMapper.writeValueAsString(extra));
                    } catch (Exception e) {
                        log.warn("保存二维码信息失败", e);
                    }
                }
                
                // 更新支付状态和第三方交易号
                paymentMapper.updateStatus(payment.getId(), payment.getStatus(), payment.getThirdPartyTradeNo());
                
                // 如果是信用卡支付，更新 Payment Intent ID
                if ("CREDIT_CARD".equalsIgnoreCase(request.getPaymentMethod()) ||
                    "CARD".equalsIgnoreCase(request.getPaymentMethod()) ||
                    "STRIPE".equalsIgnoreCase(request.getPaymentMethod())) {
                    if (payment.getStripePaymentIntentId() != null && !payment.getStripePaymentIntentId().isEmpty()) {
                        paymentMapper.updatePaymentIntentId(payment.getId(), payment.getStripePaymentIntentId());
                        log.info("已更新 Payment Intent ID，支付ID: {}, Payment Intent ID: {}", 
                                payment.getId(), payment.getStripePaymentIntentId());
                    } else {
                        log.warn("Payment Intent ID 为空，无法更新，支付ID: {}", payment.getId());
                    }
                }
                
                // 9. 对于同步支付（现金），立即更新订单状态
                // 对于异步支付（Stripe、支付宝），订单状态在 Webhook 回调中更新
                if ("CASH".equalsIgnoreCase(request.getPaymentMethod())) {
                    // 现金支付是同步的，立即更新订单状态
                    int orderUpdated = orderMapper.updateStatusIfPending(
                            orderId, 
                            OrderStatusEnum.PENDING.getCode(), 
                            OrderStatusEnum.PAID.getCode()
                    );
                    if (orderUpdated > 0) {
                        log.info("现金支付成功，已更新订单状态为已支付，订单ID: {}", orderId);
                    } else {
                        log.warn("现金支付成功，但订单状态更新失败（可能已被其他线程处理），订单ID: {}", orderId);
                    }
                }
                // 注意：Stripe 和支付宝是异步支付，订单状态在 Webhook 回调中更新（handlePaymentSuccess 方法）
                
                return paymentResponse;
                
            } finally {
                // 释放锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("释放支付创建锁，订单ID: {}", orderId);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取支付创建锁被中断，订单ID: {}", orderId, e);
            throw new BusinessException("系统繁忙，请稍后重试");
        }
    }
    
    /**
     * 调用第三方支付平台
     */
    private PaymentResponse callThirdPartyPayment(Order order, PaymentRequest request, Payment payment) {
        log.info("调用第三方支付平台，订单ID: {}, 支付方式: {}", order.getId(), request.getPaymentMethod());
        
        // 根据支付方式调用不同的支付服务
        return switch (request.getPaymentMethod().toUpperCase()) {
            case "ALIPAY" -> callAlipayPayment(order, request, payment);
            case "WECHAT", "WECHAT_PAY" -> callWechatPayment(order, request, payment);
            case "CREDIT_CARD", "CARD", "STRIPE" -> callCreditCardPayment(order, request, payment);
            case "CASH" -> callCashPayment(order, request, payment);
            default -> throw new RuntimeException("不支持的支付方式: " + request.getPaymentMethod());
        };
    }
    
    /**
     * 调用支付宝支付
     */
    private PaymentResponse callAlipayPayment(Order order, PaymentRequest request, Payment payment) {
        log.info("调用支付宝支付，订单ID: {}", order.getId());
        
        try {
            // 直接使用请求中的金额（已经是元为单位）
            BigDecimal amount = request.getAmount();
            
            // 调用支付宝服务（使用订单ID作为订单号）
            PaymentResponse response = alipayService.createPayment(
                String.valueOf(order.getId()),
                "订单支付：" + order.getId(),
                amount,
                String.valueOf(payment.getId())
            );
            
            // 设置支付ID
            response.setPaymentId(payment.getId());
            
            // 更新支付记录状态
            payment.setStatus(PaymentStatusEnum.PENDING.getCode());
            payment.setPaymentService(PaymentServiceEnum.ALIPAY);
            payment.setCategory(PaymentCategoryEnum.ALIPAY.getCode());
            
            return response;
        } catch (Exception e) {
            log.error("调用支付宝支付失败，订单ID: {}, 错误: {}", order.getId(), e.getMessage(), e);
            // 更新支付记录状态为失败
            payment.setStatus(PaymentStatusEnum.FAILED.getCode());
            payment.setPaymentService(PaymentServiceEnum.ALIPAY);
            payment.setCategory(PaymentCategoryEnum.ALIPAY.getCode());
            // 重新抛出异常，让上层处理
            throw new BusinessException("支付宝支付创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用微信支付
     */
    private PaymentResponse callWechatPayment(Order order, PaymentRequest request, Payment payment) {
        log.info("调用微信支付，订单ID: {}", order.getId());
        
        // TODO: 集成微信支付
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setStatus("PENDING");
        response.setPaymentMethod("WECHAT_PAY");
        response.setAmount(request.getAmount());
        response.setRemark("微信支付功能待开发");
        
        // 更新支付记录状态
        payment.setStatus(PaymentStatusEnum.PENDING.getCode());
        payment.setPaymentService(PaymentServiceEnum.WECHAT_PAY);
        payment.setCategory(PaymentCategoryEnum.WECHAT_PAY.getCode());
        
        return response;
    }
    
    /**
     * 调用信用卡支付（Stripe）
     * 参照 OO 项目的 stripePayWithPaymentService
     */
    private PaymentResponse callCreditCardPayment(Order order, PaymentRequest request, Payment payment) {
        log.info("调用信用卡支付（Stripe），订单ID: {}", order.getId());
        
        try {
            // 从请求中获取支付方式ID（如果用户保存了卡片）或测试卡号信息
            String paymentMethodId = null;
            if (request.getPaymentInfo() != null && !request.getPaymentInfo().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paymentInfoMap = objectMapper.readValue(request.getPaymentInfo(), Map.class);
                    if (paymentInfoMap.containsKey("cardInfo")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cardInfo = (Map<String, Object>) paymentInfoMap.get("cardInfo");
                        
                        // 优先使用已有的 paymentMethodId
                        if (cardInfo.containsKey("paymentMethodId")) {
                            paymentMethodId = cardInfo.get("paymentMethodId").toString();
                            log.info("使用已保存的 Payment Method ID: {}", paymentMethodId);
                        } 
                        // 如果没有 paymentMethodId，但有测试卡号信息，则创建 Payment Method
                        else if (cardInfo.containsKey("testCard")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> testCard = (Map<String, Object>) cardInfo.get("testCard");
                            
                            String cardNumber = testCard.get("number") != null ? testCard.get("number").toString() : null;
                            Integer expMonth = testCard.get("expMonth") != null ? 
                                    Integer.parseInt(testCard.get("expMonth").toString()) : null;
                            Integer expYear = testCard.get("expYear") != null ? 
                                    Integer.parseInt(testCard.get("expYear").toString()) : null;
                            String cvc = testCard.get("cvc") != null ? testCard.get("cvc").toString() : null;
                            String postalCode = testCard.get("postalCode") != null ? 
                                    testCard.get("postalCode").toString() : "12345";
                            String cardholderName = testCard.get("cardholderName") != null ? 
                                    testCard.get("cardholderName").toString() : "Test User";
                            
                            if (cardNumber != null && expMonth != null && expYear != null && cvc != null) {
                                log.info("检测到测试卡号信息，自动创建 Payment Method");
                                paymentMethodId = stripeService.createPaymentMethodFromCard(
                                        cardNumber, expMonth, expYear, cvc, postalCode, cardholderName
                                );
                                log.info("测试 Payment Method 创建成功，ID: {}", paymentMethodId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 paymentInfo 失败", e);
                }
            }
            
            // 构建元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", String.valueOf(order.getId()));
            metadata.put("merchantId", order.getMerchantId());
            metadata.put("userId", order.getUserId() != null ? String.valueOf(order.getUserId()) : "");
            
            // 获取商户 Stripe 配置（支持 Stripe Connect）
            MerchantStripeConfig merchantConfig = merchantStripeConfigMapper.selectByMerchantId(order.getMerchantId());
            String onBehalfOf = null;
            Long applicationFeeAmount = null;
            String currency = "usd";
            
            if (merchantConfig != null && merchantConfig.getEnabled() != null && merchantConfig.getEnabled() 
                    && merchantConfig.getStripeAccountId() != null && !merchantConfig.getStripeAccountId().isEmpty()) {
                // 使用 Stripe Connect
                onBehalfOf = merchantConfig.getStripeAccountId();
                currency = merchantConfig.getCurrency() != null ? merchantConfig.getCurrency().toLowerCase() : "usd";
                
                // 计算平台手续费
                applicationFeeAmount = calculateApplicationFee(
                        request.getAmount(),
                        merchantConfig,
                        paymentMethodId // 可以根据卡片类型判断是否是 Amex
                );
                
                log.info("使用 Stripe Connect，商户账户: {}, 平台手续费: {} 分", onBehalfOf, applicationFeeAmount);
            }
            
            // 创建 Payment Intent
            PaymentResponse response = stripeService.createPaymentIntent(
                    order.getId(),
                    request.getAmount(),
                    currency,
                    paymentMethodId,
                    metadata,
                    onBehalfOf,
                    applicationFeeAmount
            );
            
            // 从 response 中提取 Payment Intent 信息
            String paymentIntentId = response.getPaymentIntentId();
            
            // 设置支付ID
            response.setPaymentId(payment.getId());
            
            // 更新支付记录
            payment.setStatus(PaymentStatusEnum.PENDING.getCode());
            payment.setPaymentService(PaymentServiceEnum.STRIPE);
            payment.setCategory(PaymentCategoryEnum.CREDIT_CARD.getCode());
            if (paymentIntentId != null) {
                payment.setStripePaymentIntentId(paymentIntentId);
            }
            
            // 保存 Payment Intent 信息到 extra
            try {
                Map<String, Object> extra = new HashMap<>();
                if (paymentIntentId != null) {
                    extra.put("paymentIntentId", paymentIntentId);
                }
                if (response.getClientSecret() != null) {
                    extra.put("clientSecret", response.getClientSecret());
                }
                extra.put("status", "requires_payment_method");
                payment.setExtra(objectMapper.writeValueAsString(extra));
            } catch (Exception e) {
                log.warn("保存 Payment Intent 信息失败", e);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("信用卡支付创建失败，订单ID: {}", order.getId(), e);
            throw new RuntimeException("信用卡支付创建失败: " + e.getMessage());
        }
    }
    
    
    /**
     * 调用现金支付
     */
    private PaymentResponse callCashPayment(Order order, PaymentRequest request, Payment payment) {
        log.info("调用现金支付，订单ID: {}", order.getId());
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setStatus("SUCCESS");
        response.setPaymentMethod("CASH");
        response.setAmount(request.getAmount());
        response.setRemark("现金支付");
        
        // 现金支付直接成功
        payment.setStatus(PaymentStatusEnum.SUCCESS.getCode());
        payment.setPaymentService(PaymentServiceEnum.CASH);
        payment.setCategory(PaymentCategoryEnum.CASH.getCode());
        
        return response;
    }
    
    /**
     * 创建支付记录
     */
    private Payment createPaymentRecord(Order order, PaymentRequest request, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setMerchantId(order.getMerchantId());
        payment.setStoreId(order.getStoreId()); // 设置 storeId（用于分片）
        // 计算并设置 shardId（基于 storeId，与订单保持一致）
        if (order.getStoreId() != null) {
            int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(order.getStoreId());
            payment.setShardId(shardId);
        } else if (order.getShardId() != null) {
            payment.setShardId(order.getShardId());
        } else {
            throw new IllegalStateException("无法获取 storeId 或 shardId，无法创建支付记录");
        }
        payment.setStatus(PaymentStatusEnum.PENDING.getCode());
        payment.setType(PaymentTypeEnum.CHARGE.getCode());
        payment.setAmount(amount);
        payment.setOrderPrice(order.getOrderPrice());
        // 生成支付流水号（格式：PAY_{订单ID}_{时间戳}）
        String paymentNo = "PAY_" + order.getId() + "_" + System.currentTimeMillis();
        payment.setPaymentNo(paymentNo);
        payment.setCreateTime(LocalDateTime.now());
        payment.setUpdateTime(LocalDateTime.now());
        payment.setVersion(1);
        
        // 设置支付方式类别
        String paymentMethod = request.getPaymentMethod().toUpperCase();
        if ("ALIPAY".equals(paymentMethod)) {
            payment.setCategory(PaymentCategoryEnum.ALIPAY.getCode());
            payment.setPaymentService(PaymentServiceEnum.ALIPAY);
        } else if ("WECHAT".equals(paymentMethod) || "WECHAT_PAY".equals(paymentMethod)) {
            payment.setCategory(PaymentCategoryEnum.WECHAT_PAY.getCode());
            payment.setPaymentService(PaymentServiceEnum.WECHAT_PAY);
        } else if ("CREDIT_CARD".equals(paymentMethod) || "CARD".equals(paymentMethod) || "STRIPE".equals(paymentMethod)) {
            payment.setCategory(PaymentCategoryEnum.CREDIT_CARD.getCode());
            payment.setPaymentService(PaymentServiceEnum.STRIPE);
        } else if ("CASH".equals(paymentMethod)) {
            payment.setCategory(PaymentCategoryEnum.CASH.getCode());
            payment.setPaymentService(PaymentServiceEnum.CASH);
        } else {
            // 默认值，避免 null
            log.warn("未知的支付方式: {}，使用默认值", paymentMethod);
            payment.setCategory(PaymentCategoryEnum.CREDIT_CARD.getCode());
            payment.setPaymentService(PaymentServiceEnum.STRIPE);
        }
        
        paymentMapper.insert(payment);
        log.info("创建支付记录成功，支付ID: {}", payment.getId());
        return payment;
    }
    
    /**
     * 处理支付成功回调（参照 OO 项目的支付回调处理）
     * 
     * @param orderIdStr 订单ID（字符串）
     * @param thirdPartyTradeNo 第三方交易号
     * @return true 如果支付成功处理完成，false 如果订单超时已退款
     */
    @Transactional
    public boolean handlePaymentSuccess(String orderIdStr, String thirdPartyTradeNo) {
        log.info("处理支付成功回调，订单ID: {}, 第三方交易号: {}", orderIdStr, thirdPartyTradeNo);
        
        // ========== 幂等性检查：先检查回调日志表（基于 thirdPartyTradeNo 去重） ==========
        PaymentCallbackLog existingLog = null;
        if (thirdPartyTradeNo != null && !thirdPartyTradeNo.isEmpty()) {
            existingLog = paymentCallbackLogMapper.selectByThirdPartyTradeNo(thirdPartyTradeNo);
            if (existingLog != null) {
                // 如果已处理成功，直接返回
                if (PaymentCallbackLogStatusEnum.SUCCESS.equals(existingLog.getStatus())) {
                    log.info("支付回调已处理（幂等性检查），第三方交易号: {}, 订单ID: {}, 处理时间: {}", 
                            thirdPartyTradeNo, existingLog.getOrderId(), existingLog.getProcessedAt());
                    return true;
                }
                // 如果正在处理中，等待或返回（避免并发处理）
                if (PaymentCallbackLogStatusEnum.PROCESSING.equals(existingLog.getStatus())) {
                    log.warn("支付回调正在处理中（可能并发调用），第三方交易号: {}, 订单ID: {}", 
                            thirdPartyTradeNo, existingLog.getOrderId());
                    // 可以选择等待或直接返回 false，让调用方重试
                    return false;
                }
                // 如果之前处理失败，可以重试
                log.info("支付回调之前处理失败，将重试，第三方交易号: {}, 订单ID: {}, 错误: {}", 
                        thirdPartyTradeNo, existingLog.getOrderId(), existingLog.getErrorMessage());
            }
        }
        
        // 创建或更新回调日志（标记为处理中）
        PaymentCallbackLog callbackLog = null;
        Long orderId = null;
        try {
            orderId = Long.parseLong(orderIdStr);
        } catch (NumberFormatException e) {
            log.error("订单ID格式错误: {}", orderIdStr, e);
            return false;
        }
        
        // 先查询订单和支付记录，以便在创建回调日志时设置 paymentService
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，订单ID: {}", orderId);
            return false;
        }
        
        // 查询支付记录（幂等性检查）
        Payment payment = paymentMapper.selectByOrderId(orderId).stream()
                .filter(p -> p.getType() != null && p.getType().equals(PaymentTypeEnum.CHARGE.getCode()))
                .findFirst()
                .orElse(null);
        
        // 确定支付服务类型：优先从 Payment 记录获取，如果没有则根据 thirdPartyTradeNo 判断（Stripe 的 paymentIntentId）
        PaymentServiceEnum paymentService = null;
        if (payment != null && payment.getPaymentService() != null) {
            paymentService = payment.getPaymentService();
        } else {
            // 如果没有 Payment 记录，根据 thirdPartyTradeNo 判断（Stripe 的 paymentIntentId 通常以 pi_ 开头）
            if (thirdPartyTradeNo != null && thirdPartyTradeNo.startsWith("pi_")) {
                paymentService = PaymentServiceEnum.STRIPE;
            } else {
                // 默认使用 STRIPE（因为当前主要使用 Stripe）
                paymentService = PaymentServiceEnum.STRIPE;
                log.warn("无法确定支付服务类型，使用默认值 STRIPE，订单ID: {}, thirdPartyTradeNo: {}", orderId, thirdPartyTradeNo);
            }
        }
        
        if (existingLog != null) {
            callbackLog = existingLog;
            // 更新状态为处理中，并更新支付ID和支付服务（如果之前没有设置）
            if (callbackLog.getPaymentId() == null && payment != null) {
                callbackLog.setPaymentId(payment.getId());
            }
            if (callbackLog.getPaymentService() == null) {
                callbackLog.setPaymentService(paymentService);
            }
            paymentCallbackLogMapper.updateStatus(
                    callbackLog.getId(), 
                    PaymentCallbackLogStatusEnum.PROCESSING, 
                    null, 
                    null
            );
        } else {
            // 创建新的回调日志
            callbackLog = new PaymentCallbackLog();
            callbackLog.setOrderId(orderId);
            callbackLog.setThirdPartyTradeNo(thirdPartyTradeNo);
            callbackLog.setStatus(PaymentCallbackLogStatusEnum.PROCESSING);
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setPaymentService(paymentService); // 设置支付服务类型
            if (payment != null) {
                callbackLog.setPaymentId(payment.getId());
            }
            // 保存回调数据（用于审计）
            try {
                Map<String, Object> callbackData = new HashMap<>();
                callbackData.put("orderId", orderIdStr);
                callbackData.put("thirdPartyTradeNo", thirdPartyTradeNo);
                callbackData.put("timestamp", LocalDateTime.now().toString());
                callbackLog.setCallbackData(objectMapper.writeValueAsString(callbackData));
            } catch (Exception e) {
                log.warn("序列化回调数据失败，但不影响主流程", e);
            }
            try {
                paymentCallbackLogMapper.insert(callbackLog);
            } catch (Exception e) {
                // 如果插入失败（可能是并发插入导致唯一键冲突），查询已存在的记录
                log.warn("插入回调日志失败（可能是并发插入），第三方交易号: {}, 错误: {}", thirdPartyTradeNo, e.getMessage());
                existingLog = paymentCallbackLogMapper.selectByThirdPartyTradeNo(thirdPartyTradeNo);
                if (existingLog != null && PaymentCallbackLogStatusEnum.SUCCESS.equals(existingLog.getStatus())) {
                    log.info("并发插入时发现已处理成功的记录，第三方交易号: {}", thirdPartyTradeNo);
                    return true;
                }
                // 如果插入失败且没有已存在的记录，继续处理（可能是数据库问题）
                callbackLog = null;
            }
        }
        
        try {
            if (payment == null) {
                log.warn("支付记录不存在，订单ID: {}", orderId);
                if (callbackLog != null) {
                    paymentCallbackLogMapper.updateStatus(
                            callbackLog.getId(), 
                            PaymentCallbackLogStatusEnum.FAILED, 
                            null, 
                            "支付记录不存在"
                    );
                }
                return false;
            }
            
            // 3. 检查是否已处理（幂等性）
            // 如果支付已成功，检查订单状态是否也需要更新
            boolean paymentAlreadySuccess = payment.getStatus() != null && 
                    payment.getStatus().equals(PaymentStatusEnum.SUCCESS.getCode());
            
            if (paymentAlreadySuccess) {
                // 支付已成功，检查订单状态
                Integer currentOrderStatus = order.getStatus() instanceof Integer ? 
                    (Integer) order.getStatus() : null;
                
                // 如果订单状态还是"已下单"（PENDING），说明之前的状态更新失败了，需要重新更新
                if (OrderStatusEnum.PENDING.getCode().equals(currentOrderStatus)) {
                    log.warn("支付已成功，但订单状态仍是'已下单'，重新更新订单状态，订单ID: {}, 支付ID: {}", orderId, payment.getId());
                    // 跳过支付状态更新（已经是 SUCCESS），直接更新订单状态
                } else if (OrderStatusEnum.PAID.getCode().equals(currentOrderStatus) || 
                          OrderStatusEnum.PREPARING.getCode().equals(currentOrderStatus) ||
                          OrderStatusEnum.WAITING_ACCEPT.getCode().equals(currentOrderStatus)) {
                    // 订单状态已经是支付后的状态，说明已处理完成
                    log.info("支付已处理，订单ID: {}, 支付ID: {}, 当前订单状态: {}", orderId, payment.getId(), currentOrderStatus);
                    return true; // 已处理，返回 true
                } else {
                    // 订单状态异常，记录日志但继续处理
                    log.warn("支付已成功，但订单状态异常，订单ID: {}, 支付ID: {}, 当前订单状态: {}", 
                            orderId, payment.getId(), currentOrderStatus);
                }
            }
            
            // 3.5. 检查订单是否已超时（参照 OO 项目的超时退款逻辑）
            if (isOrderTimeout(order)) {
                log.warn("订单已超时，但收到支付成功回调，订单ID: {}, 创建时间: {}, 超时时间: {} 分钟", 
                        orderId, order.getCreateTime(), orderTimeoutMinutes);
                
                // 自动退款
                processTimeoutRefund(order, payment, thirdPartyTradeNo);
                return false; // 订单超时已退款，返回 false，不继续处理
            }
            
            // 4. 原子更新支付状态（如果还没有更新）
            if (!paymentAlreadySuccess) {
                int updated = paymentMapper.updateStatusIfPending(
                        payment.getId(),
                        PaymentStatusEnum.PENDING.getCode(),
                        PaymentStatusEnum.SUCCESS.getCode(),
                        thirdPartyTradeNo
                );
                
                if (updated == 0) {
                    log.warn("支付状态更新失败（可能已被其他线程处理），订单ID: {}, 支付ID: {}", orderId, payment.getId());
                    return false;
                }
            }
            
            // 5. 更新订单状态为"已支付"（原子操作）
            int orderUpdated = orderMapper.updateStatusIfPending(
                    orderId, 
                    OrderStatusEnum.PENDING.getCode(), 
                    OrderStatusEnum.PAID.getCode()
            );
            if (orderUpdated == 0) {
                log.warn("订单状态更新失败（可能已被其他线程处理或状态不正确），订单ID: {}", orderId);
                return false;
            }
            
            // 5.5. 根据商户自动接单配置，决定下一步状态
            // 如果是自动接单，立即更新为"制作中"；如果不是，更新为"待接单"
            boolean autoAccept = checkMerchantAutoAccept(order.getMerchantId(), order.getOrderType());
            Integer nextStatus;
            if (autoAccept) {
                // 自动接单：立即更新为"制作中"
                nextStatus = OrderStatusEnum.PREPARING.getCode();
                log.info("商户已开启自动接单，支付成功后立即更新为制作中，订单ID: {}, 商户ID: {}", orderId, order.getMerchantId());
            } else {
                // 非自动接单：更新为"待接单"
                nextStatus = OrderStatusEnum.WAITING_ACCEPT.getCode();
                log.info("商户未开启自动接单，支付成功后更新为待接单，订单ID: {}, 商户ID: {}", orderId, order.getMerchantId());
            }
            
            // 原子更新订单状态（从"已支付"更新为下一步状态）
            int nextStatusUpdated = orderMapper.updateStatusIfPending(
                    orderId,
                    OrderStatusEnum.PAID.getCode(),
                    nextStatus
            );
            if (nextStatusUpdated == 0) {
                log.warn("订单状态更新失败（可能已被其他线程处理），订单ID: {}, 目标状态: {}", orderId, nextStatus);
                // 不返回 false，因为支付已成功，状态更新失败可以后续重试
            }
            
            // 6. 如果是 DoorDash 配送订单，创建配送订单
            // 注意：即使没有 deliveryFeeQuoted，只要是 DELIVERY 订单就尝试创建
            // 因为 deliveryFeeQuoted 可能在创建订单时没有保存（比如地址信息不完整时使用了本地计算）
            if (OrderTypeEnum.DELIVERY.equals(order.getOrderType())) {
                try {
                    // 检查是否应该使用 DoorDash（根据商户配置）
                    boolean useDoorDash = isDoorDashDelivery(order.getMerchantId());
                    if (useDoorDash) {
                        createDoorDashDelivery(order);
            } else {
                        log.info("订单不是 DoorDash 配送订单，跳过创建 DoorDash 配送，订单ID: {}", orderId);
                    }
                } catch (Exception e) {
                    log.error("创建 DoorDash 配送订单失败，订单ID: {}", orderId, e);
                    // 不抛出异常，支付已成功，配送订单创建失败可以后续重试
                    // 记录到重试任务表，由定时任务自动重试
                    try {
                        doorDashRetryService.createRetryTask(orderId, order.getMerchantId(), payment.getId(), e);
                    } catch (Exception retryTaskException) {
                        log.error("创建 DoorDash 重试任务失败，订单ID: {}", orderId, retryTaskException);
                    }
                }
            }
            
            // 7. 更新回调日志为成功
            if (callbackLog != null) {
                try {
                    Map<String, Object> result = new HashMap<>();
                    result.put("orderId", orderId);
                    result.put("paymentId", payment.getId());
                    result.put("orderStatus", order.getStatus());
                    result.put("processedAt", LocalDateTime.now().toString());
                    
                    paymentCallbackLogMapper.updateStatus(
                            callbackLog.getId(), 
                            PaymentCallbackLogStatusEnum.SUCCESS, 
                            objectMapper.writeValueAsString(result), 
                            null
                    );
                } catch (Exception e) {
                    log.warn("更新回调日志失败，但不影响主流程，订单ID: {}", orderId, e);
                }
            }
            
            log.info("支付成功处理完成，订单ID: {}, 支付ID: {}, 第三方交易号: {}", orderId, payment.getId(), thirdPartyTradeNo);
            return true; // 支付成功处理完成
            
        } catch (Exception e) {
            log.error("处理支付成功回调异常，订单ID: {}", orderIdStr, e);
            
            // 更新回调日志为失败
            if (callbackLog != null) {
                try {
                    paymentCallbackLogMapper.updateStatus(
                            callbackLog.getId(), 
                            PaymentCallbackLogStatusEnum.FAILED, 
                            null, 
                            e.getMessage()
                    );
                } catch (Exception logError) {
                    log.warn("更新回调日志失败，订单ID: {}", orderIdStr, logError);
                }
            }
            
            throw new RuntimeException("处理支付成功失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查商户是否开启自动接单
     * @param merchantId 商户ID
     * @param orderType 订单类型（DELIVERY/PICKUP/SELF_DINE_IN）
     * @return true 如果开启自动接单，false 如果未开启（默认返回 true，即默认自动接单）
     */
    public boolean checkMerchantAutoAccept(String merchantId, OrderTypeEnum orderType) {
        try {
            // 从商品服务获取商户信息
            com.jiaoyi.common.ApiResponse<?> merchantResponse = productServiceClient.getMerchant(merchantId);
            
            if (merchantResponse.getCode() != 200 || merchantResponse.getData() == null) {
                log.warn("获取商户信息失败，默认自动接单，商户ID: {}, 响应码: {}", merchantId, merchantResponse.getCode());
                return true; // 默认自动接单
            }
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> merchantMap = (java.util.Map<String, Object>) merchantResponse.getData();
            
            // 根据订单类型判断使用哪个自动接单配置
            Boolean enableAutoSend = null;
            if (OrderTypeEnum.DELIVERY.equals(orderType) || OrderTypeEnum.PICKUP.equals(orderType)) {
                // 配送/自取订单：使用 enableAutoSend
                Object enableAutoSendObj = merchantMap.get("enableAutoSend");
                if (enableAutoSendObj != null) {
                    enableAutoSend = Boolean.parseBoolean(enableAutoSendObj.toString());
                }
            } else if (OrderTypeEnum.SELF_DINE_IN.equals(orderType)) {
                // 堂食订单：使用 enableSdiAutoSend
                Object enableSdiAutoSendObj = merchantMap.get("enableSdiAutoSend");
                if (enableSdiAutoSendObj != null) {
                    enableAutoSend = Boolean.parseBoolean(enableSdiAutoSendObj.toString());
                }
            }
            
            if (enableAutoSend == null) {
                log.warn("商户信息中未找到自动接单配置，默认自动接单，商户ID: {}, 订单类型: {}", merchantId, orderType);
                return true; // 默认自动接单
            }
            
            log.debug("商户自动接单配置，商户ID: {}, 订单类型: {}, 自动接单: {}", merchantId, orderType, enableAutoSend);
            return enableAutoSend;
            
        } catch (Exception e) {
            log.error("检查商户自动接单配置异常，默认自动接单，商户ID: {}, 订单类型: {}", merchantId, orderType, e);
            return true; // 异常时默认自动接单
        }
    }
    
    /**
     * 处理支付失败回调
     */
    @Transactional
    public void handlePaymentFailed(String orderIdStr, String thirdPartyTradeNo, String failureMessage) {
        log.info("处理支付失败回调，订单ID: {}, 第三方交易号: {}, 失败原因: {}", 
                orderIdStr, thirdPartyTradeNo, failureMessage);
        
        try {
            Long orderId = Long.parseLong(orderIdStr);
            
            // 1. 查询订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("订单不存在，订单ID: {}", orderId);
                return;
            }
            
            // 2. 查询支付记录
            Payment payment = paymentMapper.selectByOrderId(orderId).stream()
                    .filter(p -> p.getType() != null && p.getType() == PAYMENT_TYPE_CHARGE)
                    .findFirst()
                    .orElse(null);
            
            if (payment == null) {
                log.warn("支付记录不存在，订单ID: {}", orderId);
                return;
            }
            
            // 3. 更新支付状态为失败
            paymentMapper.updateStatus(
                    payment.getId(),
                    PAYMENT_STATUS_FAILED,
                    thirdPartyTradeNo
            );
            
            // 4. 更新订单状态（如果需要）
            // 注意：这里不更新订单状态，因为订单可能还有其他支付方式
            
            log.info("支付失败处理完成，订单ID: {}, 支付ID: {}", orderId, payment.getId());
            
        } catch (Exception e) {
            log.error("处理支付失败回调异常，订单ID: {}", orderIdStr, e);
        }
    }
    
    /**
     * 从订单价格JSON中解析总金额
     */
    // 已删除parseOrderPrice和parseSubtotalFromOrder方法
    // 使用OrderPriceUtil工具类替代，避免代码重复
    
    /**
     * 将 Payment 实体转换为 PaymentResponse
     */
    private PaymentResponse convertPaymentToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setPaymentMethod(payment.getPaymentService() != null ? payment.getPaymentService().getCode() : null);
        response.setAmount(payment.getAmount());
        
        if (payment.getStatus() != null && payment.getStatus().equals(PaymentStatusEnum.SUCCESS.getCode())) {
            response.setStatus("SUCCESS");
        } else if (payment.getStatus() != null && payment.getStatus().equals(PaymentStatusEnum.FAILED.getCode())) {
            response.setStatus("FAILED");
        } else {
            response.setStatus("PENDING");
        }
        
        response.setThirdPartyTradeNo(payment.getThirdPartyTradeNo());
        response.setPayTime(payment.getUpdateTime());
        
        // 解析二维码
        if (payment.getExtra() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = objectMapper.readValue(payment.getExtra(), Map.class);
                if (extra.containsKey("qrCode")) {
                    response.setQrCode(extra.get("qrCode").toString());
                    response.setPayUrl(extra.get("qrCode").toString());
                }
            } catch (Exception e) {
                log.warn("解析支付额外信息失败", e);
            }
        }
        
        return response;
    }
    
    /**
     * 计算平台手续费（参照 OO 项目）
     * 
     * @param amount 支付金额（元）
     * @param merchantConfig 商户配置
     * @param paymentMethodId 支付方式ID（可选，用于判断卡片类型）
     * @return 手续费金额（分）
     */
    private Long calculateApplicationFee(BigDecimal amount, MerchantStripeConfig merchantConfig, String paymentMethodId) {
        Double percentage;
        Double fixed;
        
        // 判断是否是美国运通（简化处理，实际应该查询 PaymentMethod）
        boolean isAmex = false; // TODO: 查询 PaymentMethod 判断卡片类型
        
        // 获取默认配置（防止空指针）
        StripeConfig.Connect connectConfig = stripeConfig.getConnect();
        if (connectConfig == null) {
            connectConfig = new StripeConfig.Connect(); // 使用默认值
        }
        
        if (isAmex) {
            percentage = merchantConfig.getAmexApplicationFeePercentage() != null 
                    ? merchantConfig.getAmexApplicationFeePercentage() 
                    : (connectConfig.getAmexApplicationFeePercentage() != null ? connectConfig.getAmexApplicationFeePercentage() : 3.5);
            fixed = merchantConfig.getAmexApplicationFeeFixed() != null 
                    ? merchantConfig.getAmexApplicationFeeFixed() 
                    : (connectConfig.getAmexApplicationFeeFixed() != null ? connectConfig.getAmexApplicationFeeFixed() : 0.30);
        } else {
            percentage = merchantConfig.getApplicationFeePercentage() != null 
                    ? merchantConfig.getApplicationFeePercentage() 
                    : (connectConfig.getApplicationFeePercentage() != null ? connectConfig.getApplicationFeePercentage() : 2.5);
            fixed = merchantConfig.getApplicationFeeFixed() != null 
                    ? merchantConfig.getApplicationFeeFixed() 
                    : (connectConfig.getApplicationFeeFixed() != null ? connectConfig.getApplicationFeeFixed() : 0.30);
        }
        
        // 确保不为 null（使用默认值）
        if (percentage == null) {
            percentage = 2.5;
        }
        if (fixed == null) {
            fixed = 0.30;
        }
        
        // 计算手续费：百分比部分 + 固定部分
        BigDecimal percentageFee = amount.multiply(BigDecimal.valueOf(percentage / 100.0));
        BigDecimal fixedFee = BigDecimal.valueOf(fixed);
        BigDecimal totalFee = percentageFee.add(fixedFee);
        
        // 转换为分并四舍五入
        long feeInCents = totalFee.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        
        log.info("计算平台手续费，金额: {} 元, 费率: {}%, 固定: {} 元, 手续费: {} 分", 
                amount, percentage, fixed, feeInCents);
        
        return feeInCents;
    }
    
    /**
     * 检查订单是否已超时（参照 OO 项目的超时检查逻辑）
     * 
     * @param order 订单
     * @return true 如果订单已超时
     */
    public boolean isOrderTimeout(Order order) {
        if (order.getCreateTime() == null) {
            return false;
        }
        
        LocalDateTime timeoutThreshold = order.getCreateTime().plusMinutes(orderTimeoutMinutes);
        boolean isTimeout = LocalDateTime.now().isAfter(timeoutThreshold);
        
        if (isTimeout) {
            log.warn("订单已超时，订单ID: {}, 创建时间: {}, 超时时间: {}, 当前时间: {}", 
                    order.getId(), order.getCreateTime(), timeoutThreshold, LocalDateTime.now());
        }
        
        return isTimeout;
    }
    
    /**
     * 处理超时订单的自动退款（参照 OO 项目的超时退款逻辑）
     * 
     * @param order 订单
     * @param payment 支付记录
     * @param thirdPartyTradeNo 第三方交易号
     */
    private void processTimeoutRefund(Order order, Payment payment, String thirdPartyTradeNo) {
        log.warn("开始处理超时订单自动退款，订单ID: {}, 支付ID: {}, 第三方交易号: {}", 
                order.getId(), payment.getId(), thirdPartyTradeNo);
        
        try {
            // 解析订单金额
            BigDecimal refundAmount = OrderPriceUtil.parseOrderTotal(order);
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("订单金额为0或无效，无法退款，订单ID: {}", order.getId());
                return;
            }
            
            String refundReason = "订单超时自动退款（订单创建后超过 " + orderTimeoutMinutes + " 分钟未支付）";
            
            // 根据支付方式选择退款服务
            PaymentServiceEnum paymentService = payment.getPaymentService();
            boolean refundSuccess = false;
            
            if (PaymentServiceEnum.ALIPAY.equals(paymentService)) {
                // 支付宝退款
                refundSuccess = alipayService.refund(thirdPartyTradeNo, refundAmount, refundReason);
            } else if (PaymentServiceEnum.STRIPE.equals(paymentService)) {
                // Stripe 退款
                try {
                    String paymentIntentId = payment.getStripePaymentIntentId();
                    if (paymentIntentId == null || paymentIntentId.isEmpty()) {
                        log.warn("Stripe 支付记录缺少 Payment Intent ID，无法退款，支付ID: {}", payment.getId());
                        return;
                    }
                    
                    stripeService.createRefund(paymentIntentId, refundAmount, refundReason);
                    refundSuccess = true;
                } catch (Exception e) {
                    log.error("Stripe 退款失败，订单ID: {}, Payment Intent ID: {}", 
                            order.getId(), payment.getStripePaymentIntentId(), e);
                    refundSuccess = false;
                }
            } else {
                log.warn("不支持的支付方式，无法自动退款，支付方式: {}, 订单ID: {}", 
                        paymentService != null ? paymentService.getCode() : "null", order.getId());
                return;
            }
            
            if (refundSuccess) {
                log.info("超时订单自动退款成功，订单ID: {}, 退款金额: {}", order.getId(), refundAmount);
                
                // 更新订单状态为已退款
                order.setStatus(OrderStatusEnum.REFUNDED.getCode());
                order.setRefundAmount(refundAmount);
                order.setRefundReason(refundReason);
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.update(order);
                
                // 创建退款支付记录
            Payment refundPayment = new Payment();
            refundPayment.setOrderId(order.getId());
            refundPayment.setMerchantId(order.getMerchantId());
            refundPayment.setStoreId(order.getStoreId()); // 设置 storeId（用于分片）
            // 计算并设置 shardId（基于 storeId，与订单保持一致）
            if (order.getStoreId() != null) {
                int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(order.getStoreId());
                refundPayment.setShardId(shardId);
            } else if (order.getShardId() != null) {
                refundPayment.setShardId(order.getShardId());
            } else {
                throw new IllegalStateException("无法获取 storeId 或 shardId，无法创建退款支付记录");
            }
            // 生成退款支付流水号（格式：REFUND_{订单ID}_{时间戳}）
            String refundPaymentNo = "REFUND_" + order.getId() + "_" + System.currentTimeMillis();
            refundPayment.setPaymentNo(refundPaymentNo);
            refundPayment.setStatus(PaymentStatusEnum.SUCCESS.getCode());
            refundPayment.setType(PaymentTypeEnum.REFUND.getCode());
            refundPayment.setAmount(refundAmount);
            refundPayment.setThirdPartyTradeNo(thirdPartyTradeNo);
                refundPayment.setPaymentService(paymentService);
                refundPayment.setCategory(payment.getCategory());
                refundPayment.setCreateTime(LocalDateTime.now());
                refundPayment.setUpdateTime(LocalDateTime.now());
                refundPayment.setVersion(1);
                paymentMapper.insert(refundPayment);
                
                log.info("超时订单退款处理完成，订单ID: {}, 退款支付记录ID: {}", order.getId(), refundPayment.getId());
            } else {
                log.error("超时订单自动退款失败，需要人工处理，订单ID: {}, 退款金额: {}", order.getId(), refundAmount);
            }
            
        } catch (Exception e) {
            log.error("处理超时订单退款异常，订单ID: {}", order.getId(), e);
        }
    }
    
    /**
     * 创建 DoorDash 配送订单（支付成功后调用）
     * 如果报价过期，会重新获取报价
     * 注意：改为 public，供 DoorDashRetryService 调用
     */
    public void createDoorDashDelivery(Order order) {
        log.info("创建 DoorDash 配送订单，订单ID: {}, 商户ID: {}", order.getId(), order.getMerchantId());
        
        try {
            // 1. 解析商户地址和用户地址
            Map<String, Object> pickupAddress = parseMerchantAddress(order.getMerchantId());
            Map<String, Object> dropoffAddress = parseCustomerAddress(order);
            
            if (pickupAddress == null || dropoffAddress == null) {
                log.warn("地址信息不完整，无法创建 DoorDash 配送订单，订单ID: {}", order.getId());
                return;
            }
            
            // 2. 获取或创建 Delivery 记录
            Delivery delivery = getOrCreateDelivery(order);
            
            // 3. 检查报价是否过期，如果过期则重新报价
            BigDecimal currentQuotedFee = delivery.getDeliveryFeeQuoted();
            LocalDateTime quotedAt = delivery.getDeliveryFeeQuotedAt();
            boolean needReQuote = false;
            
            if (quotedAt == null) {
                log.warn("订单缺少报价时间，需要重新报价，订单ID: {}", order.getId());
                needReQuote = true;
            } else {
                // 检查报价是否过期（默认有效期 10 分钟）
                LocalDateTime now = LocalDateTime.now();
                long minutesSinceQuote = java.time.Duration.between(quotedAt, now).toMinutes();
                
                if (minutesSinceQuote >= 10) {
                    log.warn("DoorDash 报价已过期（{} 分钟前获取），需要重新报价，订单ID: {}", 
                            minutesSinceQuote, order.getId());
                    needReQuote = true;
                }
            }
            
            // 4. 如果报价过期，重新获取报价
            if (needReQuote) {
                log.info("重新获取 DoorDash 报价，订单ID: {}", order.getId());
                
                // 解析订单小计（用于报价）
                BigDecimal subtotal = com.jiaoyi.order.util.OrderPriceUtil.parseOrderSubtotal(order.getOrderPrice());
                if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("订单小计无效，无法重新报价，订单ID: {}", order.getId());
                    // 如果无法重新报价，使用原报价继续（可能费用会有差异，但至少能创建配送）
                } else {
                    // 重新获取报价
                    String externalDeliveryId = "order_" + order.getId();
                    DoorDashService.DoorDashQuoteResponse newQuote = doorDashService.quoteDelivery(
                            externalDeliveryId,
                            pickupAddress,
                            dropoffAddress,
                            subtotal
                    );
                    
                    BigDecimal newQuotedFee = newQuote.getQuotedFee();
                    BigDecimal oldQuotedFee = currentQuotedFee != null ? currentQuotedFee : BigDecimal.ZERO;
                    
                    // 检查费用差异
                    BigDecimal feeDifference = newQuotedFee.subtract(oldQuotedFee);
                    BigDecimal feeDifferencePercent = oldQuotedFee.compareTo(BigDecimal.ZERO) > 0 ?
                            feeDifference.divide(oldQuotedFee, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) :
                            BigDecimal.ZERO;
                    
                    log.info("DoorDash 重新报价完成，订单ID: {}, 原报价: ${}, 新报价: ${}, 差异: ${} ({}%)", 
                            order.getId(), oldQuotedFee, newQuotedFee, feeDifference, feeDifferencePercent);
                    
                    // 如果费用差异超过 20%，记录警告（但继续使用新报价）
                    if (feeDifferencePercent.abs().compareTo(new BigDecimal("20")) > 0) {
                        log.warn("DoorDash 报价费用差异较大（{}%），订单ID: {}, 原报价: ${}, 新报价: ${}", 
                                feeDifferencePercent, order.getId(), oldQuotedFee, newQuotedFee);
                    }
                    
                    // 处理费用差异：如果新报价 > 原报价，多出来的费用由平台承担
                    // 因为用户已经按照原报价支付了，不能再向用户收费
                    if (feeDifference.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("重新报价后费用增加，多出的费用由平台承担，订单ID: {}, 原报价: ${}, 新报价: ${}, 平台承担: ${}", 
                                order.getId(), oldQuotedFee, newQuotedFee, feeDifference);
                        
                        // 构建费用差异归因 JSON
                        try {
                            Map<String, Object> variance = new HashMap<>();
                            Map<String, Object> reQuoteVariance = new HashMap<>();
                            reQuoteVariance.put("amount", feeDifference);
                            reQuoteVariance.put("attribution", "PLATFORM"); // 平台承担
                            reQuoteVariance.put("reason", "报价过期重新报价，费用增加");
                            variance.put("reQuoteVariance", reQuoteVariance);
                            
                            delivery.setDeliveryFeeVariance(objectMapper.writeValueAsString(variance));
                        } catch (Exception e) {
                            log.error("构建费用差异归因 JSON 失败，订单ID: {}", order.getId(), e);
                        }
                    } else if (feeDifference.compareTo(BigDecimal.ZERO) < 0) {
                        // 如果新报价 < 原报价，差额归平台所有（作为营销成本或利润）
                        log.info("重新报价后费用减少，差额归平台所有，订单ID: {}, 原报价: ${}, 新报价: ${}, 差额: ${}", 
                                order.getId(), oldQuotedFee, newQuotedFee, feeDifference.abs());
                    }
                    
                    // 更新 Delivery 的报价信息
                    delivery.setDeliveryFeeQuoted(newQuotedFee);
                    delivery.setDeliveryFeeQuotedAt(LocalDateTime.now());
                    delivery.setDeliveryFeeQuoteId(newQuote.getQuoteId());
                    
                    // 如果 Delivery 记录已经有 id，更新数据库中的报价信息
                    // 注意：如果还没有 id，不能插入（因为 id 是主键且不能为 null），需要等到创建 DoorDash 配送后获取到 delivery_id 再插入
                    if (delivery.getId() != null && !delivery.getId().isEmpty()) {
                        // 更新数据库中的报价信息
                        deliveryMapper.updateQuoteInfo(
                                delivery.getId(),
                                newQuotedFee,
                                LocalDateTime.now(),
                                newQuote.getQuoteId()
                        );
                    }
                    // 如果还没有 id，暂时不插入，等创建 DoorDash 配送后获取到 delivery_id 再插入
                    
                    // 如果有费用差异，更新费用差异信息
                    if (delivery.getDeliveryFeeVariance() != null && !delivery.getDeliveryFeeVariance().isEmpty()) {
                        if (delivery.getId() != null && !delivery.getId().isEmpty()) {
                            deliveryMapper.updateDeliveryFeeInfo(
                                    delivery.getId(),
                                    newQuotedFee,
                                    delivery.getDeliveryFeeChargedToUser(), // 用户已支付的费用不变
                                    null, // billedFee 还未确定
                                    delivery.getDeliveryFeeVariance()
                            );
                        }
                    }
                    
                    log.info("订单报价信息已更新，订单ID: {}, 新报价: ${}, quote_id: {}", 
                            order.getId(), newQuotedFee, newQuote.getQuoteId());
                }
            }
            
            // 4. 解析小费（从 orderPrice JSON 中）
            BigDecimal tip = parseTipsFromOrder(order);
            
            // 5. 创建 DoorDash 配送订单
            // 如果订单有有效的 quote_id，使用 acceptQuote 来锁定价格
            // 否则直接调用 createDelivery
            DoorDashService.DoorDashDeliveryResponse deliveryResponse;
            String quoteId = delivery.getDeliveryFeeQuoteId();
            LocalDateTime quoteTime = delivery.getDeliveryFeeQuotedAt();
            
            if (quoteId != null && !quoteId.isEmpty() && quoteTime != null) {
                // 检查 quote_id 是否还在有效期内（DoorDash 要求 5 分钟内 accept）
                LocalDateTime now = LocalDateTime.now();
                long minutesSinceQuote = java.time.Duration.between(quoteTime, now).toMinutes();
                
                // 如果 quote_id 还在有效期内（DoorDash 要求 5 分钟内 accept），使用 acceptQuote 锁定价格
                if (minutesSinceQuote < 5) {
                    String externalDeliveryId = "order_" + order.getId();
                    // 解析收货人电话（accept 时可以修改）
                    String dropoffPhoneNumber = parseDropoffPhoneNumber(order);
                    
                    log.info("使用 quote_id 锁定价格创建配送订单，订单ID: {}, external_delivery_id: {}, quote_id: {}, 报价时间: {} 分钟前", 
                            order.getId(), externalDeliveryId, quoteId, minutesSinceQuote);
                    try {
                        // DoorDash Drive API: POST /drive/v2/quotes/{external_delivery_id}/accept
                        deliveryResponse = doorDashService.acceptQuote(externalDeliveryId, quoteId, tip, dropoffPhoneNumber);
                        log.info("使用 quote_id 成功创建配送订单，订单ID: {}, delivery_id: {}", 
                                order.getId(), deliveryResponse.getDeliveryId());
                    } catch (Exception e) {
                        log.warn("使用 quote_id 创建配送订单失败，改用 createDelivery，订单ID: {}, quote_id: {}, 错误: {}", 
                                order.getId(), quoteId, e.getMessage());
                        // 如果 acceptQuote 失败，降级使用 createDelivery
                        deliveryResponse = doorDashService.createDelivery(
                                order,
                                pickupAddress,
                                dropoffAddress,
                                tip,
                                delivery
                        );
                    }
                } else {
                    log.warn("quote_id 已过期（{} 分钟前获取，DoorDash 要求 5 分钟内 accept），使用 createDelivery 创建配送订单，订单ID: {}, quote_id: {}", 
                            minutesSinceQuote, order.getId(), quoteId);
                    // quote_id 已过期，使用 createDelivery（会使用最新价格）
                    deliveryResponse = doorDashService.createDelivery(
                            order,
                            pickupAddress,
                            dropoffAddress,
                            tip,
                            delivery
                    );
                }
            } else {
                log.info("订单没有有效的 quote_id，使用 createDelivery 创建配送订单，订单ID: {}", order.getId());
                // 没有 quote_id，直接使用 createDelivery
                deliveryResponse = doorDashService.createDelivery(
                        order,
                        pickupAddress,
                        dropoffAddress,
                        tip,
                        delivery
                );
            }
            
            // 6. 更新 Delivery 和 Order 信息
            delivery.setId(deliveryResponse.getDeliveryId());
            delivery.setTrackingUrl(deliveryResponse.getTrackingUrl());
            // 将 DoorDash 返回的字符串状态转换为枚举
            if (deliveryResponse.getStatus() != null && !deliveryResponse.getStatus().isEmpty()) {
                com.jiaoyi.order.enums.DeliveryStatusEnum statusEnum = com.jiaoyi.order.enums.DeliveryStatusEnum.fromCode(deliveryResponse.getStatus());
                if (statusEnum != null) {
                    delivery.setStatus(statusEnum);
                } else {
                    log.warn("未知的配送状态: {}，订单ID: {}", deliveryResponse.getStatus(), order.getId());
                }
            }
            if (deliveryResponse.getDistanceMiles() != null) {
                delivery.setDistanceMiles(deliveryResponse.getDistanceMiles());
            }
            if (deliveryResponse.getEtaMinutes() != null) {
                delivery.setEtaMinutes(deliveryResponse.getEtaMinutes());
            }
            
            // 如果 Delivery 记录还没有 id，先插入（这种情况不应该发生，因为之前应该已经创建了）
            if (delivery.getId() == null || delivery.getId().isEmpty()) {
                delivery.setId(deliveryResponse.getDeliveryId());
                deliveryMapper.insert(delivery);
            } else {
                // 更新 Delivery 记录
                deliveryMapper.update(delivery);
            }
            
            // 更新订单的 deliveryId
            order.setDeliveryId(deliveryResponse.getDeliveryId());
            orderMapper.updateDeliveryId(order.getId(), deliveryResponse.getDeliveryId());
            
            // 7. 构建 additionalData（用于发送给 POS）
            Map<String, Object> additionalData = new HashMap<>();
            
            // deliveryInfo
            Map<String, Object> deliveryInfo = new HashMap<>();
            deliveryInfo.put("platform", "DOORDASH");
            deliveryInfo.put("deliveryId", deliveryResponse.getDeliveryId());
            additionalData.put("deliveryInfo", deliveryInfo);
            
            // priceInfo
            Map<String, Object> priceInfo = new HashMap<>();
            
            // deliveryFeeToThird（给 DoorDash 的费用，使用最新报价）
            Map<String, Object> deliveryFeeToThird = new HashMap<>();
            deliveryFeeToThird.put("name", "DeliveryFee");
            deliveryFeeToThird.put("value", delivery.getDeliveryFeeQuoted());
            priceInfo.put("deliveryFeeToThird", deliveryFeeToThird);
            
            // deliveryFeeToRes（给餐厅的费用）
            // 如果用户支付的费用 < 新报价，差额由平台承担（不扣餐厅的钱）
            // 如果用户支付的费用 > 新报价，差额归平台所有（不额外给餐厅）
            Map<String, Object> deliveryFeeToRes = new HashMap<>();
            deliveryFeeToRes.put("name", "DeliveryFee");
            BigDecimal userPaidFee = delivery.getDeliveryFeeChargedToUser() != null ? 
                    delivery.getDeliveryFeeChargedToUser() : BigDecimal.ZERO;
            BigDecimal quotedFee = delivery.getDeliveryFeeQuoted() != null ? 
                    delivery.getDeliveryFeeQuoted() : BigDecimal.ZERO;
            
            // deliveryFeeToRes = 用户支付的费用 - 新报价
            // 如果为负数，表示新报价 > 用户支付的费用，这部分差额由平台承担，不扣餐厅的钱
            // 如果为正数，表示用户支付的费用 > 新报价，这部分差额归平台所有，不额外给餐厅
            BigDecimal resFee = userPaidFee.subtract(quotedFee);
            deliveryFeeToRes.put("value", resFee);
            priceInfo.put("deliveryFeeToRes", deliveryFeeToRes);
            
            log.info("配送费分配，订单ID: {}, 用户支付: ${}, DoorDash报价: ${}, 餐厅费用: ${} (负数表示平台承担差额)", 
                    order.getId(), userPaidFee, quotedFee, resFee);
            
            // tipsToThird（小费）
            if (tip != null && tip.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.put("tipsToThird", tip);
            }
            
            additionalData.put("priceInfo", priceInfo);
            
            // 8. 保存 additionalData 到 Delivery 记录
            delivery.setAdditionalData(objectMapper.writeValueAsString(additionalData));
            deliveryMapper.update(delivery);
            
            log.info("DoorDash 配送订单创建成功，订单ID: {}, delivery_id: {}", 
                    order.getId(), deliveryResponse.getDeliveryId());
            
        } catch (Exception e) {
            log.error("创建 DoorDash 配送订单失败，订单ID: {}", order.getId(), e);
            throw new RuntimeException("创建 DoorDash 配送订单失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 判断是否为 DoorDash 配送（根据商户配置）
     */
    private boolean isDoorDashDelivery(String merchantId) {
        // TODO: 从商户配置中读取是否使用 DoorDash
        // 暂时返回 true（如果配置了 Mock 模式，会使用 Mock 数据）
        // 实际实现时需要查询商户配置
        return true;
    }
    
    /**
     * 解析商户地址
     */
    private Map<String, Object> parseMerchantAddress(String merchantId) {
        // TODO: 从商户信息中获取地址
        // 暂时返回示例数据，实际实现时需要查询商户信息
        Map<String, Object> address = new HashMap<>();
        address.put("street_address", "123 Main St");
        address.put("city", "New York");
        address.put("state", "NY");
        address.put("zip_code", "10001");
        address.put("lat", 40.7128);
        address.put("lng", -74.0060);
        return address;
    }
    
    /**
     * 解析收货人电话（用于 DoorDash acceptQuote）
     * DoorDash acceptQuote 时可以修改 dropoff_phone_number
     */
    private String parseDropoffPhoneNumber(Order order) {
        try {
            if (order.getCustomerInfo() != null && !order.getCustomerInfo().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> customerInfo = objectMapper.readValue(
                        order.getCustomerInfo(), 
                        Map.class
                );
                
                // 尝试从 customerInfo 中获取电话
                Object phoneObj = customerInfo.get("phone");
                if (phoneObj != null) {
                    String phone = phoneObj.toString();
                    // 确保电话格式包含国家码（如 +1-xxx-xxx-xxxx）
                    if (!phone.startsWith("+")) {
                        phone = "+1" + phone.replaceAll("[^0-9]", "");
                    }
                    return phone;
                }
                
                // 尝试从 address 中获取
                Object addressObj = customerInfo.get("address");
                if (addressObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> address = (Map<String, Object>) addressObj;
                    Object phone = address.get("phone_number");
                    if (phone != null) {
                        String phoneStr = phone.toString();
                        if (!phoneStr.startsWith("+")) {
                            phoneStr = "+1" + phoneStr.replaceAll("[^0-9]", "");
                        }
                        return phoneStr;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析收货人电话失败，订单ID: {}", order.getId(), e);
        }
        return null;
    }
    
    /**
     * 解析用户配送地址（包含必填字段：电话）
     * DoorDash 要求 dropoff_phone_number 是必填字段
     */
    private Map<String, Object> parseCustomerAddress(Order order) {
        try {
            Map<String, Object> address = new HashMap<>();
            
            if (order.getCustomerInfo() != null && !order.getCustomerInfo().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> customerInfo = objectMapper.readValue(
                        order.getCustomerInfo(), 
                        Map.class
                );
                
                // 地址信息
                Object addressObj = customerInfo.get("address");
                if (addressObj instanceof String) {
                    address.put("street_address", addressObj);
                } else if (addressObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> addrMap = (Map<String, Object>) addressObj;
                    address.put("street_address", addrMap.get("address1"));
                    address.put("city", addrMap.get("city"));
                    address.put("state", addrMap.get("state"));
                    address.put("zip_code", addrMap.get("zipCode"));
                    
                    if (addrMap.get("latitude") != null && addrMap.get("longitude") != null) {
                        address.put("lat", addrMap.get("latitude"));
                        address.put("lng", addrMap.get("longitude"));
                    }
                }
                
                // 电话信息（DoorDash 必填字段）
                String phone = (String) customerInfo.get("phone");
                if (phone != null && !phone.isEmpty()) {
                    // 确保电话格式包含国家码（如 +1-xxx-xxx-xxxx）
                    if (!phone.startsWith("+")) {
                        // 如果没有国家码，默认添加 +1（美国）
                        phone = "+1" + phone.replaceAll("[^0-9]", "");
                    }
                    address.put("phone_number", phone);
                } else {
                    log.warn("订单缺少电话信息，订单ID: {}", order.getId());
                    // 如果没有电话，尝试从 deliveryAddress 中获取
                    if (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isEmpty()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> deliveryAddr = objectMapper.readValue(
                                    order.getDeliveryAddress(), 
                                    Map.class
                            );
                            phone = (String) deliveryAddr.get("phone");
                            if (phone != null && !phone.isEmpty()) {
                                if (!phone.startsWith("+")) {
                                    phone = "+1" + phone.replaceAll("[^0-9]", "");
                                }
                                address.put("phone_number", phone);
                            }
                        } catch (Exception e) {
                            log.warn("从 deliveryAddress 解析电话失败，订单ID: {}", order.getId(), e);
                        }
                    }
                }
            }
            
            // 验证必填字段
            if (address.get("street_address") == null || address.get("phone_number") == null) {
                log.error("地址信息不完整，缺少必填字段，订单ID: {}", order.getId());
                return null;
            }
            
            return address;
        } catch (Exception e) {
            log.warn("解析用户配送地址失败，订单ID: {}", order.getId(), e);
            return null;
        }
    }
    
    /**
     * 获取或创建 Delivery 记录
     * 如果订单已有 deliveryId，则查询对应的 Delivery 记录
     * 如果不存在，则创建一个新的 Delivery 记录
     */
    private Delivery getOrCreateDelivery(Order order) {
        Delivery delivery = null;
        
        // 如果订单已有 deliveryId，尝试查询
        if (order.getDeliveryId() != null && !order.getDeliveryId().isEmpty()) {
            delivery = deliveryMapper.selectById(order.getDeliveryId());
        }
        
        // 如果查询不到，尝试通过 orderId 查询
        if (delivery == null) {
            delivery = deliveryMapper.selectByOrderId(order.getId());
        }
        
        // 如果还是查询不到，创建一个新的 Delivery 记录
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setOrderId(order.getId());
            delivery.setMerchantId(order.getMerchantId());
            delivery.setStoreId(order.getStoreId()); // 设置 storeId（用于分片）
            delivery.setExternalDeliveryId("order_" + order.getId());
            delivery.setStatus(com.jiaoyi.order.enums.DeliveryStatusEnum.CREATED);
            delivery.setVersion(0L);
            delivery.setCreateTime(LocalDateTime.now());
            delivery.setUpdateTime(LocalDateTime.now());
            // 注意：此时还没有 deliveryId，需要在创建 DoorDash 配送后更新
        }
        
        return delivery;
    }
    
    /**
     * 从订单的 orderPrice JSON 中解析小费
     */
    private BigDecimal parseTipsFromOrder(Order order) {
        try {
            if (order.getOrderPrice() != null && !order.getOrderPrice().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderPriceMap = objectMapper.readValue(
                        order.getOrderPrice(), 
                        Map.class
                );
                Object tipsObj = orderPriceMap.get("tips");
                if (tipsObj instanceof Number) {
                    return new BigDecimal(tipsObj.toString());
                }
            }
        } catch (Exception e) {
            log.warn("解析订单小费失败，订单ID: {}", order.getId(), e);
        }
        return BigDecimal.ZERO;
    }
}


