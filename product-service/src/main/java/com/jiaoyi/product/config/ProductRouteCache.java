package com.jiaoyi.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 商品域分片路由缓存
 * 
 * 功能：
 * 1. 启动时加载 product_shard_bucket_route 全量到内存（必须成功，否则应用启动失败）
 * 2. 定时刷新（每10秒）或基于 updated_at 拉取增量（缩短刷新间隔，减少旧数据风险）
 * 3. 提供主动刷新接口（forceRefresh），扩容时可立即刷新，避免等待定时任务
 * 4. 失败降级：刷新失败时继续使用上一次缓存（不能让路由不可用）
 * 
 * 核心数据结构：
 * - routeMap: product_shard_id -> ds_name 映射
 * - statusMap: product_shard_id -> status 映射（用于未来迁移）
 * 
 * 注意：
 * - 必须在 ShardingSphere 初始化之前完成初始化（使用 @Order(1)）
 * - 刷新时使用写锁保护，避免并发问题
 * - 与订单域的 RouteCache 分离，避免误操作
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(1) // 必须在 ShardingSphere 之前初始化
public class ProductRouteCache {
    
    /**
     * 使用基础数据库的 JdbcTemplate（用于读取 product_shard_bucket_route 路由表）
     * 注意：product_shard_bucket_route 表存在于基础数据库 jiaoyi 中，不分片
     */
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 路由映射：product_shard_id -> ds_name
     * 例如：0 -> "ds0", 342 -> "ds1", 684 -> "ds2"
     */
    private final Map<Integer, String> routeMap = new ConcurrentHashMap<>(1024);
    
    /**
     * 表路由映射：product_shard_id -> tbl_id
     * 例如：0 -> 0, 1 -> 1, 32 -> 0
     */
    private final Map<Integer, Integer> tableRouteMap = new ConcurrentHashMap<>(1024);
    
    /**
     * 状态映射：product_shard_id -> status（用于未来迁移）
     * 例如：0 -> "NORMAL", 100 -> "MIGRATING"
     */
    private final Map<Integer, String> statusMap = new ConcurrentHashMap<>(1024);
    
    /**
     * 版本号映射：product_shard_id -> version
     * 用于缓存热更新
     */
    private final Map<Integer, Long> versionMap = new ConcurrentHashMap<>(1024);
    
    /**
     * 迁移目标映射：product_shard_id -> (target_ds_id, target_tbl_id)
     * 仅在 MIGRATING 状态时有值
     */
    private final Map<Integer, MigrationTarget> migrationTargetMap = new ConcurrentHashMap<>(1024);
    
    /**
     * 迁移目标信息
     */
    @lombok.Data
    public static class MigrationTarget {
        private String targetDsId;
        private Integer targetTblId;
    }
    
    /**
     * 最后更新时间（用于增量刷新）
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 读写锁（保证刷新时的线程安全）
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * 是否初始化成功
     */
    private volatile boolean initialized = false;
    
    public ProductRouteCache(@Qualifier("productRouteCacheJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 启动时加载全量路由数据
     * 注意：如果初始化失败，应用启动失败（路由缓存是核心依赖）
     */
    @PostConstruct
    public void init() {
        log.info("【ProductRouteCache】开始初始化路由缓存...");
        try {
            loadAllRoutes();
            initialized = true;
            
            // 验证路由数据完整性（必须包含 0-1023 的所有 bucket）
            if (routeMap.size() != 1024 || tableRouteMap.size() != 1024) {
                throw new IllegalStateException(
                    String.format("路由数据不完整，期望 1024 条，实际 routeMap=%d, tableRouteMap=%d", 
                                routeMap.size(), tableRouteMap.size()));
            }
            
            log.info("【ProductRouteCache】路由缓存初始化成功，共 {} 条路由", routeMap.size());
        } catch (Exception e) {
            log.error("【ProductRouteCache】路由缓存初始化失败，应用启动终止", e);
            // 抛出异常，让应用启动失败（路由缓存是核心依赖，不能降级）
            throw new RuntimeException("商品域路由缓存初始化失败，应用无法启动", e);
        }
    }
    
    /**
     * 加载全量路由数据（使用写锁保护，避免并发问题）
     */
    private void loadAllRoutes() {
        String sql = "SELECT bucket_id, ds_name, tbl_id, status, version, target_ds_id, target_tbl_id, updated_at " +
                     "FROM product_shard_bucket_route ORDER BY bucket_id";
        
        // 使用临时 Map 收集数据，避免在加载过程中被读取到不完整的数据
        Map<Integer, String> newRouteMap = new ConcurrentHashMap<>(1024);
        Map<Integer, Integer> newTableRouteMap = new ConcurrentHashMap<>(1024);
        Map<Integer, String> newStatusMap = new ConcurrentHashMap<>(1024);
        Map<Integer, Long> newVersionMap = new ConcurrentHashMap<>(1024);
        Map<Integer, MigrationTarget> newMigrationTargetMap = new ConcurrentHashMap<>(1024);
        LocalDateTime[] newLastUpdateTimeRef = {null}; // 使用数组引用，避免 lambda 中的 final 限制
        
        jdbcTemplate.query(sql, rs -> {
            int bucketId = rs.getInt("bucket_id");
            String dsName = rs.getString("ds_name");
            int tblId = rs.getInt("tbl_id");
            String status = rs.getString("status");
            long version = rs.getLong("version");
            String targetDsId = rs.getString("target_ds_id");
            Integer targetTblId = rs.getObject("target_tbl_id") != null ? rs.getInt("target_tbl_id") : null;
            LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
            
            newRouteMap.put(bucketId, dsName);
            newTableRouteMap.put(bucketId, tblId);
            newStatusMap.put(bucketId, status);
            newVersionMap.put(bucketId, version);
            
            // 如果状态为 MIGRATING 且有目标信息，记录迁移目标
            if ("MIGRATING".equals(status) && targetDsId != null && targetTblId != null) {
                MigrationTarget target = new MigrationTarget();
                target.setTargetDsId(targetDsId);
                target.setTargetTblId(targetTblId);
                newMigrationTargetMap.put(bucketId, target);
            }
            
            if (newLastUpdateTimeRef[0] == null || updatedAt.isAfter(newLastUpdateTimeRef[0])) {
                newLastUpdateTimeRef[0] = updatedAt;
            }
        });
        
        // 使用写锁原子性更新（避免读取到不完整的数据）
        lock.writeLock().lock();
        try {
            routeMap.clear();
            routeMap.putAll(newRouteMap);
            tableRouteMap.clear();
            tableRouteMap.putAll(newTableRouteMap);
            statusMap.clear();
            statusMap.putAll(newStatusMap);
            versionMap.clear();
            versionMap.putAll(newVersionMap);
            migrationTargetMap.clear();
            migrationTargetMap.putAll(newMigrationTargetMap);
            lastUpdateTime = newLastUpdateTimeRef[0];
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 定时刷新路由缓存（每10秒，缩短刷新间隔，减少旧数据窗口）
     * 策略：增量拉取 + 兜底全量
     * 1. 先尝试增量拉取（updated_at > lastUpdateTime）
     * 2. 如果增量数据不完整，则全量加载
     * 3. 刷新失败不影响当前缓存，继续使用旧数据（兜底）
     */
    @Scheduled(fixedDelay = 10000) // 10秒（缩短刷新间隔，减少旧数据风险）
    public void refreshRoutes() {
        if (!initialized) {
            // 如果初始化失败，不应该走到这里（初始化失败会抛异常）
            log.error("【ProductRouteCache】路由缓存未初始化，跳过刷新");
            return;
        }
        
        try {
            // 检查是否有更新（使用读锁读取 lastUpdateTime）
            LocalDateTime currentLastUpdateTime;
            lock.readLock().lock();
            try {
                currentLastUpdateTime = lastUpdateTime;
            } finally {
                lock.readLock().unlock();
            }
            
            String checkSql = "SELECT MAX(updated_at) as max_updated_at FROM product_shard_bucket_route";
            LocalDateTime maxUpdatedAt = jdbcTemplate.queryForObject(checkSql, 
                (rs, rowNum) -> rs.getTimestamp("max_updated_at") != null ? 
                    rs.getTimestamp("max_updated_at").toLocalDateTime() : null);
            
            if (maxUpdatedAt != null && (currentLastUpdateTime == null || maxUpdatedAt.isAfter(currentLastUpdateTime))) {
                log.info("【ProductRouteCache】检测到路由更新，开始增量刷新...");
                
                // 尝试增量拉取
                boolean incrementalSuccess = loadIncrementalRoutes(currentLastUpdateTime);
                
                if (!incrementalSuccess) {
                    // 增量失败，降级为全量加载
                    log.warn("【ProductRouteCache】增量刷新失败，降级为全量加载");
                    loadAllRoutes();
                }
                
                // 验证数据完整性
                lock.readLock().lock();
                try {
                    if (routeMap.size() != 1024 || tableRouteMap.size() != 1024) {
                        log.error("【ProductRouteCache】刷新后路由数据不完整，期望 1024 条，实际 routeMap={}, tableRouteMap={}", 
                                routeMap.size(), tableRouteMap.size());
                        // 数据不完整，降级为全量加载
                        lock.readLock().unlock();
                        loadAllRoutes();
                        lock.readLock().lock();
                    } else {
                        log.info("【ProductRouteCache】路由缓存刷新成功，共 {} 条路由", routeMap.size());
                    }
                } finally {
                    lock.readLock().unlock();
                }
            }
        } catch (Exception e) {
            log.warn("【ProductRouteCache】路由缓存刷新失败，继续使用上一次缓存", e);
            // 不抛出异常，使用上一次缓存（降级策略）
        }
    }
    
    /**
     * 增量加载路由数据（只加载 updated_at > lastUpdateTime 的记录）
     * 
     * @param lastUpdateTime 上次更新时间
     * @return true 如果增量加载成功且数据完整，false 如果失败或不完整
     */
    private boolean loadIncrementalRoutes(LocalDateTime lastUpdateTime) {
        if (lastUpdateTime == null) {
            // 如果没有上次更新时间，无法增量，返回 false 触发全量加载
            return false;
        }
        
        try {
            String sql = "SELECT bucket_id, ds_name, tbl_id, status, version, target_ds_id, target_tbl_id, updated_at " +
                        "FROM product_shard_bucket_route WHERE updated_at > ? ORDER BY bucket_id";
            
            // 使用临时 Map 收集增量数据
            Map<Integer, String> incrementalRouteMap = new ConcurrentHashMap<>();
            Map<Integer, Integer> incrementalTableRouteMap = new ConcurrentHashMap<>();
            Map<Integer, String> incrementalStatusMap = new ConcurrentHashMap<>();
            Map<Integer, Long> incrementalVersionMap = new ConcurrentHashMap<>();
            Map<Integer, MigrationTarget> incrementalMigrationTargetMap = new ConcurrentHashMap<>();
            LocalDateTime[] newLastUpdateTimeRef = {lastUpdateTime};
            
            // 保存 lastUpdateTime 的副本，用于 PreparedStatementSetter
            final LocalDateTime lastUpdateTimeForQuery = lastUpdateTime;
            
            jdbcTemplate.query(sql, 
                new org.springframework.jdbc.core.PreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        ps.setTimestamp(1, java.sql.Timestamp.valueOf(lastUpdateTimeForQuery));
                    }
                },
                new org.springframework.jdbc.core.RowCallbackHandler() {
                    @Override
                    public void processRow(java.sql.ResultSet rs) throws java.sql.SQLException {
                        int bucketId = rs.getInt("bucket_id");
                        String dsName = rs.getString("ds_name");
                        int tblId = rs.getInt("tbl_id");
                        String status = rs.getString("status");
                        long version = rs.getLong("version");
                        String targetDsId = rs.getString("target_ds_id");
                        Integer targetTblId = rs.getObject("target_tbl_id") != null ? rs.getInt("target_tbl_id") : null;
                        LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
                        
                        incrementalRouteMap.put(bucketId, dsName);
                        incrementalTableRouteMap.put(bucketId, tblId);
                        incrementalStatusMap.put(bucketId, status);
                        incrementalVersionMap.put(bucketId, version);
                        
                        // 如果状态为 MIGRATING 且有目标信息，记录迁移目标
                        if ("MIGRATING".equals(status) && targetDsId != null && targetTblId != null) {
                            MigrationTarget target = new MigrationTarget();
                            target.setTargetDsId(targetDsId);
                            target.setTargetTblId(targetTblId);
                            incrementalMigrationTargetMap.put(bucketId, target);
                        } else {
                            // 如果状态不是 MIGRATING，清除迁移目标
                            incrementalMigrationTargetMap.put(bucketId, null);
                        }
                        
                        if (updatedAt.isAfter(newLastUpdateTimeRef[0])) {
                            newLastUpdateTimeRef[0] = updatedAt;
                        }
                    }
                });
            
            if (incrementalRouteMap.isEmpty()) {
                // 没有增量数据，说明没有更新，返回 true（不需要刷新）
                return true;
            }
            
            // 使用写锁原子性更新（先合并增量数据到现有缓存）
            lock.writeLock().lock();
            try {
                // 合并增量数据
                routeMap.putAll(incrementalRouteMap);
                tableRouteMap.putAll(incrementalTableRouteMap);
                statusMap.putAll(incrementalStatusMap);
                versionMap.putAll(incrementalVersionMap);
                
                // 合并迁移目标（null 值表示清除）
                for (Map.Entry<Integer, MigrationTarget> entry : incrementalMigrationTargetMap.entrySet()) {
                    if (entry.getValue() == null) {
                        migrationTargetMap.remove(entry.getKey());
                    } else {
                        migrationTargetMap.put(entry.getKey(), entry.getValue());
                    }
                }
                
                lastUpdateTime = newLastUpdateTimeRef[0];
                
                // 验证数据完整性（必须包含 1024 条）
                if (routeMap.size() != 1024 || tableRouteMap.size() != 1024) {
                    log.warn("【ProductRouteCache】增量刷新后数据不完整，期望 1024 条，实际 routeMap={}, tableRouteMap={}，需要全量加载", 
                            routeMap.size(), tableRouteMap.size());
                    return false;
                }
                
                log.info("【ProductRouteCache】增量刷新成功，更新了 {} 条路由", incrementalRouteMap.size());
                return true;
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.warn("【ProductRouteCache】增量刷新失败，将降级为全量加载", e);
            return false;
        }
    }
    
    /**
     * 根据 product_shard_id 获取物理库名称
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 物理库名称（ds0/ds1/ds2/...）
     * @throws IllegalStateException 如果缓存未初始化或路由不存在
     */
    public String getDataSourceName(int productShardId) {
        if (productShardId < 0 || productShardId >= 1024) {
            throw new IllegalArgumentException("productShardId 必须在 0-1023 范围内: " + productShardId);
        }
        
        if (!initialized) {
            throw new IllegalStateException("路由缓存未初始化，无法获取路由");
        }
        
        lock.readLock().lock();
        try {
            String dsName = routeMap.get(productShardId);
            if (dsName == null) {
                // 如果路由不存在，说明数据有问题（初始化时应该验证完整性）
                throw new IllegalStateException(
                    String.format("product_shard_id %d 的路由不存在，路由数据可能不完整", productShardId));
            }
            return dsName;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取表路由（根据 product_shard_id 获取表后缀）
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 表后缀（0-31）
     * @throws IllegalStateException 如果缓存未初始化或路由不存在
     */
    public int getTableId(int productShardId) {
        if (productShardId < 0 || productShardId >= 1024) {
            throw new IllegalArgumentException("productShardId 必须在 0-1023 范围内: " + productShardId);
        }
        
        if (!initialized) {
            throw new IllegalStateException("路由缓存未初始化，无法获取表路由");
        }
        
        lock.readLock().lock();
        try {
            Integer tblId = tableRouteMap.get(productShardId);
            if (tblId == null) {
                // 如果路由不存在，说明数据有问题（初始化时应该验证完整性）
                throw new IllegalStateException(
                    String.format("product_shard_id %d 的表路由不存在，路由数据可能不完整", productShardId));
            }
            return tblId;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取路由状态（用于迁移）
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 状态（NORMAL/MIGRATING/DUAL_WRITE）
     */
    public String getStatus(int productShardId) {
        lock.readLock().lock();
        try {
            return statusMap.getOrDefault(productShardId, "NORMAL");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 判断 bucket 是否在迁移中
     * 
     * @param productShardId 分片ID（0-1023）
     * @return true 如果正在迁移，false 否则
     */
    public boolean isMigrating(int productShardId) {
        return "MIGRATING".equals(getStatus(productShardId));
    }
    
    /**
     * 获取迁移目标（仅在 MIGRATING 状态时有效）
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 迁移目标信息，如果不在迁移中则返回 null
     */
    public MigrationTarget getMigrationTarget(int productShardId) {
        lock.readLock().lock();
        try {
            return migrationTargetMap.get(productShardId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取版本号（用于缓存热更新）
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 版本号
     */
    public long getVersion(int productShardId) {
        lock.readLock().lock();
        try {
            return versionMap.getOrDefault(productShardId, 1L);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有路由映射（用于调试）
     * 
     * @return 路由映射的副本
     */
    public Map<Integer, String> getAllRoutes() {
        lock.readLock().lock();
        try {
            return new ConcurrentHashMap<>(routeMap);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 检查路由缓存是否已初始化
     * 
     * @return true 如果已初始化，false 否则
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 主动刷新路由缓存（用于扩容时立即刷新，避免等待定时任务）
     * 
     * @return true 如果刷新成功，false 如果刷新失败
     */
    public boolean forceRefresh() {
        if (!initialized) {
            log.error("【ProductRouteCache】路由缓存未初始化，无法强制刷新");
            return false;
        }
        
        log.info("【ProductRouteCache】收到强制刷新请求，立即刷新路由缓存...");
        try {
            loadAllRoutes();
            
            // 验证数据完整性
            lock.readLock().lock();
            try {
                if (routeMap.size() != 1024 || tableRouteMap.size() != 1024) {
                    log.error("【ProductRouteCache】强制刷新后路由数据不完整，期望 1024 条，实际 routeMap={}, tableRouteMap={}", 
                            routeMap.size(), tableRouteMap.size());
                    return false;
                } else {
                    log.info("【ProductRouteCache】强制刷新成功，共 {} 条路由", routeMap.size());
                    return true;
                }
            } finally {
                lock.readLock().unlock();
            }
        } catch (Exception e) {
            log.error("【ProductRouteCache】强制刷新失败", e);
            return false;
        }
    }
    
    /**
     * 获取缓存最后更新时间（用于判断缓存是否过期）
     * 
     * @return 最后更新时间，如果未初始化则返回 null
     */
    public LocalDateTime getLastUpdateTime() {
        if (!initialized) {
            return null;
        }
        
        lock.readLock().lock();
        try {
            return lastUpdateTime;
        } finally {
            lock.readLock().unlock();
        }
    }
}


