package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变更事件DTO
 * 
 * 区分三种变更类型：
 * 1. ABSOLUTE_SET - 绝对设置（店长手动设库存=30）
 * 2. RELATIVE_DELTA - 相对变更（订单扣减-1）
 * 3. STATUS_CHANGE - 状态变更（设为售罄/恢复可售）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockChangeEvent {
    
    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        /** 绝对设置：店长手动设置库存为某个值 */
        ABSOLUTE_SET,
        /** 相对变更：订单扣减（delta为负）或补货（delta为正） */
        RELATIVE_DELTA,
        /** 状态变更：可售→售罄 或 售罄→可售 */
        STATUS_CHANGE
    }
    
    /**
     * 来源枚举
     */
    public enum Source {
        /** POS在线操作 */
        POS,
        /** 商品中心后台操作 */
        CLOUD,
        /** POS离线期间的操作（恢复后上报） */
        POS_OFFLINE
    }
    
    /** 品牌ID */
    private String brandId;
    
    /** 门店ID */
    private String poiId;
    
    /** 商品对象ID */
    private Long objectId;
    
    /** 变更类型 */
    private ChangeType changeType;
    
    /** 来源 */
    private Source source;
    
    /**
     * 变更量（RELATIVE_DELTA 时使用）
     * 负数表示扣减，正数表示补货
     */
    private BigDecimal delta;
    
    /**
     * 新库存值（ABSOLUTE_SET 时使用）
     */
    private BigDecimal newQuantity;
    
    /**
     * 新库存状态（STATUS_CHANGE 时使用）
     * 1-可售, 2-售罄
     */
    private Integer newStockStatus;
    
    /**
     * 关联订单ID（RELATIVE_DELTA 时必填，用于幂等）
     */
    private String orderId;
    
    /**
     * 操作时间（POS端实际操作时间，用于冲突合并）
     */
    private LocalDateTime operateTime;
    
    /**
     * 渠道代码（可选，用于渠道级别的扣减）
     * POS, KIOSK, ONLINE_ORDER
     */
    private String channelCode;
}
