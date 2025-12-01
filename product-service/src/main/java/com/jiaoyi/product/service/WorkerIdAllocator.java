package com.jiaoyi.product.service;

import com.jiaoyi.product.mapper.primary.WorkerIdMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Worker-ID 动态分配服务
 * 使用数据库表管理 worker-id 的分配和回收
 * 
 * 工作原理：
 * 1. 启动时根据 instanceId 查表，有则复用，无则分配新的
 * 2. 定时发送心跳，更新 last_heartbeat
 * 3. 定期清理过期的 worker-id（心跳超时）
 * 4. 关闭时释放当前实例的 worker-id
 * 
 * @author Administrator
 */
@Slf4j
@Service
public class WorkerIdAllocator {
    
    @Autowired
    private WorkerIdMapper workerIdMapper;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Value("${spring.application.name:product-service}")
    private String applicationName;
    
    @Value("${HOSTNAME:}")
    private String hostname;
    
    @Value("${server.port:8081}")
    private String serverPort;
    
    /**
     * 当前实例的唯一标识
     * 格式：{applicationName}-{hostname/IP}-{port}-{随机UUID}
     */
    private String instanceId;
    
    /**
     * 当前实例分配的 worker-id
     * -- GETTER --
     *  获取当前分配的 worker-id

     */
    @Getter
    private volatile Integer allocatedWorkerId;
    
    /**
     * 心跳线程池
     */
    private ScheduledExecutorService heartbeatExecutor;
    
    /**
     * 清理线程池
     */
    private ScheduledExecutorService cleanupExecutor;
    
    /**
     * 心跳间隔（秒）
     */
    private static final long HEARTBEAT_INTERVAL = 30;
    
    /**
     * Worker-ID 失效时间（秒）
     * 如果超过此时间未收到心跳，认为实例已失效
     */
    private static final long WORKER_ID_EXPIRE_TIME = 120;
    
    /**
     * 清理间隔（秒）
     */
    private static final long CLEANUP_INTERVAL = 60;
    
    @PostConstruct
    public void init() {
        try {
            // 生成实例ID
            instanceId = generateInstanceId();
            log.info("初始化 Worker-ID 分配器，实例ID: {}", instanceId);
            
            // 分配或复用 worker-id
            allocatedWorkerId = allocateOrReuse();
            
            if (allocatedWorkerId != null) {
                log.info("✓ 成功分配/复用 worker-id: {}", allocatedWorkerId);
                
                // 启动心跳任务
                startHeartbeat();
                
                // 启动清理任务
                startCleanup();
            } else {
                log.error("✗ 无法分配 worker-id，所有 worker-id 已被占用");
                throw new RuntimeException("无法分配 worker-id，所有 worker-id (0-1023) 已被占用");
            }
            
        } catch (Exception e) {
            log.error("初始化 Worker-ID 分配器失败", e);
            throw new RuntimeException("初始化 Worker-ID 分配器失败", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        try {
            // 停止心跳
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdown();
                try {
                    if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        heartbeatExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    heartbeatExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // 停止清理任务
            if (cleanupExecutor != null) {
                cleanupExecutor.shutdown();
                try {
                    if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanupExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // 释放 worker-id
            if (allocatedWorkerId != null && instanceId != null) {
                releaseWorkerId();
                log.info("已释放 worker-id: {}", allocatedWorkerId);
            }
            
        } catch (Exception e) {
            log.error("释放 worker-id 失败", e);
        }
    }
    
    /**
     * 生成实例ID
     */
    private String generateInstanceId() {
        StringBuilder sb = new StringBuilder(applicationName);
        
        // 添加 hostname 或 IP
        if (hostname != null && !hostname.trim().isEmpty()) {
            sb.append("-").append(hostname);
        } else {
            try {
                String hostAddress = InetAddress.getLocalHost().getHostAddress();
                sb.append("-").append(hostAddress);
            } catch (Exception e) {
                // 忽略
            }
        }
        
        // 添加端口
        sb.append("-").append(serverPort);
        
        // 添加随机UUID（防止同一机器多实例冲突）
        sb.append("-").append(UUID.randomUUID().toString(), 0, 8);
        
        return sb.toString();
    }
    
    /**
     * 分配或复用 worker-id（使用 Redis 分布式锁保证并发安全）
     * @return 分配的 worker-id，如果无法分配返回 null
     */
    private Integer allocateOrReuse() {
        String lockKey = "snowflake:worker:allocate:lock";
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 1. 先查表，看是否有已分配的 worker-id（不加锁，快速路径）
            Integer existing = workerIdMapper.findByInstance(instanceId);
            if (existing != null) {
                log.info("发现已分配的 worker-id: {}，复用", existing);
                return existing;
            }
            
            // 2. 尝试获取 Redis 分布式锁（最多等待30秒，锁持有时间60秒）
            boolean lockAcquired = lock.tryLock(30, 60, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.error("获取 worker-id 分配锁失败，等待超时");
                return null;
            }
            
            log.debug("成功获取 worker-id 分配锁");
            
            try {
                // 3. 再次检查（双重检查，防止在等待锁期间其他实例已分配）
                existing = workerIdMapper.findByInstance(instanceId);
                if (existing != null) {
                    log.info("获取锁后发现已分配的 worker-id: {}，复用", existing);
                    return existing;
                }
                
                // 4. 查找最小未使用的 worker-id
                Integer newId = findMinAvailableId();
                if (newId == null) {
                    log.error("所有 worker-id (0-1023) 已被占用");
                    return null;
                }
                
                // 5. 插入数据库
                workerIdMapper.insert(newId, instanceId);
                log.info("分配新的 worker-id: {}", newId);
                
                return newId;
                
            } finally {
                // 6. 释放锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取 worker-id 分配锁被中断", e);
            return null;
        } catch (Exception e) {
            log.error("分配 worker-id 失败", e);
            // 确保释放锁
            if (lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception ex) {
                    log.error("释放锁时发生异常", ex);
                }
            }
            return null;
        }
    }
    
    /**
     * 查找最小未使用的 worker-id（0-1023）
     * @return 最小未使用的 worker-id，如果全部已分配返回 null
     */
    private Integer findMinAvailableId() {
        try {
            // 获取所有已分配的 worker-id
            java.util.List<Integer> allocated = workerIdMapper.findAllocatedWorkerIds();
            java.util.Set<Integer> allocatedSet = new java.util.HashSet<>(allocated);
            
            // 从 0 开始查找第一个未分配的
            for (int i = 0; i <= 1023; i++) {
                if (!allocatedSet.contains(i)) {
                    return i;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("查找最小未使用的 worker-id 失败", e);
            return null;
        }
    }
    
    /**
     * 启动心跳任务
     */
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-id-heartbeat");
            t.setDaemon(true);
            return t;
        });
        
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                log.error("发送心跳失败", e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        if (allocatedWorkerId == null || instanceId == null) {
            return;
        }
        
        try {
            int affected = workerIdMapper.updateHeartbeat(allocatedWorkerId, instanceId);
            if (affected == 0) {
                // worker-id 被其他实例占用，重新分配
                log.warn("worker-id {} 被其他实例占用，尝试重新分配", allocatedWorkerId);
                allocatedWorkerId = allocateOrReuse();
                if (allocatedWorkerId == null) {
                    log.error("无法重新分配 worker-id");
                }
            }
        } catch (Exception e) {
            log.error("发送心跳失败", e);
        }
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanup() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-id-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try {
                cleanExpiredWorkerIds();
            } catch (Exception e) {
                log.error("清理过期 worker-id 失败", e);
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * 清理过期的 worker-id
     */
    private void cleanExpiredWorkerIds() {
        try {
            long expireTimeMillis = System.currentTimeMillis() - (WORKER_ID_EXPIRE_TIME * 1000);
            Timestamp expireTime = new Timestamp(expireTimeMillis);
            
            int deleted = workerIdMapper.deleteExpired(expireTime);
            if (deleted > 0) {
                log.info("清理了 {} 个过期的 worker-id", deleted);
            }
        } catch (Exception e) {
            log.error("清理过期 worker-id 失败", e);
        }
    }
    
    /**
     * 释放 worker-id
     */
    private void releaseWorkerId() {
        try {
            int affected = workerIdMapper.deleteByInstance(instanceId);
            if (affected > 0) {
                log.info("已释放 worker-id: {}", allocatedWorkerId);
            }
        } catch (Exception e) {
            log.error("释放 worker-id 失败", e);
        }
    }

}

