package com.jiaoyi.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 渠道库存扣减结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDeductResult {
    
    public enum Status {
        /** 扣减成功（从渠道额度扣） */
        SUCCESS_FROM_CHANNEL,
        /** 扣减成功（从共享池借） */
        SUCCESS_FROM_SHARED_POOL,
        /** 库存不足（渠道额度和共享池都不够） */
        OUT_OF_STOCK,
        /** 该渠道已售罄 */
        CHANNEL_OUT_OF_STOCK,
        /** 重复请求（幂等，已扣过） */
        DUPLICATE
    }
    
    private Status status;
    
    /** 渠道剩余额度 */
    private BigDecimal channelRemaining;
    
    /** 共享池剩余 */
    private BigDecimal sharedPoolRemaining;
    
    private String message;
    
    public static ChannelDeductResult success(Status status, BigDecimal channelRemaining, BigDecimal sharedPoolRemaining) {
        return new ChannelDeductResult(status, channelRemaining, sharedPoolRemaining, "扣减成功");
    }
    
    public static ChannelDeductResult outOfStock(BigDecimal channelRemaining, BigDecimal sharedPoolRemaining) {
        return new ChannelDeductResult(Status.OUT_OF_STOCK, channelRemaining, sharedPoolRemaining, "库存不足");
    }
    
    public static ChannelDeductResult duplicate() {
        return new ChannelDeductResult(Status.DUPLICATE, null, null, "重复扣减请求");
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS_FROM_CHANNEL || status == Status.SUCCESS_FROM_SHARED_POOL;
    }
}
