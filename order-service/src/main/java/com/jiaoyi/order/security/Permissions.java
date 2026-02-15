package com.jiaoyi.order.security;

/**
 * 权限常量定义
 */
public class Permissions {

    // ========== 订单权限 ==========
    /**
     * 查看订单
     */
    public static final String ORDER_VIEW = "order:view";

    /**
     * 创建订单
     */
    public static final String ORDER_CREATE = "order:create";

    /**
     * 取消订单
     */
    public static final String ORDER_CANCEL = "order:cancel";

    // ========== 退款权限 ==========
    /**
     * 申请退款（用户）
     */
    public static final String REFUND_APPLY = "refund:apply";

    /**
     * 处理退款（商家）
     */
    public static final String REFUND_PROCESS = "refund:process";

    /**
     * 查看退款
     */
    public static final String REFUND_VIEW = "refund:view";

    /**
     * 管理员退款（强制退款）
     */
    public static final String REFUND_ADMIN = "refund:admin";

    // ========== 商家权限 ==========
    /**
     * 管理商家订单
     */
    public static final String MERCHANT_MANAGE_ORDERS = "merchant:manage:orders";

    /**
     * 管理商家退款
     */
    public static final String MERCHANT_MANAGE_REFUNDS = "merchant:manage:refunds";

    // ========== 管理员权限 ==========
    /**
     * 系统管理
     */
    public static final String ADMIN_SYSTEM = "admin:system";

    /**
     * 数据查询
     */
    public static final String ADMIN_QUERY = "admin:query";
}
