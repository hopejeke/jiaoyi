package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.OutboxNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxNode表Mapper
 */
@Mapper
public interface OutboxNodeMapper {
    
    /**
     * 插入节点
     */
    int insert(OutboxNode node);
    
    /**
     * 根据ID查询
     */
    OutboxNode selectById(@Param("id") Long id);
    
    /**
     * 根据nodeId查询节点
     */
    OutboxNode selectByNodeId(@Param("nodeId") String nodeId);
    
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
    List<OutboxNode> selectAvailableNodes(@Param("expiredTime") LocalDateTime expiredTime);
    
    /**
     * 查询过期节点（用于清理）
     */
    List<OutboxNode> selectExpiredNodes(@Param("expiredTime") LocalDateTime expiredTime, @Param("limit") int limit);
    
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

