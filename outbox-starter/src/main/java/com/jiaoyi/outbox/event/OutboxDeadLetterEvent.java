package com.jiaoyi.outbox.event;

import com.jiaoyi.outbox.entity.Outbox;

/**
 * Outbox 死信事件
 * 当任务超过最大重试次数被标记为 DEAD 时发布
 */
public record OutboxDeadLetterEvent(Outbox outbox, String handlerName, int retryCount) {
}


