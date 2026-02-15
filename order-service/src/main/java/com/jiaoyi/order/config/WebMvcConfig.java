package com.jiaoyi.order.config;

import com.jiaoyi.order.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")  // 拦截所有/api/**接口
                .excludePathPatterns(
                        "/api/health",       // 健康检查
                        "/api/webhook/**",   // Webhook回调（Stripe等）
                        "/api/public/**"     // 公开接口
                );
    }
}
