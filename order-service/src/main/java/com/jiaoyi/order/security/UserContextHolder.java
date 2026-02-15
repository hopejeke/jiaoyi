package com.jiaoyi.order.security;

/**
 * 用户上下文持有者
 * 使用ThreadLocal存储当前线程的用户信息
 */
public class UserContextHolder {

    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();

    /**
     * 设置当前用户上下文
     */
    public static void setContext(UserContext context) {
        contextHolder.set(context);
    }

    /**
     * 获取当前用户上下文
     */
    public static UserContext getContext() {
        return contextHolder.get();
    }

    /**
     * 清除当前用户上下文
     */
    public static void clear() {
        contextHolder.remove();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        UserContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户类型
     */
    public static UserType getCurrentUserType() {
        UserContext context = getContext();
        return context != null ? context.getUserType() : null;
    }

    /**
     * 获取当前商家ID
     */
    public static Long getCurrentMerchantId() {
        UserContext context = getContext();
        return context != null ? context.getMerchantId() : null;
    }

    /**
     * 检查是否已登录
     */
    public static boolean isAuthenticated() {
        return getContext() != null && getContext().getUserId() != null;
    }
}
