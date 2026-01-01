package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.DoorDashRetryTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DoorDash 重试任务 Mapper
 */
@Mapper
public interface DoorDashRetryTaskMapper {
    
    /**
     * 插入重试任务
     */
    int insert(DoorDashRetryTask task);
    
    /**
     * 根据ID查询任务
     */
    DoorDashRetryTask selectById(@Param("id") Long id);
    
    /**
     * 根据订单ID查询任务
     */
    DoorDashRetryTask selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据商户ID和订单ID查询任务（推荐，包含分片键）
     */
    DoorDashRetryTask selectByMerchantIdAndOrderId(
            @Param("merchantId") String merchantId,
            @Param("orderId") Long orderId
    );
    
    /**
     * 查询待重试的任务（状态为 PENDING 且 nextRetryTime <= 当前时间）
     */
    List<DoorDashRetryTask> selectPendingTasks(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * 查询需要人工介入的任务（状态为 FAILED 或 MANUAL）
     */
    List<DoorDashRetryTask> selectManualInterventionTasks(
            @Param("merchantId") String merchantId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );
    
    /**
     * 更新任务状态
     */
    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("errorStack") String errorStack
    );
    
    /**
     * 更新重试信息（重试次数、下次重试时间、最后重试时间）
     */
    int updateRetryInfo(
            @Param("id") Long id,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastRetryTime") LocalDateTime lastRetryTime,
            @Param("errorMessage") String errorMessage,
            @Param("errorStack") String errorStack
    );
    
    /**
     * 更新任务为成功状态
     */
    int updateSuccess(
            @Param("id") Long id,
            @Param("successTime") LocalDateTime successTime
    );
    
    /**
     * 更新任务为需要人工介入状态
     */
    int updateManualIntervention(
            @Param("id") Long id,
            @Param("manualInterventionTime") LocalDateTime manualInterventionTime,
            @Param("manualInterventionNote") String manualInterventionNote
    );
    
    /**
     * 统计各状态的任务数量（优化：直接在 SQL 中统计）
     */
    List<Map<String, Object>> countByStatus(@Param("merchantId") String merchantId);
}

