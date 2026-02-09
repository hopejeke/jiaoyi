package com.jiaoyi.order.controller;

import com.jiaoyi.order.config.RocketMQConfig;
import com.jiaoyi.order.config.StripeConfig;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.entity.Payment;
import com.jiaoyi.order.mapper.OrderItemMapper;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.PaymentMapper;
import com.jiaoyi.order.service.OutboxHelper;
import com.jiaoyi.order.service.PaymentService;
import com.jiaoyi.order.service.StripeService;
import com.jiaoyi.order.service.WebhookEventLogService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Stripe Webhook 控制器
 * 参照 OO 项目的 StripePay.handleWebhook
 */
@RestController
@RequestMapping("/api/payment/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final StripeService stripeService;
    private final StripeConfig stripeConfig;
    private final WebhookEventLogService webhookEventLogService;
    private final OutboxHelper outboxHelper;
    private final RedissonClient redissonClient;
    
    /**
     * 处理 GET 请求（用于测试和验证）
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> handleWebhookGet(HttpServletRequest request) {
        log.info("收到 GET 请求到 Webhook 端点（可能是测试访问）");
        log.info("请求URL: {}", request.getRequestURL());
        log.info("请求路径: {}", request.getRequestURI());
        return ResponseEntity.ok("Stripe Webhook endpoint is active. Please use POST method to send webhook events.");
    }
    
    /**
     * 处理 Stripe Webhook 回调
     * 参照 OO 项目的 handleWebhook 方法
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            HttpServletRequest request) {
        
        log.info("========== 收到 Stripe Webhook 回调 ==========");
        log.info("请求方法: {}", request.getMethod());
        log.info("请求URL: {}", request.getRequestURL());
        log.info("请求路径: {}", request.getRequestURI());
        log.info("Content-Type: {}", request.getContentType());
        log.info("Stripe-Signature: {}", signature != null ? "已提供" : "未提供");
        log.info("请求体长度: {} 字节", payload != null ? payload.length() : 0);
        
        if (payload == null || payload.isEmpty()) {
            log.error("Webhook 请求体为空！");
            return ResponseEntity.badRequest().body("Payload is empty");
        }
        
        try {
            // 验证签名（如果配置了 webhook secret）
            Event event;
            String webhookSecret = null; // 在外部声明，以便在 catch 块中使用
            if (signature != null && !signature.isEmpty()) {
                try {
                    // 从配置中获取 webhook secret
                    webhookSecret = getWebhookSecret();
                    if (webhookSecret != null && !webhookSecret.isEmpty() 
                            && !webhookSecret.contains("请替换")) {
                        log.info("验证 Webhook 签名...");
                        event = Webhook.constructEvent(payload, signature, webhookSecret);
                        log.info("Webhook 签名验证成功");
                    } else {
                        log.warn("Webhook Secret 未配置或无效，跳过签名验证（仅开发环境）");
                        event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
                    }
                } catch (SignatureVerificationException e) {
                    log.error("========== Webhook 签名验证失败 ==========");
                    log.error("错误信息: {}", e.getMessage());
                    log.error("当前配置的 Webhook Secret: {}", webhookSecret != null ? webhookSecret.substring(0, Math.min(20, webhookSecret.length())) + "..." : "null");
                    log.error("收到的 Stripe-Signature: {}", signature);
                    log.error("==========================================");
                    log.error("");
                    log.error("解决方案：");
                    log.error("1. 如果使用 Stripe CLI 转发，请使用 CLI 显示的 webhook secret（运行 'stripe listen' 时显示的 whsec_...）");
                    log.error("2. 如果使用 Stripe Dashboard 配置的 webhook endpoint，请在 Dashboard 中查看该 endpoint 的 Signing secret");
                    log.error("3. 确保 application.properties 中的 stripe.webhook-secret 与上述 secret 一致");
                    log.error("4. 如果只是本地测试，可以暂时将 webhook-secret 设置为空或包含'请替换'，跳过签名验证（仅开发环境）");
                    log.error("");
                    return ResponseEntity.status(400).body("Invalid signature: " + e.getMessage());
                }
            } else {
                log.warn("未提供 Stripe-Signature 请求头，跳过签名验证（仅开发环境）");
                event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
            }
            
            if (event == null) {
                log.error("无法解析 Webhook 事件");
                return ResponseEntity.badRequest().body("Failed to parse event");
            }
            
            log.info("Stripe Webhook 事件类型: {}, ID: {}, 创建时间: {}", 
                    event.getType(), event.getId(), event.getCreated());
            
            // 处理不同类型的事件
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "charge.succeeded":
                    handleChargeSucceeded(event);
                    break;
                case "charge.failed":
                    handleChargeFailed(event);
                    break;
                default:
                    log.info("未处理的事件类型: {}", event.getType());
            }
            
            log.info("========== Stripe Webhook 处理完成 ==========");
            return ResponseEntity.ok("success");
            
        } catch (Exception e) {
            log.error("处理 Stripe Webhook 异常", e);
            log.error("异常堆栈:", e);
            return ResponseEntity.status(500).body("Webhook processing failed: " + e.getMessage());
        }
    }
    
    /**
     * 获取 Webhook Secret（从配置中）
     */
    private String getWebhookSecret() {
        if (stripeConfig != null && stripeConfig.getWebhookSecret() != null) {
            return stripeConfig.getWebhookSecret();
        }
        return null;
    }
    
    /**
     * 处理 Payment Intent 成功事件
     * 【回调链路】：事件幂等 + 分布式锁 + 更新支付/订单状态 + 写库存扣减 outbox，立即返回 200
     * OutboxService 异步发送消息或调用库存服务扣库存
     *
     * 注意：
     * 1. 使用 @Transactional 确保数据库操作的原子性
     * 2. 使用分布式锁（基于 orderId）防止并发处理同一个订单的支付回调
     * 3. 事件幂等性通过 webhookEventLogService.tryInsert 保证
     * 4. 增加重试机制，确保支付回调在并发情况下能正确处理
     */
    @Transactional
    public void handlePaymentIntentSucceeded(Event event) {
        String eventId = event.getId();
        String eventType = event.getType();

        log.info("处理 Payment Intent 成功事件，eventId: {}, eventType: {}", eventId, eventType);

        // 1. 解析 PaymentIntent 信息
        ParsedPaymentIntent parsed = parsePaymentIntent(event);
        if (parsed == null || parsed.paymentIntentId == null || parsed.orderId == null) {
            log.warn("无法解析 PaymentIntent 信息，eventId: {}", eventId);
            return;
        }

        String paymentIntentId = parsed.paymentIntentId;
        Long orderId = parsed.orderId;
        String latestChargeId = parsed.latestChargeId;

        log.info("解析成功，eventId: {}, paymentIntentId: {}, orderId: {}, latestChargeId: {}",
                eventId, paymentIntentId, orderId, latestChargeId);

        // 2. 事件幂等：尝试插入事件日志（如果已存在则直接返回，说明是重复事件）
        boolean isFirstTime = webhookEventLogService.tryInsert(
                eventId,
                eventType,
                paymentIntentId,
                latestChargeId,
                orderId
        );

        if (!isFirstTime) {
            log.info("重复事件，直接返回（幂等），eventId: {}, paymentIntentId: {}", eventId, paymentIntentId);
            return; // 重复事件，直接返回（幂等成功）
        }

        // 3. 重试机制：使用配置的最大重试次数
        int maxRetries = com.jiaoyi.order.constants.OrderConstants.PAYMENT_CALLBACK_MAX_RETRIES;
        for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
            try {
                if (retryCount > 0) {
                    log.info("【StripeWebhook】开始第 {} 次重试，orderId: {}, eventId: {}",
                            retryCount, orderId, eventId);
                }

                // 尝试处理支付回调
                if (processPaymentIntentSucceeded(eventId, paymentIntentId, orderId, latestChargeId)) {
                    // 处理成功，退出重试循环
                    log.info("【StripeWebhook】支付回调处理成功，orderId: {}, eventId: {}", orderId, eventId);
                    return;
                }

            } catch (Exception e) {
                log.error("【StripeWebhook】处理支付回调失败，重试次数: {}/{}, orderId: {}, eventId: {}",
                        retryCount + 1, maxRetries, orderId, eventId, e);

                // 如果是最后一次重试，标记为失败
                if (retryCount == maxRetries - 1) {
                    webhookEventLogService.markFailed(eventId, "处理失败，已达最大重试次数: " + e.getMessage());
                    throw new RuntimeException("处理支付成功失败", e);
                }

                // 指数退避：等待一段时间后重试
                try {
                    long backoffMillis = (long) Math.pow(2, retryCount) * 1000; // 1s, 2s, 4s
                    log.info("【StripeWebhook】等待 {}ms 后重试，orderId: {}", backoffMillis, orderId);
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    webhookEventLogService.markFailed(eventId, "重试被中断");
                    throw new RuntimeException("处理支付成功失败", ie);
                }
            }
        }

        // 如果所有重试都失败，记录错误
        log.error("【StripeWebhook】支付回调处理失败，已达最大重试次数: {}, orderId: {}, eventId: {}",
                maxRetries, orderId, eventId);
        webhookEventLogService.markFailed(eventId, "已达最大重试次数，处理失败");
    }

    /**
     * 处理 Payment Intent 成功的核心逻辑（可重试）
     * @return true 表示处理成功，false 表示需要重试
     */
    private boolean processPaymentIntentSucceeded(String eventId, String paymentIntentId,
                                                   Long orderId, String latestChargeId)
            throws InterruptedException {
        // 3. 分布式锁：防止并发处理同一个订单的支付回调
        String lockKey = com.jiaoyi.order.constants.OrderConstants.PAYMENT_CALLBACK_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        // 尝试获取锁，使用配置的等待时间和持有时间
        boolean lockAcquired = lock.tryLock(
            com.jiaoyi.order.constants.OrderConstants.PAYMENT_CALLBACK_LOCK_WAIT_SECONDS,
            com.jiaoyi.order.constants.OrderConstants.PAYMENT_CALLBACK_LOCK_LEASE_SECONDS,
            TimeUnit.SECONDS);

        if (!lockAcquired) {
            log.warn("【StripeWebhook】获取分布式锁失败，订单可能正在被其他线程处理，orderId: {}, eventId: {}",
                    orderId, eventId);
            // 等待一小段时间后，再次检查事件是否已处理
            Thread.sleep(500);
            boolean isProcessed = webhookEventLogService.isProcessed(eventId);
            if (isProcessed) {
                log.info("【StripeWebhook】事件已被其他线程处理完成，orderId: {}, eventId: {}", orderId, eventId);
                return true; // 已处理，返回成功
            }
            // 未处理，返回false触发重试
            log.warn("【StripeWebhook】获取锁失败且事件未处理，将触发重试，orderId: {}", orderId);
            return false;
        }

        log.info("【StripeWebhook】成功获取分布式锁，开始处理支付成功，orderId: {}, eventId: {}", orderId, eventId);

        try {
            // 4. 再次检查事件是否已处理（双重检查，防止在获取锁期间被其他线程处理）
            boolean isProcessed = webhookEventLogService.isProcessed(eventId);
            if (isProcessed) {
                log.info("【StripeWebhook】事件已被其他线程处理完成（双重检查），orderId: {}, eventId: {}",
                        orderId, eventId);
                return true; // 已处理，返回成功
            }

            // 5. 直接更新支付和订单状态（在事务中）
            String orderIdStr = String.valueOf(orderId);
            log.info("【StripeWebhook】开始处理支付成功，orderId: {}, eventId: {}, paymentIntentId: {}",
                    orderId, eventId, paymentIntentId);

            boolean paymentProcessed = paymentService.handlePaymentSuccess(orderIdStr, paymentIntentId);

            if (!paymentProcessed) {
                log.warn("【StripeWebhook】支付处理失败或订单已超时退款，orderId: {}, eventId: {}", orderId, eventId);
                webhookEventLogService.markFailed(eventId, "支付处理失败或订单已超时退款");
                return true; // 业务上处理完成（虽然失败），不需要重试
            }

            log.info("【StripeWebhook】支付处理成功，开始写入库存扣减任务，orderId: {}, eventId: {}", orderId, eventId);

            // 6. 查询订单项，写入库存扣减任务到 outbox
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
            log.info("【StripeWebhook】查询到订单项数量: {}, orderId: {}",
                    orderItems != null ? orderItems.size() : 0, orderId);

            if (orderItems == null || orderItems.isEmpty()) {
                log.warn("【StripeWebhook】订单项为空，无法写入库存扣减任务，orderId: {}, eventId: {}", orderId, eventId);
                webhookEventLogService.markProcessed(eventId);
                return true; // 订单项为空，业务上处理完成
            }

            boolean enqueued = outboxHelper.enqueueDeductStockTask(orderId, orderItems);

            if (!enqueued) {
                log.error("【StripeWebhook】✗ 写入库存扣减 outbox 失败，orderId: {}, eventId: {}", orderId, eventId);
                // Outbox写入失败需要重试
                return false;
            }

            log.info("【StripeWebhook】✓ 库存扣减任务写入成功，orderId: {}, eventId: {}", orderId, eventId);

            // 7. 标记事件为已处理
            webhookEventLogService.markProcessed(eventId);

            return true; // 处理成功

        } catch (Exception e) {
            log.error("【StripeWebhook】处理支付成功失败，eventId: {}, orderId: {}, paymentIntentId: {}",
                    eventId, orderId, paymentIntentId, e);
            throw e; // 抛出异常，触发重试
        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("【StripeWebhook】释放分布式锁，orderId: {}, eventId: {}", orderId, eventId);
            }
        }
    }
    
    /**
     * 解析 PaymentIntent 信息
     * 返回解析结果，包含 paymentIntentId, orderId, latestChargeId
     */
    private ParsedPaymentIntent parsePaymentIntent(Event event) {
        String paymentIntentId = null;
        String orderIdStr = null;
        String latestChargeId = null;
        
        try {
            // 方法1：尝试从 getDataObjectDeserializer 获取
            java.util.Optional<com.stripe.model.StripeObject> dataObject = event.getDataObjectDeserializer().getObject();
            
            if (dataObject.isPresent()) {
                com.stripe.model.StripeObject obj = dataObject.get();
                log.debug("事件数据对象类型: {}", obj.getClass().getName());
                
                if (obj instanceof PaymentIntent paymentIntent) {
                    paymentIntentId = paymentIntent.getId();
                    orderIdStr = paymentIntent.getMetadata() != null ? paymentIntent.getMetadata().get("orderId") : null;
                    latestChargeId = paymentIntent.getLatestCharge();
                    log.debug("成功从 getDataObjectDeserializer 获取 PaymentIntent，ID: {}", paymentIntentId);
                } else {
                    log.warn("事件数据对象不是 PaymentIntent 类型，实际类型: {}", obj.getClass().getName());
                }
            }
            
            // 方法2：如果方法1失败，直接从事件数据中解析 JSON
            if (paymentIntentId == null) {
                log.debug("尝试从事件数据 JSON 中解析 PaymentIntent 信息");
                com.stripe.model.Event.Data eventData = event.getData();
                if (eventData != null && eventData.getObject() != null) {
                    try {
                        com.google.gson.JsonElement jsonElement = com.stripe.net.ApiResource.GSON.toJsonTree(eventData.getObject());
                        com.google.gson.JsonObject jsonObject = null;
                        
                        if (jsonElement.isJsonObject()) {
                            com.google.gson.JsonObject rootObject = jsonElement.getAsJsonObject();
                            if (rootObject.has("object") && rootObject.get("object").isJsonObject()) {
                                jsonObject = rootObject.getAsJsonObject("object");
                            } else {
                                jsonObject = rootObject;
                            }
                        }
                        
                        if (jsonObject != null) {
                            if (jsonObject.has("id") && jsonObject.get("id").isJsonPrimitive()) {
                                paymentIntentId = jsonObject.get("id").getAsString();
                            }
                            
                            if (jsonObject.has("metadata") && jsonObject.get("metadata").isJsonObject()) {
                                com.google.gson.JsonObject metadata = jsonObject.getAsJsonObject("metadata");
                                if (metadata.has("orderId") && metadata.get("orderId").isJsonPrimitive()) {
                                    orderIdStr = metadata.get("orderId").getAsString();
                                }
                            }
                            
                            if (jsonObject.has("latest_charge")) {
                                com.google.gson.JsonElement latestChargeElement = jsonObject.get("latest_charge");
                                if (latestChargeElement.isJsonPrimitive() && !latestChargeElement.isJsonNull()) {
                                    latestChargeId = latestChargeElement.getAsString();
                                }
                            }
                        }
                    } catch (Exception jsonException) {
                        log.error("解析 JSON 时出错", jsonException);
                    }
                }
            }
            
            if (paymentIntentId == null) {
                log.error("无法从事件中解析 PaymentIntent ID");
                return null;
        }
        
        if (orderIdStr == null || orderIdStr.isEmpty()) {
            log.warn("Payment Intent 元数据中缺少 orderId，Payment Intent ID: {}", paymentIntentId);
                return null;
            }
            
            Long orderId = Long.parseLong(orderIdStr);
            
            return new ParsedPaymentIntent(paymentIntentId, orderId, latestChargeId);
            
        } catch (Exception e) {
            log.error("解析 PaymentIntent 信息异常", e);
            return null;
        }
    }
    
    /**
     * 解析结果
     */
    private record ParsedPaymentIntent(String paymentIntentId, Long orderId, String latestChargeId) {}
    
    /**
     * 处理 Payment Intent 失败事件
     */
    private void handlePaymentIntentFailed(Event event) {
        log.info("处理 Payment Intent 失败事件");
        
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        
        if (paymentIntent == null) {
            return;
        }
        
        String paymentIntentId = paymentIntent.getId();
        log.warn("Payment Intent 支付失败，ID: {}, 失败原因: {}", 
                paymentIntentId, paymentIntent.getLastPaymentError());
        
        // 更新支付状态为失败
        Payment payment = paymentMapper.selectByPaymentIntentId(paymentIntentId);
        if (payment != null) {
            paymentMapper.updateStatus(
                    payment.getId(),
                    com.jiaoyi.order.enums.PaymentStatusEnum.FAILED.getCode(),
                    paymentIntentId
            );
        }
    }
    
    /**
     * 处理 Charge 成功事件（兼容旧版本）
     */
    private void handleChargeSucceeded(Event event) {
        log.info("处理 Charge 成功事件");
        
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            
            if (charge == null) {
                log.warn("Charge 为空");
                return;
            }
            
            String chargeId = charge.getId();
            String orderIdStr = charge.getMetadata().get("orderId");
            
            if (orderIdStr == null) {
                log.warn("Charge 元数据中缺少 orderId，Charge ID: {}", chargeId);
                return;
            }
            
            log.info("Charge 支付成功，Charge ID: {}, 订单ID: {}", chargeId, orderIdStr);
            
            // 查询订单，检查订单状态
            try {
                Long orderId = Long.parseLong(orderIdStr);
                Order order = orderMapper.selectById(orderId);
                if (order == null) {
                    log.warn("订单不存在，订单ID: {}", orderId);
                    return;
                }
                
                // 检查订单是否已取消（如果已取消，需要自动退款）
                if (com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode().equals(order.getStatus())) {
                    log.warn("订单已取消，但收到支付成功回调，订单ID: {}, Charge ID: {}, 当前状态: {}", 
                            orderId, chargeId, order.getStatus());
                    // 查询支付记录
                    Payment payment = paymentMapper.selectByThirdPartyTradeNo(chargeId);
                    if (payment != null) {
                        handleCancelledOrderPayment(order, payment, chargeId);
                    } else {
                        log.warn("支付记录不存在，无法处理已取消订单的退款，Charge ID: {}", chargeId);
                    }
                    return;
                }
                
                // 检查订单是否已支付（幂等处理）
                if (com.jiaoyi.order.enums.OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
                    log.info("订单已支付，重复回调（幂等处理），订单ID: {}", orderId);
                    return;
                }
                
                // 检查订单状态是否允许支付
                if (!com.jiaoyi.order.enums.OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
                    log.warn("订单状态不允许支付，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
                    return;
                }
                
                // 处理支付成功
                boolean paymentProcessed = paymentService.handlePaymentSuccess(orderIdStr, chargeId);
                if (paymentProcessed) {
                    log.info("Charge 支付成功处理完成，订单ID: {}, Charge ID: {}", orderIdStr, chargeId);
                } else {
                    log.warn("Charge 支付成功回调处理未完成（可能已超时并退款），订单ID: {}", orderIdStr, chargeId);
                }
            } catch (Exception e) {
                log.error("处理 Charge 支付成功失败，订单ID: {}", orderIdStr, e);
            }
            
        } catch (Exception e) {
            log.error("处理 Charge 成功事件失败", e);
        }
    }
    
    /**
     * 处理 Charge 失败事件（兼容旧版本）
     */
    private void handleChargeFailed(Event event) {
        log.info("处理 Charge 失败事件");
        
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            
            if (charge == null) {
                log.warn("Charge 为空");
                return;
            }
            
            String chargeId = charge.getId();
            String orderIdStr = charge.getMetadata().get("orderId");
            String failureMessage = charge.getFailureMessage() != null ? charge.getFailureMessage() : "未知失败";
            
            log.warn("Charge 支付失败，Charge ID: {}, 订单ID: {}, 失败原因: {}", 
                    chargeId, orderIdStr, failureMessage);
            
            if (orderIdStr != null) {
                paymentService.handlePaymentFailed(orderIdStr, chargeId, failureMessage);
            }
            
        } catch (Exception e) {
            log.error("处理 Charge 失败事件失败", e);
        }
    }
    
    /**
     * 处理已取消订单的支付回调（Stripe）
     * 这种情况需要自动退款：订单已取消但支付成功了
     * @param order 订单
     * @param payment 支付记录
     * @param stripeId Payment Intent ID 或 Charge ID
     */
    private void handleCancelledOrderPayment(Order order, Payment payment, String stripeId) {
        log.warn("处理已取消订单的支付回调（Stripe），订单ID: {}, Stripe ID: {}", 
                order.getId(), stripeId);
        
        try {
            // 解析订单金额
            java.math.BigDecimal refundAmount = parseOrderPrice(order);
            if (refundAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.warn("订单金额为0或无效，无法退款，订单ID: {}", order.getId());
                return;
            }
            
            String refundReason = "订单已取消但支付成功，自动退款";
            
            // 使用 Stripe 退款（支持 Payment Intent ID 或 Charge ID）
            try {
                com.stripe.model.Refund refund = stripeService.createRefund(
                        stripeId, 
                        refundAmount, 
                        refundReason
                );
                
                if (refund != null && "succeeded".equals(refund.getStatus())) {
                    log.info("已取消订单自动退款成功，订单ID: {}, Stripe ID: {}, 退款金额: {}, Refund ID: {}", 
                            order.getId(), stripeId, refundAmount, refund.getId());
                    
                    // 更新订单状态为已退款
                    order.setStatus(com.jiaoyi.order.enums.OrderStatusEnum.REFUNDED.getCode());
                    order.setRefundAmount(refundAmount);
                    order.setRefundReason(refundReason);
                    order.setUpdateTime(java.time.LocalDateTime.now());
                    orderMapper.updateStatus(order.getId(), order.getStatus());
                    
                    // 创建退款支付记录
                    Payment refundPayment = new Payment();
                    refundPayment.setOrderId(order.getId());
                    refundPayment.setMerchantId(order.getMerchantId());
                    refundPayment.setStatus(com.jiaoyi.order.enums.PaymentStatusEnum.SUCCESS.getCode());
                    refundPayment.setType(com.jiaoyi.order.enums.PaymentTypeEnum.REFUND.getCode());
                    refundPayment.setAmount(refundAmount);
                    refundPayment.setThirdPartyTradeNo(refund.getId());
                    refundPayment.setPaymentService(com.jiaoyi.order.enums.PaymentServiceEnum.STRIPE);
                    refundPayment.setCategory(com.jiaoyi.order.enums.PaymentCategoryEnum.CREDIT_CARD.getCode());
                    refundPayment.setCreateTime(java.time.LocalDateTime.now());
                    refundPayment.setUpdateTime(java.time.LocalDateTime.now());
                    refundPayment.setVersion(1);
                    paymentMapper.insert(refundPayment);
                    
                    log.info("已取消订单退款处理完成，订单ID: {}, 退款支付记录ID: {}", order.getId(), refundPayment.getId());
                } else {
                    log.error("已取消订单自动退款失败，订单ID: {}, Stripe ID: {}, 退款状态: {}", 
                            order.getId(), stripeId, refund != null ? refund.getStatus() : "null");
                }
            } catch (Exception e) {
                log.error("Stripe 退款处理异常，订单ID: {}, Stripe ID: {}", order.getId(), stripeId, e);
            }
            
        } catch (Exception e) {
            log.error("处理已取消订单支付回调异常，订单ID: {}", order.getId(), e);
        }
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
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> orderPrice = mapper.readValue(orderPriceStr, java.util.Map.class);
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
    
}

