package com.jiaoyi.dto;

import com.jiaoyi.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品缓存更新消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCacheUpdateMessage {
    
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
     * 商品ID
     */
    private Long productId;
    
    /**
     * 操作类型
     */
    private OperationType operationType;
    
    /**
     * 操作时间戳
     */
    private Long timestamp;
    
    /**
     * 消息版本号（用于幂等性控制）
     */
    private Long messageVersion;
    
    /**
     * 是否聚合库存信息
     */
    private Boolean enrichInventory;
    
    /**
     * 商品数据（用于CREATE和UPDATE操作，在executeLocalTransaction中执行数据库操作）
     * 注意：对于CREATE操作，如果ID是自增的，productId可能为null
     */
    private Product product;
}
