package com.jiaoyi.dto;

import com.jiaoyi.entity.StoreProduct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店铺商品缓存更新消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreProductCacheUpdateMessage {
    
    /**
     * 操作类型：CREATE-创建, UPDATE-更新, DELETE-删除, REFRESH-刷新
     */
    public enum OperationType {
        CREATE,
        UPDATE,
        DELETE,
        REFRESH
    }
    
    /**
     * 店铺商品ID
     */
    private Long productId;
    
    /**
     * 店铺ID
     */
    private Long storeId;
    
    /**
     * 操作类型
     */
    private OperationType operationType;
    
    /**
     * 操作时间戳
     */
    private Long timestamp;
    
    /**
     * 是否聚合库存信息
     */
    private Boolean enrichInventory;
    
    /**
     * 店铺商品数据（用于CREATE和UPDATE操作，在executeLocalTransaction中执行数据库操作）
     * 注意：对于CREATE操作，如果ID是自增的，productId可能为null
     */
    private StoreProduct storeProduct;
}

