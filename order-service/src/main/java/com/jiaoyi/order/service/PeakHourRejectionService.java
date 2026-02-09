package com.jiaoyi.order.service;

import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.entity.CapabilityOfOrder;
import com.jiaoyi.order.entity.CapabilityOfOrderConfig;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.enums.OrderStatusEnum;
import com.jiaoyi.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 高峰拒单服务
 * 实现订单接单能力限制（Capability of Order）功能
 * 用于在高峰时段自动限制订单数量，防止商家接单过多导致服务质量下降
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PeakHourRejectionService {

    private final OrderMapper orderMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${peak-hour.use-redis:false}")
    private boolean useRedis;
    
    /**
     * 判断是否允许接单（高峰拒单检查）
     * 
     * @param merchantId 商户ID
     * @param config 限流配置
     * @param currentCapability 当前能力状态
     * @return 判断结果
     */
    public PeakHourRejectionResult judgeCapabilityOfOrder(
            String merchantId,
            CapabilityOfOrderConfig config,
            CapabilityOfOrder currentCapability) {
        
        // 1. 检查配置是否启用
        if (config == null || !Boolean.TRUE.equals(config.getEnable())) {
            log.debug("商户 {} 的高峰拒单功能未启用", merchantId);
            return PeakHourRejectionResult.enableOrder();
        }
        
        long now = System.currentTimeMillis();
        CapabilityOfOrder capability = currentCapability != null ? currentCapability : new CapabilityOfOrder();
        
        // 2. 检查是否在限流期间
        if (capability.getNextOpenAt() != null && capability.getNextOpenAt() > now) {
            log.info("商户 {} 仍在限流期间，下次开放时间: {}", 
                merchantId, capability.getNextOpenAt());
            return PeakHourRejectionResult.rejectOrder(capability.getNextOpenAt());
        }
        
        // 3. 计算时间窗口
        long resetTime = capability.getReOpenAllAt() != null ? capability.getReOpenAllAt() : 0;
        // 仅判断2.5小时内的订单数量（目前可以设置的临时关闭最大为2小时）
        resetTime = Math.max(resetTime, now - (long)(2.5 * 3600 * 1000));
        
        long timeInterval = Math.max(
            resetTime,
            now - (config.getTimeInterval() * 60 * 1000L)
        );
        
        log.debug("商户 {} 统计订单时间窗口: {} - {}", merchantId, timeInterval, now);

        // 4. 统计时间窗口内的订单数量（支持Redis优化）
        int qtyOfOrder = useRedis && redisTemplate != null
            ? countOrdersByRedis(merchantId, config.getTimeInterval())
            : countOrdersByDatabase(merchantId, timeInterval, now);

        log.info("商户 {} 在时间窗口内订单数量: {} (方式: {}), 阈值: {}",
            merchantId, qtyOfOrder, useRedis ? "Redis" : "DB", config.getQtyOfOrders());
        
        // 5. 判断是否达到限流条件
        if (qtyOfOrder >= config.getQtyOfOrders()) {
            // 触发限流
            long nextOpenAt = now + (config.getClosingDuration() * 60 * 1000L);
            log.warn("商户 {} 触发高峰限流，订单数: {}, 阈值: {}, 下次开放时间: {}", 
                merchantId, qtyOfOrder, config.getQtyOfOrders(), nextOpenAt);
            
            return PeakHourRejectionResult.triggerRejection(nextOpenAt);
        } else {
            // 未达到限流条件，允许接单
            log.debug("商户 {} 未达到限流条件，允许接单", merchantId);
            return PeakHourRejectionResult.enableOrder();
        }
    }

    /**
     * 记录订单到Redis（订单创建成功后调用）
     */
    public void recordOrder(String merchantId, Long orderId) {
        if (!useRedis || redisTemplate == null) {
            return;
        }

        try {
            String key = "merchant:orders:" + merchantId;
            long timestamp = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(key, orderId.toString(), timestamp);
            redisTemplate.expire(key, 1, java.util.concurrent.TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("记录订单到Redis失败: merchantId={}, orderId={}", merchantId, orderId, e);
        }
    }

    /**
     * 从Redis移除订单（订单取消时调用）
     */
    public void removeOrder(String merchantId, Long orderId) {
        if (!useRedis || redisTemplate == null) {
            return;
        }

        try {
            String key = "merchant:orders:" + merchantId;
            redisTemplate.opsForZSet().remove(key, orderId.toString());
        } catch (Exception e) {
            log.error("从Redis移除订单失败: merchantId={}, orderId={}", merchantId, orderId, e);
        }
    }

    /**
     * 使用Redis统计订单数
     */
    private int countOrdersByRedis(String merchantId, int windowMinutes) {
        try {
            String key = "merchant:orders:" + merchantId;
            long now = System.currentTimeMillis();
            long windowStart = now - windowMinutes * 60 * 1000L;

            // 清理过期数据
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // 统计窗口内数量
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            return count == null ? 0 : count.intValue();

        } catch (Exception e) {
            log.error("Redis统计失败，降级到DB查询: merchantId={}", merchantId, e);
            return countOrdersByDatabase(merchantId,
                System.currentTimeMillis() - windowMinutes * 60 * 1000L,
                System.currentTimeMillis());
        }
    }

    /**
     * 使用DB统计订单数
     */
    private int countOrdersByDatabase(String merchantId, long startMillis, long endMillis) {
        LocalDateTime startTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(startMillis),
            ZoneId.systemDefault()
        );
        LocalDateTime endTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(endMillis),
            ZoneId.systemDefault()
        );

        List<Order> orderList = orderMapper.selectByMerchantIdAndTimeRange(
            merchantId, startTime, endTime);

        int count = 0;
        for (Order order : orderList) {
            if (order.getId() != null && order.getStatus() != null) {
                Integer status = order.getStatus();
                if (!status.equals(-1) && !status.equals(OrderStatusEnum.CANCELLED.getCode())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 高峰拒单判断结果
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PeakHourRejectionResult {
        /**
         * 是否允许接单
         */
        private boolean enableOrder;
        
        /**
         * 下次开放时间（如果被限流）
         */
        private Long nextOpenAt;
        
        /**
         * 是否需要更新商户状态（关闭/打开服务）
         */
        private boolean needUpdateMerchant;
        
        /**
         * 是否触发限流
         */
        private boolean triggered;
        
        public static PeakHourRejectionResult enableOrder() {
            return new PeakHourRejectionResult(true, null, false, false);
        }
        
        public static PeakHourRejectionResult rejectOrder(Long nextOpenAt) {
            return new PeakHourRejectionResult(false, nextOpenAt, false, false);
        }
        
        public static PeakHourRejectionResult triggerRejection(Long nextOpenAt) {
            return new PeakHourRejectionResult(false, nextOpenAt, true, true);
        }
    }
}

