package com.jiaoyi.order.task;

import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.UserOrderIndex;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.UserOrderIndexMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户订单索引表补偿任务
 *
 * 用途：修复索引表数据不一致的情况
 *
 * 场景：
 * 1. 索引表写入失败（事务未回滚，订单创建成功但索引未写入）
 * 2. 历史数据迁移（订单表有数据，但索引表为空）
 *
 * 策略：
 * - 每天凌晨 4 点执行一次
 * - 查询最近 7 天的订单，检查索引表是否存在
 * - 如果不存在，补写索引记录
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserOrderIndexRepairTask {

    private final OrderMapper orderMapper;
    private final UserOrderIndexMapper userOrderIndexMapper;
    private final ObjectMapper objectMapper;

    /**
     * 补偿任务：每天凌晨 4 点执行
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void repairUserOrderIndex() {
        log.info("【UserOrderIndexRepairTask】开始执行用户订单索引表补偿任务");

        try {
            // 1. 查询最近 7 天的订单（可配置）
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<Order> recentOrders = orderMapper.selectTimeoutOrders(sevenDaysAgo);

            if (recentOrders == null || recentOrders.isEmpty()) {
                log.info("【UserOrderIndexRepairTask】没有需要补偿的订单");
                return;
            }

            log.info("【UserOrderIndexRepairTask】查询到 {} 个最近 7 天的订单，开始检查索引表", recentOrders.size());

            int repairedCount = 0;
            int errorCount = 0;

            // 2. 逐个检查索引表是否存在
            for (Order order : recentOrders) {
                try {
                    // 2.1 查询索引表（会触发广播查询，但补偿任务低频执行，可以接受）
                    UserOrderIndex existingIndex = userOrderIndexMapper.selectByOrderId(order.getId());

                    // 2.2 如果索引不存在，补写
                    if (existingIndex == null) {
                        UserOrderIndex newIndex = UserOrderIndex.builder()
                                .userId(order.getUserId())
                                .orderId(order.getId())
                                .storeId(order.getStoreId())
                                .merchantId(order.getMerchantId())
                                .orderStatus(order.getStatus())
                                .orderType(order.getOrderType() != null ? order.getOrderType().getCode() : null)
                                .totalAmount(extractTotalAmount(order))
                                .createdAt(order.getCreateTime() != null ? order.getCreateTime() : LocalDateTime.now())
                                .build();

                        userOrderIndexMapper.insert(newIndex);
                        repairedCount++;

                        log.info("【UserOrderIndexRepairTask】补写索引记录，orderId: {}, userId: {}",
                                order.getId(), order.getUserId());
                    }

                } catch (Exception e) {
                    log.error("【UserOrderIndexRepairTask】补偿失败，orderId: {}", order.getId(), e);
                    errorCount++;
                }
            }

            log.info("【UserOrderIndexRepairTask】补偿任务执行完成，补偿记录数: {}, 失败数: {}", repairedCount, errorCount);

        } catch (Exception e) {
            log.error("【UserOrderIndexRepairTask】补偿任务执行失败", e);
        }
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
     * 手动触发补偿任务（用于测试或紧急修复）
     */
    public void manualRepair() {
        log.info("【UserOrderIndexRepairTask】手动触发补偿任务");
        repairUserOrderIndex();
    }
}
