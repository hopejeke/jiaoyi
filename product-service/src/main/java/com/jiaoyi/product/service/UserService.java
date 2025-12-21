package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.User;
import com.jiaoyi.product.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserMapper userMapper;
    
    /**
     * 创建用户
     */
    @Transactional
    public User createUser(User user) {
        log.info("创建用户，email: {}, name: {}", user.getEmail(), user.getName());
        
        // 检查email是否已存在
        Optional<User> existing = userMapper.selectByEmail(user.getEmail());
        if (existing.isPresent()) {
            throw new RuntimeException("邮箱已存在，email: " + user.getEmail());
        }
        
        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus(200); // 默认活跃状态
        }
        
        // 插入用户
        userMapper.insert(user);
        
        log.info("用户创建成功，ID: {}, email: {}", user.getId(), user.getEmail());
        
        return user;
    }
    
    /**
     * 根据ID查询用户
     */
    public Optional<User> getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    /**
     * 根据email查询用户
     */
    public Optional<User> getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }
    
    /**
     * 根据phone查询用户
     */
    public Optional<User> getUserByPhone(String phone) {
        return userMapper.selectByPhone(phone);
    }
    
    /**
     * 根据openid查询用户
     */
    public Optional<User> getUserByOpenid(String openid) {
        return userMapper.selectByOpenid(openid);
    }
    
    /**
     * 查询所有用户
     */
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }
    
    /**
     * 更新用户
     */
    @Transactional
    public User updateUser(User user) {
        log.info("更新用户，ID: {}", user.getId());
        
        // 检查用户是否存在
        Optional<User> existing = userMapper.selectById(user.getId());
        if (!existing.isPresent()) {
            throw new RuntimeException("用户不存在，ID: " + user.getId());
        }
        
        // 如果修改了email，检查是否重复
        if (user.getEmail() != null && !user.getEmail().equals(existing.get().getEmail())) {
            Optional<User> duplicate = userMapper.selectByEmail(user.getEmail());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(user.getId())) {
                throw new RuntimeException("邮箱已存在，email: " + user.getEmail());
            }
        }
        
        // 更新用户
        int affectedRows = userMapper.update(user);
        if (affectedRows == 0) {
            throw new RuntimeException("用户更新失败");
        }
        
        // 查询更新后的用户
        User updatedUser = userMapper.selectById(user.getId())
                .orElseThrow(() -> new RuntimeException("用户更新失败：更新后无法查询到用户记录"));
        
        log.info("用户更新成功，ID: {}, email: {}", updatedUser.getId(), updatedUser.getEmail());
        
        return updatedUser;
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("删除用户，ID: {}", id);
        
        // 检查用户是否存在
        Optional<User> existing = userMapper.selectById(id);
        if (!existing.isPresent()) {
            throw new RuntimeException("用户不存在，ID: " + id);
        }
        
        // 删除用户
        int affectedRows = userMapper.deleteById(id);
        if (affectedRows == 0) {
            throw new RuntimeException("用户删除失败");
        }
        
        log.info("用户删除成功，ID: {}", id);
    }
}

