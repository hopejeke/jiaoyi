package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * POS离线事件回放结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PosOfflineReplayResult {
    
    /** 总事件数 */
    private int totalEvents;
    
    /** 成功回放数 */
    private int successCount;
    
    /** 跳过数（幂等去重） */
    private int skippedCount;
    
    /** 失败数 */
    private int failedCount;
    
    /** 是否检测到超卖 */
    private boolean oversellDetected;
    
    /** 超卖数量 */
    private BigDecimal oversellQuantity;
    
    /** 超卖记录ID（如果有） */
    private Long oversellRecordId;
    
    /** 失败的事件详情 */
    private List<FailedEvent> failedEvents = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedEvent {
        private String orderId;
        private String reason;
    }
}
