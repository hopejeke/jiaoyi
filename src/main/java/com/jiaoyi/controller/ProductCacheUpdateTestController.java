package com.jiaoyi.controller;

import com.jiaoyi.dto.ProductCacheUpdateMessage;
import com.jiaoyi.service.ProductCacheUpdateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 商品缓存更新测试控制器
 */
@RestController
@RequestMapping("/api/product-cache-update-test")
@RequiredArgsConstructor
@Slf4j
public class ProductCacheUpdateTestController {

    private final ProductCacheUpdateMessageService productCacheUpdateMessageService;

    /**
     * 测试发送缓存更新消息
     */
    @PostMapping("/send-message/{productId}")
    public ResponseEntity<String> sendCacheUpdateMessage(
            @PathVariable Long productId,
            @RequestParam(required = false, defaultValue = "REFRESH") String operationType,
            @RequestParam(required = false, defaultValue = "true") Boolean enrichInventory) {
        
        try {
            ProductCacheUpdateMessage.OperationType type = 
                ProductCacheUpdateMessage.OperationType.valueOf(operationType.toUpperCase());
            
            productCacheUpdateMessageService.sendCacheUpdateMessage(productId, type, enrichInventory);
            
            return ResponseEntity.ok(String.format(
                "缓存更新消息已发送，商品ID: %d, 操作类型: %s, 聚合库存: %s", 
                productId, type, enrichInventory));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("无效的操作类型: " + operationType);
        } catch (Exception e) {
            log.error("发送缓存更新消息失败", e);
            return ResponseEntity.status(500).body("发送失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的操作类型
     */
    @GetMapping("/operation-types")
    public ResponseEntity<String> getOperationTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("支持的操作类型：\n");
        for (ProductCacheUpdateMessage.OperationType type : ProductCacheUpdateMessage.OperationType.values()) {
            sb.append("- ").append(type.name()).append("\n");
        }
        return ResponseEntity.ok(sb.toString());
    }
}

