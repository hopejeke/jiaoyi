package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.dto.*;
import com.jiaoyi.order.entity.*;
import com.jiaoyi.order.enums.*;
import com.jiaoyi.order.mapper.*;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 退款服务
 * 支持部分退款、多次退款、幂等性、并发控制
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundService {
    
    private final RefundMapper refundMapper;
    private final RefundItemMapper refundItemMapper;
    private final RefundIdempotencyLogMapper refundIdempotencyLogMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundCalculator refundCalculator;
    private final RefundStateMachine refundStateMachine;
    private final com.jiaoyi.order.adapter.PaymentAdapterWithFallback paymentAdapterWithFallback;
    private final StripeService stripeService; // 保留用于兼容
    private final AlipayService alipayService; // 保留用于兼容
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final com.jiaoyi.order.config.StripeConfig stripeConfig;
    
    /**
     * 创建退款（幂等、并发安全）
     */
    @Transactional
    public RefundResponse createRefund(RefundRequest request) {
        String lockKey = "refund:order:" + request.getOrderId();
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 1. 分布式锁（防止并发退款）
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                throw new RuntimeException("退款处理中，请勿重复提交");
            }
            
            // 2. 幂等性检查（基于幂等日志表）
            RefundIdempotencyLog existingLog = refundIdempotencyLogMapper.selectByRequestNo(
                request.getOrderId(), 
                request.getRequestNo()
            );
            if (existingLog != null && existingLog.getRefundId() != null) {
                // 幂等：查询已存在的退款单
                Refund existingRefund = refundMapper.selectById(existingLog.getRefundId());
                if (existingRefund != null) {
                    log.info("退款请求已存在（幂等），退款ID: {}, 请求号: {}", 
                        existingRefund.getRefundId(), request.getRequestNo());
                    return convertToResponse(existingRefund);
                }
            }
            
            // 生成请求指纹（用于额外的幂等性校验）
            String fingerprint = generateFingerprint(request);
            
            // 检查指纹是否已存在
            RefundIdempotencyLog existingFingerprintLog = refundIdempotencyLogMapper.selectByFingerprint(fingerprint);
            if (existingFingerprintLog != null && existingFingerprintLog.getRefundId() != null) {
                Refund existingRefund = refundMapper.selectById(existingFingerprintLog.getRefundId());
                if (existingRefund != null) {
                    log.info("退款请求指纹已存在（幂等），退款ID: {}, 指纹: {}", 
                        existingRefund.getRefundId(), fingerprint);
                    return convertToResponse(existingRefund);
                }
            }
            
            // 创建幂等性日志（先落库，确保幂等性）
            // 注意：此时 order 还未查询，先使用空字符串，后续会更新
            RefundIdempotencyLog idempotencyLog = new RefundIdempotencyLog();
            idempotencyLog.setOrderId(request.getOrderId());
            idempotencyLog.setRequestNo(request.getRequestNo());
            idempotencyLog.setMerchantId(""); // 稍后从 order 中获取
            idempotencyLog.setFingerprint(fingerprint);
            try {
                idempotencyLog.setRequestParams(objectMapper.writeValueAsString(request));
            } catch (Exception e) {
                log.warn("序列化请求参数失败", e);
            }
            idempotencyLog.setResult("PROCESSING");
            idempotencyLog.setCreatedAt(LocalDateTime.now());
            idempotencyLog.setUpdatedAt(LocalDateTime.now());
            
            try {
                refundIdempotencyLogMapper.insert(idempotencyLog);
            } catch (Exception e) {
                // 如果插入失败（可能是唯一索引冲突），再次查询
                RefundIdempotencyLog conflictLog = refundIdempotencyLogMapper.selectByRequestNo(
                    request.getOrderId(), request.getRequestNo());
                if (conflictLog != null && conflictLog.getRefundId() != null) {
                    Refund existingRefund = refundMapper.selectById(conflictLog.getRefundId());
                    if (existingRefund != null) {
                        log.info("退款请求并发冲突（幂等），退款ID: {}, 请求号: {}", 
                            existingRefund.getRefundId(), request.getRequestNo());
                        return convertToResponse(existingRefund);
                    }
                }
                throw new BusinessException("创建退款幂等性日志失败: " + e.getMessage());
            }
            
            // 3. 查询订单（带锁）
            Order order = orderMapper.selectByIdForUpdate(request.getOrderId());
            if (order == null) {
                // 更新幂等日志为失败
                if (idempotencyLog.getId() != null) {
                    refundIdempotencyLogMapper.updateResult(
                        idempotencyLog.getId(), null, "FAILED", "订单不存在");
                }
                throw new RuntimeException("订单不存在");
            }
            
            // 更新幂等日志的 merchantId
            if (idempotencyLog.getId() != null && order.getMerchantId() != null) {
                idempotencyLog.setMerchantId(order.getMerchantId());
            }
            
            // 4. 检查订单状态：允许已支付(2)和已完成(4)的订单退款
            Integer orderStatus = order.getStatus();
            if (!OrderStatusEnum.PAID.getCode().equals(orderStatus) && 
                !OrderStatusEnum.COMPLETED.getCode().equals(orderStatus)) {
                throw new RuntimeException("只有已支付或已完成的订单才能申请退款");
            }
            
            // 5. 查询支付记录
            List<Payment> payments = paymentMapper.selectByOrderId(request.getOrderId());
            Payment payment = payments.stream()
                .filter(p -> PaymentTypeEnum.CHARGE.getCode().equals(p.getType()))
                .filter(p -> PaymentStatusEnum.SUCCESS.getCode().equals(p.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到支付记录"));
            
            // 6. 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(request.getOrderId());
            if (orderItems == null || orderItems.isEmpty()) {
                throw new RuntimeException("订单项为空");
            }
            
            // 7. 计算可退余额
            BigDecimal totalRefunded = calculateTotalRefunded(request.getOrderId());
            BigDecimal totalPaid = parseOrderPrice(order);
            BigDecimal availableRefund = totalPaid.subtract(totalRefunded);
            
            if (availableRefund.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("订单已全额退款，无法继续退款");
            }
            
            // 8. 计算退款明细
            RefundCalculationResult calculation = refundCalculator.calculateRefund(
                order, orderItems, request
            );
            
            // 9. 检查可退余额
            if (calculation.getTotalRefundAmount().compareTo(availableRefund) > 0) {
                throw new RuntimeException(
                    String.format("退款金额超过可退余额，可退: %.2f, 请求退款: %.2f", 
                        availableRefund, calculation.getTotalRefundAmount())
                );
            }
            
            // 10. 计算抽成回补金额
            // 回补抽成 = (退款金额 / 原订单总额) × 总平台抽成
            BigDecimal orderTotal = parseOrderPrice(order);
            BigDecimal charge = getChargeFromOrderPrice(order);
            BigDecimal commissionReversal = BigDecimal.ZERO;
            if (charge != null && charge.compareTo(BigDecimal.ZERO) > 0 && orderTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal refundRate = calculation.getTotalRefundAmount().divide(orderTotal, 4, java.math.RoundingMode.HALF_UP);
                commissionReversal = charge.multiply(refundRate).setScale(2, java.math.RoundingMode.HALF_UP);
                log.info("计算抽成回补，订单ID: {}, 退款金额: {}, 订单总额: {}, 平台抽成: {}, 回补抽成: {}", 
                    request.getOrderId(), calculation.getTotalRefundAmount(), orderTotal, charge, commissionReversal);
            }
            
            // 11. 创建退款单
            Refund refund = new Refund();
            refund.setOrderId(request.getOrderId());
            refund.setPaymentId(payment.getId());
            refund.setMerchantId(order.getMerchantId());
            refund.setStoreId(order.getStoreId()); // 设置 storeId（用于分片）
            // 计算并设置 shardId（基于 storeId，与订单保持一致）
            if (order.getStoreId() != null) {
                int shardId = com.jiaoyi.order.util.ShardUtil.calculateShardId(order.getStoreId());
                refund.setShardId(shardId);
            } else if (order.getShardId() != null) {
                refund.setShardId(order.getShardId());
            } else {
                throw new IllegalStateException("无法获取 storeId 或 shardId，无法创建退款单");
            }
            refund.setRequestNo(request.getRequestNo());
            refund.setRefundAmount(calculation.getTotalRefundAmount());
            refund.setReason(request.getReason());
            refund.setStatus(RefundStatus.CREATED.getCode());
            refund.setCommissionReversal(commissionReversal);
            refund.setVersion(1L);
            refund.setCreatedAt(LocalDateTime.now());
            refund.setUpdatedAt(LocalDateTime.now());
            
            refundMapper.insert(refund);
            log.info("退款单创建成功，退款ID: {}, 订单ID: {}, 退款金额: {}, 抽成回补: {}", 
                refund.getRefundId(), refund.getOrderId(), refund.getRefundAmount(), refund.getCommissionReversal());
            
            // 更新幂等性日志（关联退款ID）
            refundIdempotencyLogMapper.updateResult(
                idempotencyLog.getId(),
                refund.getRefundId(),
                "SUCCESS",
                null
            );
            
            // 12. 创建退款明细
            for (RefundItemDetail item : calculation.getRefundItems()) {
                RefundItem refundItem = new RefundItem();
                refundItem.setRefundId(refund.getRefundId());
                refundItem.setMerchantId(order.getMerchantId());
                refundItem.setStoreId(order.getStoreId()); // 设置 storeId（用于分片）
                // 设置 shardId（与退款单保持一致）
                refundItem.setShardId(refund.getShardId());
                refundItem.setOrderItemId(item.getOrderItemId());
                refundItem.setSubject(item.getSubject());
                refundItem.setRefundQty(item.getRefundQty());
                refundItem.setRefundAmount(item.getRefundAmount());
                refundItem.setTaxRefund(item.getTaxRefund());
                refundItem.setDiscountRefund(item.getDiscountRefund());
                refundItem.setCreatedAt(LocalDateTime.now());
                
                refundItemMapper.insert(refundItem);
            }
            
            // 12. 异步处理退款（调用第三方支付平台）
            processRefundAsync(refund, payment);
            
            return convertToResponse(refund);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 计算累计已退款金额
     */
    private BigDecimal calculateTotalRefunded(Long orderId) {
        List<Refund> refunds = refundMapper.selectByOrderId(orderId);
        return refunds.stream()
            .filter(r -> RefundStatus.SUCCEEDED.getCode().equals(r.getStatus()))
            .map(Refund::getRefundAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 解析订单总金额
     */
    @SuppressWarnings("unchecked")
    private BigDecimal parseOrderPrice(Order order) {
        if (order.getOrderPrice() == null) {
            throw new RuntimeException("订单价格信息为空");
        }
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr.startsWith("{")) {
                Map<String, Object> orderPrice = (Map<String, Object>) objectMapper.readValue(orderPriceStr, Map.class);
                Object totalObj = orderPrice.get("total");
                if (totalObj != null) {
                    return new BigDecimal(totalObj.toString());
                }
            }
            throw new RuntimeException("订单价格格式错误");
        } catch (Exception e) {
            throw new RuntimeException("解析订单价格失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从订单价格中获取平台抽成（charge）
     */
    @SuppressWarnings("unchecked")
    private BigDecimal getChargeFromOrderPrice(Order order) {
        if (order.getOrderPrice() == null) {
            return BigDecimal.ZERO;
        }
        try {
            String orderPriceStr = order.getOrderPrice();
            if (orderPriceStr.startsWith("{")) {
                Map<String, Object> orderPrice = (Map<String, Object>) objectMapper.readValue(orderPriceStr, Map.class);
                Object chargeObj = orderPrice.get("charge");
                if (chargeObj != null) {
                    return new BigDecimal(chargeObj.toString());
                }
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("解析订单平台抽成失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 异步处理退款（调用第三方支付平台）
     */
    @Async
    public void processRefundAsync(Refund refund, Payment payment) {
        try {
            // 使用状态机校验状态转换：CREATED -> PROCESSING
            RefundStatus currentStatus = RefundStatus.fromCode(refund.getStatus());
            refundStateMachine.validateTransition(currentStatus, RefundStatus.PROCESSING);
            
            // 更新状态为处理中（原子更新，带版本号校验）
            int updated = refundMapper.updateStatusWithVersion(
                refund.getRefundId(), 
                RefundStatus.PROCESSING.getCode(), 
                null,
                refund.getVersion()
            );
            if (updated == 0) {
                throw new BusinessException("退款状态更新失败，可能已被其他线程修改");
            }
            
            // 使用 Adapter 模式调用第三方退款（带超时和 Fallback）
            com.jiaoyi.order.adapter.PaymentAdapter.RefundRequest adapterRequest = 
                new com.jiaoyi.order.adapter.PaymentAdapter.RefundRequest();
            adapterRequest.setPaymentService(payment.getPaymentService());
            adapterRequest.setPaymentIntentId(payment.getStripePaymentIntentId());
            adapterRequest.setThirdPartyTradeNo(payment.getThirdPartyTradeNo());
            adapterRequest.setRefundAmount(refund.getRefundAmount());
            adapterRequest.setReason(refund.getReason());
            adapterRequest.setRequestNo(refund.getRequestNo());
            
            // 调用 Adapter（带超时和 Fallback）
            com.jiaoyi.order.adapter.PaymentAdapter.RefundResult adapterResult = 
                paymentAdapterWithFallback.createRefundWithFallback(adapterRequest);
            
            String thirdPartyRefundId = adapterResult.getThirdPartyRefundId();
            
            // 如果 Adapter 返回失败，抛出异常
            if (!adapterResult.isSuccess() && adapterResult.getStatus() == com.jiaoyi.order.adapter.PaymentAdapter.RefundStatus.FAILED) {
                throw new RuntimeException("第三方退款失败: " + adapterResult.getErrorMessage());
            }
            
            // 如果 Fallback 返回 PROCESSING，记录日志但不抛出异常（等待重试）
            if (!adapterResult.isSuccess() && adapterResult.getStatus() == com.jiaoyi.order.adapter.PaymentAdapter.RefundStatus.PROCESSING) {
                log.warn("退款请求已进入 Fallback 流程，退款ID: {}, 请求号: {}, 将等待重试或人工处理", 
                    refund.getRefundId(), refund.getRequestNo());
                // 不抛出异常，保持 PROCESSING 状态，等待后续重试
                return;
            }
            
            // 使用状态机校验状态转换：PROCESSING -> SUCCEEDED
            Refund currentRefundForSuccess = refundMapper.selectById(refund.getRefundId());
            if (currentRefundForSuccess == null) {
                throw new BusinessException("退款单不存在");
            }
            RefundStatus currentStatusForSuccess = RefundStatus.fromCode(currentRefundForSuccess.getStatus());
            refundStateMachine.validateTransition(currentStatusForSuccess, RefundStatus.SUCCEEDED);
            
            // 更新退款状态（原子更新，带版本号校验）
            if (thirdPartyRefundId != null) {
                int updatedSuccess = refundMapper.updateStatusWithVersion(
                    refund.getRefundId(), 
                    RefundStatus.SUCCEEDED.getCode(), 
                    thirdPartyRefundId,
                    currentRefundForSuccess.getVersion()
                );
                if (updatedSuccess == 0) {
                    throw new BusinessException("退款状态更新失败，可能已被其他线程修改");
                }
                
                // 更新订单退款金额（累加）
                updateOrderRefundAmount(refund.getOrderId());
                
                log.info("退款处理成功，退款ID: {}, 第三方退款ID: {}", 
                    refund.getRefundId(), thirdPartyRefundId);
            } else {
                refundMapper.updateStatusWithError(
                    refund.getRefundId(), 
                    RefundStatus.FAILED.getCode(), 
                    "第三方退款返回空"
                );
            }
            
        } catch (Exception e) {
            log.error("处理退款失败，退款ID: {}", refund.getRefundId(), e);
            
            // 使用状态机校验状态转换：PROCESSING -> FAILED
            Refund currentRefund = refundMapper.selectById(refund.getRefundId());
            if (currentRefund != null) {
                try {
                    RefundStatus currentStatus = RefundStatus.fromCode(currentRefund.getStatus());
                    refundStateMachine.validateTransition(currentStatus, RefundStatus.FAILED);
                    
                    // 更新退款状态（原子更新，带版本号校验）
                    int updated = refundMapper.updateStatusWithErrorAndVersion(
                        refund.getRefundId(), 
                        RefundStatus.FAILED.getCode(), 
                        e.getMessage(),
                        currentRefund.getVersion()
                    );
                    if (updated == 0) {
                        log.warn("退款状态更新失败，可能已被其他线程修改，退款ID: {}", refund.getRefundId());
                    }
                } catch (BusinessException be) {
                    log.warn("退款状态转换校验失败: {}", be.getMessage());
                }
            }
        }
    }
    
    /**
     * 处理退款回调（Stripe/Alipay）
     */
    @Transactional
    public void handleRefundCallback(String thirdPartyRefundId, RefundStatus targetStatus) {
        // 1. 查询退款单
        Refund refund = refundMapper.selectByThirdPartyRefundId(thirdPartyRefundId);
        if (refund == null) {
            log.warn("未找到退款单，第三方退款ID: {}", thirdPartyRefundId);
            return;
        }
        
        // 2. 幂等性检查：如果已经是目标状态，直接返回
        RefundStatus currentStatus = RefundStatus.fromCode(refund.getStatus());
        if (currentStatus == targetStatus) {
            log.info("退款回调已处理（幂等），退款ID: {}, 状态: {}", refund.getRefundId(), targetStatus.getDescription());
            return;
        }
        
        // 3. 使用状态机校验状态转换
        refundStateMachine.validateTransition(currentStatus, targetStatus);
        
        // 4. 更新退款状态（原子更新，带版本号校验）
        int updated = refundMapper.updateStatusWithVersion(
            refund.getRefundId(), 
            targetStatus.getCode(), 
            thirdPartyRefundId,
            refund.getVersion()
        );
        if (updated == 0) {
            log.warn("退款状态更新失败，可能已被其他线程修改，退款ID: {}", refund.getRefundId());
            return;
        }
        
        // 5. 如果成功，更新订单退款金额
        if (RefundStatus.SUCCEEDED.equals(targetStatus)) {
            updateOrderRefundAmount(refund.getOrderId());
        }
    }
    
    /**
     * 生成请求指纹（用于幂等性校验）
     * MD5(requestNo + orderId + amount + timestamp)
     */
    private String generateFingerprint(RefundRequest request) {
        try {
            String data = request.getRequestNo() + "|" + 
                         request.getOrderId() + "|" +
                         (request.getRefundAmount() != null ? request.getRefundAmount().toString() : "") + "|" +
                         System.currentTimeMillis();
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("生成请求指纹失败", e);
            return request.getRequestNo(); // 降级：使用请求号作为指纹
        }
    }
    
    /**
     * 更新订单退款金额（累加）
     */
    private void updateOrderRefundAmount(Long orderId) {
        BigDecimal totalRefunded = calculateTotalRefunded(orderId);
        orderMapper.updateRefundAmount(orderId, totalRefunded);
        
        // 如果全额退款，更新订单状态
        Order order = orderMapper.selectById(orderId);
        BigDecimal totalPaid = parseOrderPrice(order);
        if (totalRefunded.compareTo(totalPaid) >= 0) {
            orderMapper.updateStatus(orderId, OrderStatusEnum.REFUNDED.getCode());
            log.info("订单已全额退款，更新订单状态为已退款，订单ID: {}", orderId);
        }
    }
    
    /**
     * 查询退款单详情
     */
    public RefundResponse getRefundDetail(Long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new RuntimeException("退款单不存在");
        }
        return convertToResponse(refund);
    }
    
    /**
     * 查询订单的退款列表
     */
    public List<RefundResponse> getRefundsByOrderId(Long orderId) {
        List<Refund> refunds = refundMapper.selectByOrderId(orderId);
        return refunds.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * 转换为响应 DTO
     */
    private RefundResponse convertToResponse(Refund refund) {
        RefundResponse response = new RefundResponse();
        response.setRefundId(refund.getRefundId());
        response.setOrderId(refund.getOrderId());
        response.setRequestNo(refund.getRequestNo());
        response.setRefundAmount(refund.getRefundAmount());
        response.setReason(refund.getReason());
        response.setStatus(refund.getStatus());
        response.setThirdPartyRefundId(refund.getThirdPartyRefundId());
        response.setErrorMessage(refund.getErrorMessage());
        response.setCommissionReversal(refund.getCommissionReversal());
        response.setCreatedAt(refund.getCreatedAt());
        response.setProcessedAt(refund.getProcessedAt());
        
        // 查询退款明细
        List<RefundItem> refundItems = refundItemMapper.selectByRefundId(refund.getRefundId());
        List<RefundItemResponse> itemResponses = refundItems.stream()
            .map(item -> {
                RefundItemResponse itemResponse = new RefundItemResponse();
                itemResponse.setRefundItemId(item.getRefundItemId());
                itemResponse.setOrderItemId(item.getOrderItemId());
                itemResponse.setSubject(item.getSubject());
                itemResponse.setRefundQty(item.getRefundQty());
                itemResponse.setRefundAmount(item.getRefundAmount());
                itemResponse.setTaxRefund(item.getTaxRefund());
                itemResponse.setDiscountRefund(item.getDiscountRefund());
                return itemResponse;
            })
            .collect(Collectors.toList());
        response.setRefundItems(itemResponses);
        
        return response;
    }
}

