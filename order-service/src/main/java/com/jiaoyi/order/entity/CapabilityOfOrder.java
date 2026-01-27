package com.jiaoyi.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 高峰拒单能力配置
 * 用于在高峰时段自动限制订单数量，防止商家接单过多导致服务质量下降
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityOfOrder implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 下次开放时间（时间戳，毫秒）
     * 如果当前时间 < nextOpenAt，表示仍在限流期间，不能接单
     */
    private Long nextOpenAt;
    
    /**
     * 重新开放所有服务的时间（时间戳，毫秒）
     * 用于计算时间窗口的起始时间
     */
    private Long reOpenAllAt;
    
    /**
     * 操作类型标记
     * 'manual': 手动操作（商家手动关闭/打开）
     * 'system': 系统自动操作（限流自动关闭/打开）
     */
    private OperateType operate;
    
    /**
     * 操作类型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperateType implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * Pickup 服务操作类型
         */
        private String pickUp; // 'manual' | 'system'
        
        /**
         * Delivery 服务操作类型
         */
        private String delivery; // 'manual' | 'system'
        
        /**
         * Togo 服务操作类型
         */
        private String togo; // 'manual' | 'system'
        
        /**
         * SelfDineIn 服务操作类型
         */
        private String selfDineIn; // 'manual' | 'system'
    }
}





