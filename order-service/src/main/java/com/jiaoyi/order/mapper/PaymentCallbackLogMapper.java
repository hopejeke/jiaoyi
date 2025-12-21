package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付回调日志 Mapper
 */
@Mapper
public interface PaymentCallbackLogMapper {
    
    /**
     * 插入支付回调日志
     */
    int insert(PaymentCallbackLog log);
    
    /**
     * 根据第三方交易号查询（用于幂等性检查）
     */
    PaymentCallbackLog selectByThirdPartyTradeNo(@Param("thirdPartyTradeNo") String thirdPartyTradeNo);
    
    /**
     * 更新处理状态
     */
    int updateStatus(@Param("id") Long id, 
                     @Param("status") com.jiaoyi.order.enums.PaymentCallbackLogStatusEnum status, 
                     @Param("result") String result, 
                     @Param("errorMessage") String errorMessage);
}

