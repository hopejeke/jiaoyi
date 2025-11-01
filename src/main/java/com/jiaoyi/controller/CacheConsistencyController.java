package com.jiaoyi.controller;

import com.jiaoyi.service.CacheConsistencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存一致性管理控制器
 */
@RestController
@RequestMapping("/api/cache-consistency")
@RequiredArgsConstructor
@Slf4j
public class CacheConsistencyController {

    private final CacheConsistencyService cacheConsistencyService;

    /**
     * 获取缓存一致性状态
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        String status = cacheConsistencyService.getCacheConsistencyStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 缓存预热
     */
    @PostMapping("/warm-up")
    public ResponseEntity<String> warmUpCache() {
        log.info("收到缓存预热请求");
        cacheConsistencyService.warmUpCache();
        return ResponseEntity.ok("缓存预热已启动");
    }

    /**
     * 检查缓存一致性
     */
    @PostMapping("/check")
    public ResponseEntity<String> checkConsistency() {
        log.info("收到缓存一致性检查请求");
        cacheConsistencyService.checkCacheConsistency();
        return ResponseEntity.ok("缓存一致性检查已启动");
    }

    /**
     * 强制刷新所有缓存
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<String> refreshAllCache() {
        log.info("收到强制刷新所有缓存请求");
        cacheConsistencyService.forceRefreshAllCache();
        return ResponseEntity.ok("所有缓存已强制刷新");
    }

    /**
     * 缓存一致性策略说明
     */
    @GetMapping("/strategies")
    public ResponseEntity<String> getStrategies() {
        String strategies = """
                缓存一致性保证策略：
                
                1. Cache-Aside模式（已实现）
                   - 读：先查缓存，未命中则查数据库并缓存
                   - 写：先写数据库，再更新/删除缓存
                
                2. Write-Through模式（已实现）
                   - 写：同时写数据库和缓存
                   - 读：只读缓存
                
                3. Write-Behind模式（异步写入）
                   - 写：先写缓存，异步写数据库
                   - 读：只读缓存
                
                4. 双删策略（已实现）
                   - 先删除缓存
                   - 更新数据库
                   - 延迟删除缓存
                
                5. 版本控制（已实现）
                   - 为每个缓存项设置版本号
                   - 检查版本一致性
                
                6. 分布式锁（已实现）
                   - 防止并发更新导致的数据不一致
                
                7. 定期一致性检查（已实现）
                   - 定期检查缓存和数据库一致性
                
                8. 缓存预热（已实现）
                   - 系统启动时预加载热点数据
                """;
        
        return ResponseEntity.ok(strategies);
    }
}

