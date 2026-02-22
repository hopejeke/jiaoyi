package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * POS离线事件批量回放请求DTO
 * 
 * POS网络恢复后，将离线期间的库存变更事件批量上报给商品中心。
 * 商品中心逐条回放，基于orderId做幂等，检测超卖并补偿。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PosOfflineReplayRequest {
    
    /** 品牌ID */
    private String brandId;
    
    /** 门店ID */
    private String storeId;
    
    /** POS实例标识（用于日志追踪） */
    private String posInstanceId;
    
    /** 离线期间的库存变更事件列表（按时间顺序） */
    private List<StockChangeEvent> events;
}
