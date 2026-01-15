package com.jiaoyi.order.service;

import com.jiaoyi.order.enums.RefundStatus;
import com.jiaoyi.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 退款状态机
 * 定义退款状态转换规则和校验逻辑
 * 
 * 状态转换图：
 * CREATED -> PROCESSING -> SUCCEEDED
 *          -> PROCESSING -> FAILED
 *          -> CANCELED
 */
@Component
@Slf4j
public class RefundStateMachine {
    
    /**
     * 状态转换规则映射
     * key: 当前状态, value: 允许转换到的状态集合
     */
    private static final Map<RefundStatus, Set<RefundStatus>> TRANSITION_RULES = new HashMap<>();
    
    static {
        // CREATED 可以转换到 PROCESSING 或 CANCELED
        TRANSITION_RULES.put(RefundStatus.CREATED, Set.of(
            RefundStatus.PROCESSING,
            RefundStatus.CANCELED
        ));
        
        // PROCESSING 可以转换到 SUCCEEDED 或 FAILED
        TRANSITION_RULES.put(RefundStatus.PROCESSING, Set.of(
            RefundStatus.SUCCEEDED,
            RefundStatus.FAILED
        ));
        
        // SUCCEEDED, FAILED, CANCELED 是终态，不允许再转换
        TRANSITION_RULES.put(RefundStatus.SUCCEEDED, Collections.emptySet());
        TRANSITION_RULES.put(RefundStatus.FAILED, Collections.emptySet());
        TRANSITION_RULES.put(RefundStatus.CANCELED, Collections.emptySet());
    }
    
    /**
     * 检查状态转换是否合法
     * 
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return true 如果转换合法，否则抛出异常
     * @throws BusinessException 如果转换不合法
     */
    public boolean canTransition(RefundStatus currentStatus, RefundStatus targetStatus) {
        if (currentStatus == null) {
            throw new BusinessException("当前状态不能为空");
        }
        if (targetStatus == null) {
            throw new BusinessException("目标状态不能为空");
        }
        
        // 相同状态，允许（幂等）
        if (currentStatus == targetStatus) {
            return true;
        }
        
        Set<RefundStatus> allowedTransitions = TRANSITION_RULES.get(currentStatus);
        if (allowedTransitions == null) {
            throw new BusinessException("未知的当前状态: " + currentStatus);
        }
        
        if (!allowedTransitions.contains(targetStatus)) {
            throw new BusinessException(
                String.format("退款状态转换不合法: %s -> %s。允许的转换: %s",
                    currentStatus.getDescription(),
                    targetStatus.getDescription(),
                    allowedTransitions.isEmpty() ? "无（终态）" : allowedTransitions
                )
            );
        }
        
        return true;
    }
    
    /**
     * 验证并执行状态转换（不实际更新数据库，只做校验）
     * 
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @throws BusinessException 如果转换不合法
     */
    public void validateTransition(RefundStatus currentStatus, RefundStatus targetStatus) {
        canTransition(currentStatus, targetStatus);
        log.debug("退款状态转换验证通过: {} -> {}", currentStatus.getDescription(), targetStatus.getDescription());
    }
    
    /**
     * 获取允许的状态转换列表
     * 
     * @param currentStatus 当前状态
     * @return 允许转换到的状态集合
     */
    public Set<RefundStatus> getAllowedTransitions(RefundStatus currentStatus) {
        return TRANSITION_RULES.getOrDefault(currentStatus, Collections.emptySet());
    }
    
    /**
     * 检查是否为终态
     * 
     * @param status 状态
     * @return true 如果是终态（SUCCEEDED, FAILED, CANCELED）
     */
    public boolean isTerminalStatus(RefundStatus status) {
        return status == RefundStatus.SUCCEEDED 
            || status == RefundStatus.FAILED 
            || status == RefundStatus.CANCELED;
    }
    
    /**
     * 检查是否可以取消
     * 
     * @param currentStatus 当前状态
     * @return true 如果可以取消（CREATED 状态）
     */
    public boolean canCancel(RefundStatus currentStatus) {
        return currentStatus == RefundStatus.CREATED;
    }
    
    /**
     * 检查是否可以重试（失败后可以重试）
     * 
     * @param currentStatus 当前状态
     * @return true 如果失败后可以重试
     */
    public boolean canRetry(RefundStatus currentStatus) {
        return currentStatus == RefundStatus.FAILED;
    }
}






