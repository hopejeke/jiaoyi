package com.jiaoyi.outbox.repository;

import com.jiaoyi.outbox.entity.Outbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 JdbcTemplate 的 OutboxRepository 实现（默认实现）
 * 业务方不需要配置 MyBatis，开箱即用
 */
/**
 * 基于 JdbcTemplate 的 OutboxRepository 实现（默认实现）
 * 业务方不需要配置 MyBatis，开箱即用
 * 
 * 注意：此类不再使用 @Repository 注解，而是由 OutboxAutoConfiguration 通过 @Bean 方法显式创建
 */
@Slf4j
public class JdbcOutboxRepository implements OutboxRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public int insert(String table, Outbox outbox) {
        String sql = "INSERT INTO " + table + " (" +
                "type, biz_key, sharding_key, store_id, shard_id, topic, tag, message_key, payload, message_body, " +
                "status, retry_count, next_retry_time, lock_owner, lock_time, lock_until, " +
                "last_error, created_at, updated_at, completed_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int idx = 1;
            ps.setString(idx++, outbox.getType());
            ps.setString(idx++, outbox.getBizKey());
            ps.setString(idx++, outbox.getShardingKey());
            ps.setObject(idx++, outbox.getStoreId()); // store_id 用于 ShardingSphere 分片路由
            ps.setObject(idx++, outbox.getShardId());
            ps.setString(idx++, outbox.getTopic());
            ps.setString(idx++, outbox.getTag());
            ps.setString(idx++, outbox.getMessageKey());
            ps.setString(idx++, outbox.getPayload());
            ps.setString(idx++, outbox.getPayload()); // message_body 兼容字段
            ps.setString(idx++, outbox.getStatus() != null ? outbox.getStatus().name() : "NEW");
            ps.setInt(idx++, outbox.getRetryCount() != null ? outbox.getRetryCount() : 0);
            ps.setTimestamp(idx++, outbox.getNextRetryTime() != null ? Timestamp.valueOf(outbox.getNextRetryTime()) : null);
            ps.setString(idx++, outbox.getLockOwner());
            ps.setTimestamp(idx++, outbox.getLockTime() != null ? Timestamp.valueOf(outbox.getLockTime()) : null);
            ps.setTimestamp(idx++, outbox.getLockUntil() != null ? Timestamp.valueOf(outbox.getLockUntil()) : null);
            ps.setString(idx++, outbox.getLastError());
            ps.setTimestamp(idx++, outbox.getCreatedAt() != null ? Timestamp.valueOf(outbox.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(idx++, outbox.getUpdatedAt() != null ? Timestamp.valueOf(outbox.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(idx++, outbox.getCompletedAt() != null ? Timestamp.valueOf(outbox.getCompletedAt()) : null);
            return ps;
        }, keyHolder);
        
        if (keyHolder.getKey() != null) {
            outbox.setId(keyHolder.getKey().longValue());
        }
        
        return 1;
    }
    
    @Override
    public Outbox selectById(String table, Long id) {
        String sql = "SELECT * FROM " + table + " WHERE id = ?";
        List<Outbox> results = jdbcTemplate.query(sql, new OutboxRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<Outbox> selectCandidatesByShard(String table, List<Integer> shardIds, LocalDateTime now, int limit) {
        if (shardIds == null || shardIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        String placeholders = shardIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM " + table +
                " WHERE status IN ('NEW', 'FAILED')" +
                " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
                " AND shard_id IN (" + placeholders + ")" +
                " ORDER BY created_at ASC LIMIT ?";
        
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(now));
        params.addAll(shardIds);
        params.add(limit);
        
        return jdbcTemplate.query(sql, new OutboxRowMapper(), params.toArray());
    }
    
    @Override
    public List<Outbox> selectCandidatesBySingleShard(String table, Integer shardId, LocalDateTime now, int limit) {
        String sql = "SELECT * FROM " + table +
                " WHERE status IN ('NEW', 'FAILED')" +
                " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
                " AND shard_id = ?" +
                " ORDER BY created_at ASC LIMIT ?";
        
        return jdbcTemplate.query(sql, new OutboxRowMapper(), Timestamp.valueOf(now), shardId, limit);
    }
    
    @Override
    public List<Long> selectIdsForClaim(String table, Integer shardId, LocalDateTime now, int limit) {
        String sql = "SELECT id FROM " + table +
                " WHERE shard_id = ?" +
                " AND status IN ('NEW', 'FAILED')" +
                " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
                " AND (lock_until IS NULL OR lock_until < ?)" +
                " ORDER BY id ASC LIMIT ?" +
                " FOR UPDATE SKIP LOCKED";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), 
                shardId, Timestamp.valueOf(now), Timestamp.valueOf(now), limit);
    }
    
    @Override
    public int claimByIds(String table, Integer shardId, List<Long> ids, String lockedBy, 
                         LocalDateTime lockUntil, LocalDateTime now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "UPDATE " + table +
                " SET status = 'PROCESSING'," +
                " lock_owner = ?, lock_time = ?, lock_until = ?, updated_at = ?" +
                " WHERE shard_id = ?" +
                " AND id IN (" + placeholders + ")" +
                " AND status IN ('NEW', 'FAILED')" +
                " AND (next_retry_time IS NULL OR next_retry_time <= ?)" +
                " AND (lock_until IS NULL OR lock_until < ?)";
        
        List<Object> params = new ArrayList<>();
        params.add(lockedBy);
        params.add(Timestamp.valueOf(now));
        params.add(Timestamp.valueOf(lockUntil));
        params.add(Timestamp.valueOf(now));
        params.add(shardId);
        params.addAll(ids);
        params.add(Timestamp.valueOf(now));
        params.add(Timestamp.valueOf(now));
        
        return jdbcTemplate.update(sql, params.toArray());
    }
    
    @Override
    public List<Outbox> selectByIds(String table, Integer shardId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM " + table +
                " WHERE shard_id = ? AND id IN (" + placeholders + ")" +
                " ORDER BY id ASC";
        
        List<Object> params = new ArrayList<>();
        params.add(shardId);
        params.addAll(ids);
        
        return jdbcTemplate.query(sql, new OutboxRowMapper(), params.toArray());
    }
    
    @Override
    public int markSent(String table, Long id, String lockedBy) {
        String sql = "UPDATE " + table +
                " SET status = 'SENT', completed_at = NOW(), updated_at = NOW()" +
                " WHERE id = ? AND lock_owner = ? AND status = 'PROCESSING'";
        
        return jdbcTemplate.update(sql, id, lockedBy);
    }
    
    @Override
    public int markFailed(String table, Long id, String lockedBy, int retryCount, 
                         LocalDateTime nextRetryTime, String errorMessage) {
        String sql = "UPDATE " + table +
                " SET status = 'FAILED', retry_count = ?, next_retry_time = ?, last_error = ?, updated_at = NOW()" +
                " WHERE id = ? AND lock_owner = ? AND status = 'PROCESSING'";
        
        return jdbcTemplate.update(sql, retryCount, 
                nextRetryTime != null ? Timestamp.valueOf(nextRetryTime) : null,
                errorMessage, id, lockedBy);
    }
    
    @Override
    public int markDead(String table, Long id, String lockedBy, String errorMessage) {
        String sql = "UPDATE " + table +
                " SET status = 'DEAD', completed_at = NOW(), last_error = ?, updated_at = NOW()" +
                " WHERE id = ? AND lock_owner = ? AND status = 'PROCESSING'";
        
        return jdbcTemplate.update(sql, errorMessage, id, lockedBy);
    }
    
    @Override
    public int releaseLock(String table, Long id, String lockedBy) {
        String sql = "UPDATE " + table +
                " SET status = 'NEW', lock_owner = NULL, lock_time = NULL, lock_until = NULL, updated_at = NOW()" +
                " WHERE id = ? AND lock_owner = ? AND status = 'PROCESSING'";
        
        return jdbcTemplate.update(sql, id, lockedBy);
    }
    
    @Override
    public int recoverStuck(String table, LocalDateTime now) {
        // 广播查询所有表（不按分片键过滤，因为 outbox 表已改为按 store_id 分片）
        // 这是可接受的，因为这是维护性任务，频率低（10秒一次）
        String sql = "UPDATE " + table +
                " SET status = 'FAILED'," +
                " lock_owner = NULL, lock_time = NULL, lock_until = NULL," +
                " next_retry_time = NOW()," +
                " last_error = CONCAT('[RECOVER] lock expired at ', NOW())," +
                " updated_at = NOW()" +
                " WHERE status = 'PROCESSING'" +
                " AND lock_until < ?";
        
        return jdbcTemplate.update(sql, Timestamp.valueOf(now));
    }
    
    @Override
    @Deprecated
    public int recoverStuckByShard(String table, Integer shardId, LocalDateTime now) {
        // 已废弃：因为 outbox 表已改为按 store_id 分片，无法按 shard_id 路由
        // 此方法保留用于向后兼容，但实际会广播查询所有表
        return recoverStuck(table, now);
    }
    
    @Override
    public int extendLock(String table, Long id, String lockedBy, LocalDateTime newLockUntil, LocalDateTime now) {
        String sql = "UPDATE " + table +
                " SET lock_until = ?, updated_at = ?" +
                " WHERE id = ?" +
                " AND status = 'PROCESSING'" +
                " AND lock_owner = ?" +
                " AND lock_until >= ?";
        
        return jdbcTemplate.update(sql, Timestamp.valueOf(newLockUntil), Timestamp.valueOf(now),
                id, lockedBy, Timestamp.valueOf(now));
    }
    
    @Override
    public int resetDeadToNew(String table, Long id) {
        String sql = "UPDATE " + table +
                " SET status = 'NEW', retry_count = 0, next_retry_time = NULL, last_error = NULL, updated_at = NOW()" +
                " WHERE id = ? AND status = 'DEAD'";
        
        return jdbcTemplate.update(sql, id);
    }
    
    @Override
    public int deleteSentRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime) {
        String sql = "DELETE FROM " + table +
                " WHERE status = 'SENT'" +
                " AND shard_id >= ? AND shard_id < ?" +
                " AND created_at < ?";
        
        return jdbcTemplate.update(sql, startShardId, endShardId, Timestamp.valueOf(cutoffTime));
    }
    
    @Override
    public int deleteDeadRecordsByShardRange(String table, int startShardId, int endShardId, LocalDateTime cutoffTime) {
        String sql = "DELETE FROM " + table +
                " WHERE status = 'DEAD'" +
                " AND shard_id >= ? AND shard_id < ?" +
                " AND created_at < ?";
        
        return jdbcTemplate.update(sql, startShardId, endShardId, Timestamp.valueOf(cutoffTime));
    }
    
    @Override
    public List<Outbox> selectDeadLetters(String table, String type, String bizKey, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + table + " WHERE status = 'DEAD'");
        List<Object> params = new ArrayList<>();
        
        if (type != null && !type.isEmpty()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        
        if (bizKey != null && !bizKey.isEmpty()) {
            sql.append(" AND biz_key = ?");
            params.add(bizKey);
        }
        
        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        
        return jdbcTemplate.query(sql.toString(), new OutboxRowMapper(), params.toArray());
    }
    
    @Override
    public List<Outbox> selectDeadByBizKeyAndType(String table, String bizKey, String type) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + table + 
                " WHERE status = 'DEAD' AND biz_key = ?");
        List<Object> params = new ArrayList<>();
        params.add(bizKey);
        
        if (type != null && !type.isEmpty()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        
        sql.append(" ORDER BY updated_at DESC");
        
        return jdbcTemplate.query(sql.toString(), new OutboxRowMapper(), params.toArray());
    }
    
    /**
     * Outbox 实体 RowMapper
     */
    private static class OutboxRowMapper implements RowMapper<Outbox> {
        @Override
        public Outbox mapRow(ResultSet rs, int rowNum) throws SQLException {
            Outbox outbox = new Outbox();
            outbox.setId(rs.getLong("id"));
            outbox.setType(rs.getString("type"));
            outbox.setBizKey(rs.getString("biz_key"));
            
            // sharding_key 通用分片键字段
            Object shardingKeyObj = rs.getObject("sharding_key");
            if (shardingKeyObj != null) {
                outbox.setShardingKey(shardingKeyObj.toString());
            }
            
            // store_id 用于 ShardingSphere 分片路由
            Object storeIdObj = rs.getObject("store_id");
            if (storeIdObj != null) {
                if (storeIdObj instanceof Long) {
                    outbox.setStoreId((Long) storeIdObj);
                } else if (storeIdObj instanceof Number) {
                    outbox.setStoreId(((Number) storeIdObj).longValue());
                } else {
                    try {
                        outbox.setStoreId(Long.parseLong(storeIdObj.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析 store_id: {}", storeIdObj);
                    }
                }
            }
            
            Object shardIdObj = rs.getObject("shard_id");
            if (shardIdObj != null) {
                outbox.setShardId(rs.getInt("shard_id"));
            }
            
            outbox.setTopic(rs.getString("topic"));
            outbox.setTag(rs.getString("tag"));
            outbox.setMessageKey(rs.getString("message_key"));
            outbox.setPayload(rs.getString("payload"));
            
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                try {
                    outbox.setStatus(Outbox.OutboxStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    log.warn("未知的 outbox 状态: {}", statusStr);
                    outbox.setStatus(Outbox.OutboxStatus.NEW);
                }
            }
            
            outbox.setRetryCount(rs.getInt("retry_count"));
            
            Timestamp nextRetryTime = rs.getTimestamp("next_retry_time");
            if (nextRetryTime != null) {
                outbox.setNextRetryTime(nextRetryTime.toLocalDateTime());
            }
            
            outbox.setLockOwner(rs.getString("lock_owner"));
            
            Timestamp lockTime = rs.getTimestamp("lock_time");
            if (lockTime != null) {
                outbox.setLockTime(lockTime.toLocalDateTime());
            }
            
            Timestamp lockUntil = rs.getTimestamp("lock_until");
            if (lockUntil != null) {
                outbox.setLockUntil(lockUntil.toLocalDateTime());
            }
            
            outbox.setLastError(rs.getString("last_error"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                outbox.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                outbox.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                outbox.setCompletedAt(completedAt.toLocalDateTime());
            }
            
            // 兼容旧字段
            outbox.setMessageBody(rs.getString("message_body"));
            outbox.setErrorMessage(rs.getString("error_message"));
            
            Timestamp sentAt = rs.getTimestamp("sent_at");
            if (sentAt != null) {
                outbox.setSentAt(sentAt.toLocalDateTime());
            }
            
            return outbox;
        }
    }
}

