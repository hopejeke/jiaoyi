package com.jiaoyi.mapper;

import com.jiaoyi.entity.SchedNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调度节点Mapper
 */
@Mapper
public interface SchedNodeMapper {
    
    /**
     * 插入节点
     */
    int insert(SchedNode schedNode);
    
    /**
     * 根据ID查询
     */
    SchedNode selectById(@Param("id") Long id);
    
    /**
     * 根据nodeId查询
     */
    SchedNode selectByNodeId(@Param("nodeId") String nodeId);
    
    /**
     * 更新心跳过期时间
     */
    int updateExpiredTime(@Param("id") Long id, @Param("expiredTime") LocalDateTime expiredTime);
    
    /**
     * 更新节点信息（IP、端口等）
     */
    int updateByNodeId(@Param("nodeId") String nodeId, 
                       @Param("ip") String ip, 
                       @Param("port") Integer port,
                       @Param("expiredTime") LocalDateTime expiredTime);
    
    /**
     * 查询所有启用的节点（按ID降序排序，用于计算节点索引）
     * 
     * @param expiredTime 过期时间阈值，只查询expired_time大于此时间的节点（即存活的节点）
     * @return 节点列表（按id desc排序）
     */
    List<SchedNode> selectAvailableNodes(@Param("expiredTime") LocalDateTime expiredTime);
    
    /**
     * 查询过期节点（用于清理）
     */
    List<SchedNode> selectExpiredNodes(@Param("expiredTime") LocalDateTime expiredTime, @Param("limit") int limit);
    
    /**
     * 删除过期节点
     */
    int deleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 禁用节点
     */
    int disableNode(@Param("id") Long id);
    
    /**
     * 启用节点
     */
    int enableNode(@Param("id") Long id);
}

