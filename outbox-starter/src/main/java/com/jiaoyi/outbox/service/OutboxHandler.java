package com.jiaoyi.outbox.service;

import com.jiaoyi.outbox.entity.Outbox;

/**
 * Outbox 任务处理器接口
 * 策略模式：不同的 type 对应不同的 handler
 */
public interface OutboxHandler {
    
    /**
     * 判断是否支持该类型
     * @param type 任务类型
     * @return true 如果支持
     */
    boolean supports(String type);
    
    /**
     * 处理任务
     * @param outbox Outbox 记录
     * @throws Exception 处理失败时抛出异常
     */
    void handle(Outbox outbox) throws Exception;
}



