package com.jiaoyi.order.interceptor;

import com.jiaoyi.order.annotation.RequireAuth;
import com.jiaoyi.order.annotation.RequirePermission;
import com.jiaoyi.order.security.UserContext;
import com.jiaoyi.order.security.UserContextHolder;
import com.jiaoyi.order.security.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证拦截器
 * 拦截所有请求，验证用户身份和权限
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 只拦截Controller方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 2. 检查方法或类上是否有@RequireAuth注解
        RequireAuth methodAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        RequireAuth classAuth = handlerMethod.getBeanType().getAnnotation(RequireAuth.class);

        boolean requireAuth = methodAuth != null || classAuth != null;

        // 3. 如果需要认证，解析token并验证
        if (requireAuth) {
            String token = extractToken(request);

            if (token == null || token.isEmpty()) {
                log.warn("请求未携带token: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
                return false;
            }

            // 解析token并设置用户上下文
            UserContext userContext = parseToken(token);
            if (userContext == null) {
                log.warn("token无效: {}", token);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\"}");
                return false;
            }

            UserContextHolder.setContext(userContext);
            log.debug("用户认证成功: userId={}, userType={}", userContext.getUserId(), userContext.getUserType());
        }

        // 4. 检查方法或类上是否有@RequirePermission注解
        RequirePermission methodPermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        RequirePermission classPermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);

        RequirePermission permission = methodPermission != null ? methodPermission : classPermission;

        if (permission != null) {
            UserContext userContext = UserContextHolder.getContext();
            if (userContext == null) {
                log.warn("需要权限但用户未登录: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
                return false;
            }

            // 管理员拥有所有权限
            if (userContext.isAdmin()) {
                log.debug("管理员用户，跳过权限检查: userId={}", userContext.getUserId());
                return true;
            }

            // 验证权限
            boolean hasPermission;
            if (permission.requireAll()) {
                hasPermission = userContext.hasAllPermissions(permission.value());
            } else {
                hasPermission = userContext.hasAnyPermission(permission.value());
            }

            if (!hasPermission) {
                log.warn("用户无权限访问: userId={}, required={}, actual={}",
                        userContext.getUserId(), permission.value(), userContext.getPermissions());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
                return false;
            }

            log.debug("权限验证通过: userId={}, permissions={}", userContext.getUserId(), permission.value());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除ThreadLocal，防止内存泄漏
        UserContextHolder.clear();
    }

    /**
     * 从请求中提取token
     * 支持两种方式：
     * 1. Header: Authorization: Bearer <token>
     * 2. Query Parameter: token=<token>
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 从Header中获取
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 2. 从Query Parameter中获取
        String token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * 解析token，获取用户信息
     * TODO: 这里需要实现真实的JWT解析逻辑
     *
     * 示例实现：
     * - 解析JWT token
     * - 验证签名
     * - 验证过期时间
     * - 从token中提取用户信息
     */
    private UserContext parseToken(String token) {
        try {
            // TODO: 实现JWT解析
            // 这里是示例代码，实际应该使用JWT库（如jjwt）解析token

            // 临时实现：允许测试token通过
            if ("test-token-admin".equals(token)) {
                // 管理员测试token
                Set<String> permissions = new HashSet<>();
                permissions.add("*"); // 所有权限
                return new UserContext(1L, "admin", UserType.ADMIN, null, permissions);
            } else if ("test-token-merchant".equals(token)) {
                // 商家测试token
                Set<String> permissions = new HashSet<>();
                permissions.add("merchant:manage:orders");
                permissions.add("merchant:manage:refunds");
                permissions.add("refund:process");
                permissions.add("refund:view");
                return new UserContext(100L, "merchant1", UserType.MERCHANT, 1001L, permissions);
            } else if ("test-token-customer".equals(token)) {
                // 顾客测试token
                Set<String> permissions = new HashSet<>();
                permissions.add("order:view");
                permissions.add("order:create");
                permissions.add("refund:apply");
                permissions.add("refund:view");
                return new UserContext(1000L, "customer1", UserType.CUSTOMER, null, permissions);
            }

            // TODO: 正式环境应该返回null或抛出异常
            return null;

        } catch (Exception e) {
            log.error("解析token失败", e);
            return null;
        }
    }
}
