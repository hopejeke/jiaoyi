package com.jiaoyi.order.mapper;

import com.jiaoyi.order.entity.UserOrderIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户订单索引表 Mapper
 */
@Mapper
public interface UserOrderIndexMapper {

    /**
     * 插入索引记录
     *
     * @param index 索引对象
     * @return 影响行数
     */
    int insert(UserOrderIndex index);

    /**
     * 根据用户ID查询订单索引列表（按创建时间倒序）
     * ShardingSphere 会根据 user_id 精准路由到对应的分片
     *
     * @param userId 用户ID
     * @return 索引列表
     */
    List<UserOrderIndex> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和订单状态查询订单索引列表
     *
     * @param userId      用户ID
     * @param orderStatus 订单状态
     * @return 索引列表
     */
    List<UserOrderIndex> selectByUserIdAndStatus(@Param("userId") Long userId,
                                                  @Param("orderStatus") Integer orderStatus);

    /**
     * 根据用户ID分页查询订单索引列表
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 索引列表
     */
    List<UserOrderIndex> selectByUserIdWithPage(@Param("userId") Long userId,
                                                 @Param("offset") Integer offset,
                                                 @Param("limit") Integer limit);

    /**
     * 更新订单状态（当订单状态变化时，同步更新索引表）
     *
     * @param orderId     订单ID
     * @param userId      用户ID（分片键，必须提供）
     * @param orderStatus 新的订单状态
     * @return 影响行数
     */
    int updateOrderStatus(@Param("orderId") Long orderId,
                          @Param("userId") Long userId,
                          @Param("orderStatus") Integer orderStatus);

    /**
     * 根据订单ID和用户ID删除索引记录
     *
     * @param orderId 订单ID
     * @param userId  用户ID（分片键，必须提供）
     * @return 影响行数
     */
    int deleteByOrderIdAndUserId(@Param("orderId") Long orderId,
                                  @Param("userId") Long userId);

    /**
     * 根据订单ID查询索引记录（用于补偿，会触发广播查询）
     *
     * @param orderId 订单ID
     * @return 索引对象
     */
    UserOrderIndex selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量插入索引记录
     *
     * @param indexes 索引列表
     * @return 影响行数
     */
    int batchInsert(@Param("indexes") List<UserOrderIndex> indexes);
}
