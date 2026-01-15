package com.jiaoyi.order.controller;

import com.jiaoyi.outbox.OutboxDispatcher;
import com.jiaoyi.outbox.entity.Outbox;
import com.jiaoyi.outbox.repository.OutboxRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 管理接口
 * 用于查询死信列表、手动重放等操作
 */
@Slf4j
@RestController
@RequestMapping("/outbox")
@RequiredArgsConstructor
public class OutboxManagementController {
    
    private final OutboxRepository outboxRepository;
    private final OutboxDispatcher outboxDispatcher;
    
    @Value("${outbox.table:outbox}")
    private String table;
    
    /**
     * 查询死信列表
     * GET /outbox/dead?page=1&size=20&type=DEDUCT_STOCK_HTTP
     */
    @GetMapping("/dead")
    public ResponseEntity<List<Outbox>> listDeadLetters(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("查询死信列表，type: {}, bizKey: {}, page: {}, size: {}", type, bizKey, page, size);
            
            int offset = (page - 1) * size;
            List<Outbox> deadLetters = outboxRepository.selectDeadLetters(table, type, bizKey, offset, size);
            
            return ResponseEntity.ok(deadLetters);
            
        } catch (Exception e) {
            log.error("查询死信列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 手动重放死信任务（按 ID）
     * POST /outbox/{id}/retry
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<RetryResponse> retryById(@PathVariable Long id) {
        try {
            log.info("手动重放死信任务，ID: {}", id);
            
            // 1. 查询任务
            Outbox outbox = outboxRepository.selectById(table, id);
            if (outbox == null) {
                return ResponseEntity.badRequest()
                    .body(new RetryResponse(false, "任务不存在，ID: " + id));
            }
            
            // 2. 检查状态是否为 DEAD
            if (outbox.getStatus() != Outbox.OutboxStatus.DEAD) {
                return ResponseEntity.badRequest()
                    .body(new RetryResponse(false, "任务不是死信状态，当前状态: " + outbox.getStatus()));
            }
            
            // 3. 重置状态：DEAD -> NEW，重置重试次数和锁信息
            int updated = outboxRepository.resetDeadToNew(table, id);
            if (updated == 0) {
                return ResponseEntity.badRequest()
                    .body(new RetryResponse(false, "重置失败，任务可能已被其他操作修改"));
            }
            
            // 4. 触发一次 dispatch
            outboxDispatcher.dispatchOnce(outbox.getShardId());
            
            log.info("死信任务已重新入队，ID: {}, type: {}, bizKey: {}, shardId: {}", 
                    id, outbox.getType(), outbox.getBizKey(), outbox.getShardId());
            
            return ResponseEntity.ok(new RetryResponse(true, "任务已重新入队，等待处理"));
            
        } catch (Exception e) {
            log.error("手动重放死信任务失败，ID: {}", id, e);
            return ResponseEntity.internalServerError()
                .body(new RetryResponse(false, "重放失败: " + e.getMessage()));
        }
    }
    
    /**
     * 手动重放死信任务（按业务键）
     * POST /outbox/replay?bizKey=xxx&type=DEDUCT_STOCK_HTTP
     */
    @PostMapping("/replay")
    public ResponseEntity<RetryResponse> replayByBizKey(
            @RequestParam String bizKey,
            @RequestParam(required = false) String type) {
        
        try {
            log.info("手动重放死信任务，bizKey: {}, type: {}", bizKey, type);
            
            // 1. 查询任务（按 bizKey 和 type）
            List<Outbox> deadTasks = outboxRepository.selectDeadByBizKeyAndType(table, bizKey, type);
            if (deadTasks.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new RetryResponse(false, "未找到匹配的死信任务，bizKey: " + bizKey + ", type: " + type));
            }
            
            // 2. 重置状态并触发 dispatch
            int successCount = 0;
            for (Outbox outbox : deadTasks) {
                int updated = outboxRepository.resetDeadToNew(table, outbox.getId());
                if (updated > 0) {
                    outboxDispatcher.dispatchOnce(outbox.getShardId());
                    successCount++;
                    log.info("死信任务已重新入队，ID: {}, type: {}, bizKey: {}, shardId: {}", 
                            outbox.getId(), outbox.getType(), outbox.getBizKey(), outbox.getShardId());
                }
            }
            
            return ResponseEntity.ok(new RetryResponse(true, 
                    String.format("成功重放 %d/%d 个死信任务", successCount, deadTasks.size())));
            
        } catch (Exception e) {
            log.error("手动重放死信任务失败，bizKey: {}, type: {}", bizKey, type, e);
            return ResponseEntity.internalServerError()
                .body(new RetryResponse(false, "重放失败: " + e.getMessage()));
        }
    }
    
    @Data
    public static class RetryResponse {
        private boolean success;
        private String message;
        private LocalDateTime timestamp;
        
        public RetryResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
    }
}


