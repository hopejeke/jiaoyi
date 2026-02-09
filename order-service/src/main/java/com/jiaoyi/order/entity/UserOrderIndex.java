package com.jiaoyi.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户订单索引表实体类
 *
 * 用途：解决按 userId 查询订单的广播查询问题
 *
 * 设计思路：
 * - 订单表（orders）按 store_id 分片，适合商户查询
 * - 索引表（user_order_index）按 user_id 分片，适合用户查询
 * - 索引表只存最小必要字段，减少存储成本
 *
 * 查询流程：
 * 1. 从索引表查询用户的订单ID列表（精准路由）
 * 2. 按 store_id 分组，批量查询订单详情（精准路由）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrderIndex {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID（分片键）
     */
    private Long userId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 商户ID（Long 类型，用于精准查询订单详情）
     */
    private Long storeId;

    /**
     * 商户ID（字符串形式）
     */
    private String merchantId;

    /**
     * 订单状态（冗余字段，用于快速过滤）
     * 1-已下单, 2-已支付, 3-制作中, 4-配送中, 5-已完成, 6-已取消
     */
    private Integer orderStatus;

    /**
     * 订单类型（冗余字段，用于快速过滤）
     * DELIVERY-外卖配送, PICKUP-到店自提, DINE_IN-堂食
     */
    private String orderType;

    /**
     * 订单总金额（冗余字段，用于展示）
     */
    private BigDecimal totalAmount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
