package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.RefundIdempotencyLog;
import org.apache.ibatis.annotations.Param;

/**
 * 退款幂等性日志 Mapper
 */
public interface RefundIdempotencyLogMapper {
    
    /**
     * 插入幂等性日志
     */
    int insert(RefundIdempotencyLog log);
    
    /**
     * 根据请求号查询幂等性日志
     * 
     * @param orderId 订单ID
     * @param requestNo 请求号
     * @return 幂等性日志
     */
    RefundIdempotencyLog selectByRequestNo(@Param("orderId") Long orderId, @Param("requestNo") String requestNo);
    
    /**
     * 根据指纹查询幂等性日志
     * 
     * @param fingerprint 请求指纹
     * @return 幂等性日志
     */
    RefundIdempotencyLog selectByFingerprint(@Param("fingerprint") String fingerprint);
    
    /**
     * 更新处理结果
     * 
     * @param id 主键ID
     * @param refundId 退款ID
     * @param result 处理结果
     * @param errorMessage 错误信息
     */
    void updateResult(@Param("id") Long id, 
                     @Param("refundId") Long refundId,
                     @Param("result") String result,
                     @Param("errorMessage") String errorMessage);
}






