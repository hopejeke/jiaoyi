package com.jiaoyi.order.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 用户上下文信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户类型（CUSTOMER-顾客, MERCHANT-商家, ADMIN-管理员）
     */
    private UserType userType;

    /**
     * 商家ID（如果是商家用户）
     */
    private Long merchantId;

    /**
     * 权限列表
     */
    private Set<String> permissions;

    /**
     * 是否是管理员
     */
    public boolean isAdmin() {
        return UserType.ADMIN.equals(userType);
    }

    /**
     * 是否是商家
     */
    public boolean isMerchant() {
        return UserType.MERCHANT.equals(userType);
    }

    /**
     * 是否有指定权限
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 是否有指定权限中的任意一个
     */
    public boolean hasAnyPermission(String... permissions) {
        if (this.permissions == null || permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (this.permissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否有指定的所有权限
     */
    public boolean hasAllPermissions(String... permissions) {
        if (this.permissions == null || permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (!this.permissions.contains(permission)) {
                return false;
            }
        }
        return true;
    }
}
