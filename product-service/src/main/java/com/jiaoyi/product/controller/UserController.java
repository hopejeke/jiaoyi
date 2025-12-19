package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.User;
import com.jiaoyi.product.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    /**
     * 创建用户
     */
    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user) {
        log.info("创建用户，email: {}", user.getEmail());
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.ok(ApiResponse.success("创建成功", createdUser));
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        log.info("查询用户，ID: {}", id);
        Optional<User> user = userService.getUserById(id);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "用户不存在")));
    }
    
    /**
     * 根据email查询用户
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<User>> getUserByEmail(@PathVariable String email) {
        log.info("查询用户，email: {}", email);
        Optional<User> user = userService.getUserByEmail(email);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "用户不存在")));
    }
    
    /**
     * 根据phone查询用户
     */
    @GetMapping("/phone/{phone}")
    public ResponseEntity<ApiResponse<User>> getUserByPhone(@PathVariable String phone) {
        log.info("查询用户，phone: {}", phone);
        Optional<User> user = userService.getUserByPhone(phone);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "用户不存在")));
    }
    
    /**
     * 根据openid查询用户
     */
    @GetMapping("/openid/{openid}")
    public ResponseEntity<ApiResponse<User>> getUserByOpenid(@PathVariable String openid) {
        log.info("查询用户，openid: {}", openid);
        Optional<User> user = userService.getUserByOpenid(openid);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "用户不存在")));
    }
    
    /**
     * 查询所有用户
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        log.info("查询所有用户");
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("查询成功", users));
    }
    
    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable Long id,
            @RequestBody User user) {
        log.info("更新用户，ID: {}", id);
        user.setId(id);
        try {
            User updatedUser = userService.updateUser(user);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedUser));
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.info("删除用户，ID: {}", id);
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}

