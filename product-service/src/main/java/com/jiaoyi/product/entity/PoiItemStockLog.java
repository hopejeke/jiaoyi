package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存变更记录表实体类
 * 对应 poi_item_stock_log 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoiItemStockLog {
    
    private Long id;
    
    /**
     * 品牌ID
     */
    private String brandId;
    
    /**
     * 门店ID
     */
    private String storeId;
    
    /**
     * 关联 poi_item_stock.id
     */
    private Long stockId;
    
    /**
     * 变更内容（JSON格式）
     */
    private String content;
    
    /**
     * 变更类型：ABSOLUTE_SET-绝对设置, RELATIVE_DELTA-相对变更, STATUS_CHANGE-状态变更
     */
    private String changeType = "ABSOLUTE_SET";
    
    /**
     * 变更量（RELATIVE_DELTA时有效，负数表示扣减）
     */
    private java.math.BigDecimal delta = java.math.BigDecimal.ZERO;
    
    /**
     * 来源：POS-POS在线, CLOUD-商品中心, POS_OFFLINE-POS离线回放
     */
    private String source = "CLOUD";
    
    /**
     * 关联订单ID（用于幂等，RELATIVE_DELTA时必填）
     */
    private String orderId;

    /**
     * 扣减来源（仅 RELATIVE_DELTA 扣减时填）：FROM_CHANNEL-从渠道额度扣, FROM_SHARED_POOL-从共享池借
     * 用于订单取消时按源头归还
     */
    private String deductSource;

    /**
     * 渠道代码（扣减时必填，用于按源头归还时知道还到哪个渠道）
     */
    private String channelCode;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
