package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户Mapper接口（主库，不分片）
 */
@Mapper
public interface UserMapper {
    
    /**
     * 插入用户
     */
    void insert(User user);
    
    /**
     * 根据ID查询用户
     */
    Optional<User> selectById(@Param("id") Long id);
    
    /**
     * 根据email查询用户
     */
    Optional<User> selectByEmail(@Param("email") String email);
    
    /**
     * 根据phone查询用户
     */
    Optional<User> selectByPhone(@Param("phone") String phone);
    
    /**
     * 根据openid查询用户
     */
    Optional<User> selectByOpenid(@Param("openid") String openid);
    
    /**
     * 查询所有用户
     */
    List<User> selectAll();
    
    /**
     * 更新用户
     */
    int update(User user);
    
    /**
     * 删除用户
     */
    int deleteById(@Param("id") Long id);
}

