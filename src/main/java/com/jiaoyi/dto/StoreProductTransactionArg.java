package com.jiaoyi.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 店铺商品事务消息参数容器
 * 用于在sendMessageInTransaction和executeLocalTransaction之间传递数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreProductTransactionArg {
    
    /**
     * 用于CREATE操作传递insert后的productId
     * executeLocalTransaction执行insert后，将ID设置到此AtomicReference中
     * sendMessageInTransaction返回后，可以从AtomicReference获取ID
     */
    private AtomicReference<Long> productIdRef;
    
    /**
     * 店铺ID
     */
    private Long storeId;
    
    /**
     * StoreProduct对象（用于CREATE和UPDATE操作）
     */
    private com.jiaoyi.entity.StoreProduct storeProduct;
}

