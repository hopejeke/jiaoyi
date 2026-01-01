package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 退款单 Mapper
 */
@Mapper
public interface RefundMapper {
    
    /**
     * 插入退款单
     */
    int insert(Refund refund);
    
    /**
     * 根据ID查询退款单
     */
    Refund selectById(@Param("refundId") Long refundId);
    
    /**
     * 根据订单ID查询退款单列表
     */
    List<Refund> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据请求号查询退款单（幂等性检查）
     */
    Refund selectByRequestNo(@Param("orderId") Long orderId, @Param("requestNo") String requestNo);
    
    /**
     * 根据第三方退款ID查询退款单
     */
    Refund selectByThirdPartyRefundId(@Param("thirdPartyRefundId") String thirdPartyRefundId);
    
    /**
     * 更新退款状态
     */
    int updateStatus(@Param("refundId") Long refundId, 
                     @Param("status") String status, 
                     @Param("thirdPartyRefundId") String thirdPartyRefundId);
    
    /**
     * 更新退款状态（带错误信息）
     */
    int updateStatusWithError(@Param("refundId") Long refundId, 
                              @Param("status") String status, 
                              @Param("errorMessage") String errorMessage);
    
    /**
     * 更新退款状态（带版本号，乐观锁）
     */
    int updateStatusWithVersion(@Param("refundId") Long refundId, 
                               @Param("status") String status, 
                               @Param("thirdPartyRefundId") String thirdPartyRefundId,
                               @Param("version") Long version);
    
    /**
     * 更新退款状态（带错误信息和版本号，乐观锁）
     */
    int updateStatusWithErrorAndVersion(@Param("refundId") Long refundId, 
                                       @Param("status") String status, 
                                       @Param("errorMessage") String errorMessage,
                                       @Param("version") Long version);
}

