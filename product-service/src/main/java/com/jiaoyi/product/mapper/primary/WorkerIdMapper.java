package com.jiaoyi.product.mapper.primary;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 * 雪花算法 Worker-ID 分配表 Mapper
 * 
 * @author Administrator
 */
@Mapper
public interface WorkerIdMapper {
    
    /**
     * 根据实例标识查找 worker-id
     * 
     * @param instance 实例标识
     * @return worker-id，如果不存在返回 null
     */
    Integer findByInstance(@Param("instance") String instance);
    
    /**
     * 查找所有已分配的 worker-id
     * 
     * @return 已分配的 worker-id 列表
     */
    List<Integer> findAllocatedWorkerIds();
    
    
    /**
     * 插入新的 worker-id 分配记录
     * 
     * @param workerId worker-id
     * @param instance 实例标识
     * @return 影响行数
     */
    int insert(@Param("workerId") Integer workerId, @Param("instance") String instance);
    
    /**
     * 更新心跳时间
     * 
     * @param workerId worker-id
     * @param instance 实例标识
     * @return 影响行数
     */
    int updateHeartbeat(@Param("workerId") Integer workerId, @Param("instance") String instance);
    
    /**
     * 删除 worker-id 分配记录（实例关闭时调用）
     * 
     * @param instance 实例标识
     * @return 影响行数
     */
    int deleteByInstance(@Param("instance") String instance);
    
    /**
     * 删除过期的 worker-id 分配记录（心跳超时）
     * 
     * @param expireTime 过期时间（时间戳）
     * @return 删除的记录数
     */
    int deleteExpired(@Param("expireTime") Timestamp expireTime);
}

