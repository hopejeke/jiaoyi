package com.jiaoyi.order.service;

import com.jiaoyi.order.client.CouponServiceClient;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.config.RocketMQConfig;
import com.jiaoyi.order.dto.OrderTimeoutMessage;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderStatus;
import com.jiaoyi.order.entity.OrderCoupon;
import com.jiaoyi.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.support.MessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单超时消息服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
    topic = RocketMQConfig.ORDER_TIMEOUT_TOPIC,
    consumerGroup = RocketMQConfig.ORDER_TIMEOUT_CONSUMER_GROUP,
    selectorExpression = RocketMQConfig.ORDER_TIMEOUT_TAG
)
public class OrderTimeoutMessageService implements RocketMQListener<OrderTimeoutMessage> {

    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final RedissonClient redissonClient;
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送订单超时延迟消息
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param userId 用户ID
     * @param delayMinutes 延迟分钟数
     */
    public void sendOrderTimeoutMessage(Long orderId, String orderNo, Long userId, int delayMinutes) {
        try {
            // 检查RocketMQ是否可用
            if (rocketMQTemplate == null) {
                log.warn("RocketMQ不可用，跳过发送延迟消息，订单ID: {}, 订单号: {}", orderId, orderNo);
                return;
            }
            
            OrderTimeoutMessage message = new OrderTimeoutMessage(orderId, orderNo, userId);
            message.setTimeoutMillis((long) delayMinutes * 60 * 1000); // 转换为毫秒
            
            log.info("发送订单超时延迟消息，订单ID: {}, 订单号: {}, 延迟: {}分钟", orderId, orderNo, delayMinutes);
            
            // 计算延迟级别（RocketMQ延迟消息级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h）
            int delayLevel = calculateDelayLevel(delayMinutes);
            
            // 发送延迟消息
            String destination = RocketMQConfig.ORDER_TIMEOUT_TOPIC + ":" + RocketMQConfig.ORDER_TIMEOUT_TAG;
            org.springframework.messaging.Message<OrderTimeoutMessage> springMessage = MessageBuilder
                .withPayload(message)
                .build();
            rocketMQTemplate.syncSend(destination, springMessage, 3000, delayLevel);
            
            log.info("订单超时延迟消息发送成功，订单ID: {}, 延迟级别: {}", orderId, delayLevel);
            
        } catch (Exception e) {
            log.error("发送订单超时延迟消息失败，订单ID: {}", orderId, e);
        }
    }
    
    /**
     * 计算延迟级别
     * RocketMQ延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * 对应级别：      1  2  3   4  5  6  7  8  9 10 11 12 13 14 15 16 17 18
     * 所以：1分钟=级别5, 2分钟=级别6, ..., 30分钟=级别16
     */
    private int calculateDelayLevel(int delayMinutes) {
        if (delayMinutes <= 1) return 5;      // 1分钟 -> 级别5
        if (delayMinutes <= 2) return 6;      // 2分钟 -> 级别6
        if (delayMinutes <= 3) return 7;      // 3分钟 -> 级别7
        if (delayMinutes <= 4) return 8;      // 4分钟 -> 级别8
        if (delayMinutes <= 5) return 9;      // 5分钟 -> 级别9
        if (delayMinutes <= 6) return 10;     // 6分钟 -> 级别10
        if (delayMinutes <= 7) return 11;     // 7分钟 -> 级别11
        if (delayMinutes <= 8) return 12;     // 8分钟 -> 级别12
        if (delayMinutes <= 9) return 13;     // 9分钟 -> 级别13
        if (delayMinutes <= 10) return 14;    // 10分钟 -> 级别14
        if (delayMinutes <= 20) return 15;    // 20分钟 -> 级别15
        if (delayMinutes <= 30) return 16;    // 30分钟 -> 级别16
        if (delayMinutes <= 60) return 17;    // 1小时 -> 级别17
        return 18; // 2小时 -> 级别18（最大延迟）
    }

    /**
     * RocketMQ消息监听器（需要RocketMQ服务启动时才能使用）
     * 注意：这个方法只有在RocketMQ服务启动时才会被调用
     */
    @Override
    @Transactional
    public void onMessage(OrderTimeoutMessage message) {
        handleOrderTimeout(message);
    }

    /**
     * 处理订单超时消息
     */
    @Transactional
    public void handleOrderTimeout(OrderTimeoutMessage message) {
        log.info("接收到订单超时消息，订单ID: {}, 订单号: {}", message.getOrderId(), message.getOrderNo());
        
        String lockKey = "order:timeout:" + message.getOrderId();
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取订单超时处理锁失败，订单ID: {}", message.getOrderId());
                return;
            }
            
            // 重新查询订单状态，确保订单仍然是待支付状态
            Order order = orderMapper.selectById(message.getOrderId());
            if (order == null || order.getStatus() != OrderStatus.PENDING) {
                log.info("订单状态已变更，跳过超时处理，订单ID: {}, 当前状态: {}", 
                        message.getOrderId(), order != null ? order.getStatus() : "订单不存在");
                return;
            }
            
            log.info("开始处理超时订单，订单ID: {}, 订单号: {}", order.getId(), order.getOrderNo());
            
            // 取消订单
            boolean cancelled = cancelTimeoutOrder(order);
            
            if (cancelled) {
                log.info("订单超时取消成功，订单ID: {}, 订单号: {}", order.getId(), order.getOrderNo());
            } else {
                log.warn("订单超时取消失败，订单ID: {}, 订单号: {}", order.getId(), order.getOrderNo());
            }
            
        } catch (InterruptedException e) {
            log.error("获取订单超时处理锁被中断，订单ID: {}", message.getOrderId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("处理订单超时消息时发生异常，订单ID: {}", message.getOrderId(), e);
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 取消超时订单
     */
    private boolean cancelTimeoutOrder(Order order) {
        try {
            // 1. 原子更新订单状态：只有当状态为PENDING时才更新为CANCELLED
            int updatedRows = orderMapper.updateStatusIfPending(order.getId(), OrderStatus.CANCELLED);
            
            if (updatedRows == 0) {
                log.info("订单状态已变更，取消超时处理，订单ID: {}", order.getId());
                return false;
            }
            
            log.info("订单超时取消成功，订单ID: {}, 订单号: {}", order.getId(), order.getOrderNo());
            
            // 2. 解锁库存
            if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                List<Long> productIds = order.getOrderItems().stream()
                        .map(item -> item.getProductId())
                        .collect(Collectors.toList());
                List<Integer> quantities = order.getOrderItems().stream()
                        .map(item -> item.getQuantity())
                        .collect(Collectors.toList());
                
                ProductServiceClient.UnlockStockBatchRequest unlockRequest = new ProductServiceClient.UnlockStockBatchRequest();
                unlockRequest.setProductIds(productIds);
                unlockRequest.setQuantities(quantities);
                unlockRequest.setOrderId(order.getId());
                productServiceClient.unlockStockBatch(unlockRequest);
                log.info("超时订单库存解锁成功，订单ID: {}", order.getId());
            }
            
            // 3. 退还优惠券
            if (order.getOrderCoupons() != null && !order.getOrderCoupons().isEmpty()) {
                for (OrderCoupon orderCoupon : order.getOrderCoupons()) {
                    couponServiceClient.refundCoupon(orderCoupon.getCouponId());
                    log.info("超时订单优惠券退还成功，订单ID: {}, 优惠券ID: {}, 优惠券代码: {}", 
                            order.getId(), orderCoupon.getCouponId(), orderCoupon.getCouponCode());
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("取消超时订单失败，订单ID: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * 手动取消指定订单（用于测试）
     */
    @Transactional
    public boolean cancelOrderManually(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，订单ID: {}", orderId);
            return false;
        }
        
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("订单状态不是待支付，无法取消，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
            return false;
        }
        
        return cancelTimeoutOrder(order);
    }
}


