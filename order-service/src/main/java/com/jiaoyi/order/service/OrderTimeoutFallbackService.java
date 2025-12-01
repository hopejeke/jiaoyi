package com.jiaoyi.order.service;

import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderStatus;
import com.jiaoyi.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时兜底定时任务服务
 * 当消息系统出现故障时，通过定时任务确保超时订单被取消
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutFallbackService {

    private final OrderMapper orderMapper;
    private final RedissonClient redissonClient;

    @Value("${order.timeout.minutes:40}")
    private int timeoutMinutes;

    @Value("${order.timeout.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${order.timeout.fallback.interval:300000}")
    private long fallbackInterval; // 5分钟执行一次

    /**
     * 兜底定时任务 - 每5分钟执行一次
     * 检查是否有超时未支付的订单需要取消
     */
    @Scheduled(fixedDelayString = "${order.timeout.fallback.interval:300000}")
    public void checkTimeoutOrders() {
        if (!fallbackEnabled) {
            log.debug("订单超时兜底任务已禁用");
            return;
        }

        log.info("开始执行订单超时兜底检查任务");
        
        try {
            // 计算超时时间点
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
            
            // 查询超时未支付的订单
            List<Order> timeoutOrders = orderMapper.selectTimeoutOrders(timeoutThreshold);
            
            if (timeoutOrders.isEmpty()) {
                log.debug("未发现超时订单");
                return;
            }

            log.info("发现 {} 个超时订单需要处理", timeoutOrders.size());
            
            // 处理每个超时订单
            for (Order order : timeoutOrders) {
                processTimeoutOrder(order);
            }
            
        } catch (Exception e) {
            log.error("订单超时兜底任务执行异常", e);
        }
    }

    /**
     * 处理单个超时订单
     */
    @Transactional
    public void processTimeoutOrder(Order order) {
        String lockKey = "order:timeout:" + order.getId();
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待3秒，锁定时间30秒
            if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                try {
                    // 重新查询订单状态，确保订单仍然是待支付状态
                    Order currentOrder = orderMapper.selectById(order.getId());
                    if (currentOrder == null || !"PENDING".equals(currentOrder.getStatus().name())) {
                        log.debug("订单 {} 状态已变更，跳过处理", order.getId());
                        return;
                    }

                    // 检查订单是否真的超时
                    LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
                    if (currentOrder.getCreateTime().isAfter(timeoutThreshold)) {
                        log.debug("订单 {} 未超时，跳过处理", order.getId());
                        return;
                    }

                    // 执行订单取消
                    int updatedRows = orderMapper.updateStatusIfPending(order.getId(), OrderStatus.CANCELLED);
                    if (updatedRows > 0) {
                        log.info("兜底任务成功取消超时订单: {}, 创建时间: {}", 
                                order.getId(), order.getCreateTime());
                        
                        // 释放库存（通过 Feign Client 调用 product-service）
                        releaseInventoryForOrder(order);
                        
                    } else {
                        log.warn("兜底任务取消订单失败，订单可能已被其他进程处理: {}", order.getId());
                    }
                    
                } finally {
                    // 安全释放锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.debug("无法获取订单 {} 的锁，可能正在被其他进程处理", order.getId());
            }
            
        } catch (InterruptedException e) {
            log.warn("获取订单 {} 锁时被中断", order.getId());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("处理超时订单 {} 时发生异常", order.getId(), e);
        } finally {
            // 确保锁被释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 释放订单对应的库存
     * 注意：这里需要通过 Feign Client 调用 product-service 的库存服务
     */
    private void releaseInventoryForOrder(Order order) {
        try {
            // TODO: 通过 Feign Client 调用 product-service 的批量解锁库存接口
            // 需要查询订单项，然后调用批量解锁接口
            log.info("订单 {} 的库存已释放（需要实现 Feign Client 调用，查询订单项后批量解锁）", order.getId());
        } catch (Exception e) {
            log.error("释放订单 {} 库存时发生异常", order.getId(), e);
        }
    }

    /**
     * 手动触发兜底检查（用于测试或紧急情况）
     */
    public void manualCheck() {
        log.info("手动触发订单超时兜底检查");
        checkTimeoutOrders();
    }

    /**
     * 根据订单ID处理超时订单
     */
    public void processTimeoutOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            processTimeoutOrder(order);
        } else {
            log.warn("订单 {} 不存在", orderId);
        }
    }

    /**
     * 获取兜底任务状态
     */
    public String getFallbackStatus() {
        return String.format("兜底任务状态 - 启用: %s, 超时时间: %d分钟, 检查间隔: %d毫秒", 
                fallbackEnabled, timeoutMinutes, fallbackInterval);
    }
}

