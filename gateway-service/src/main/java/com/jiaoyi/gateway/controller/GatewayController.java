package com.jiaoyi.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * API网关控制器
 * 统一处理前端请求，路由到各个微服务
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GatewayController {
    
    private final RestTemplate restTemplate;
    
    @Value("${gateway.product-service-url:http://localhost:8081}")
    private String productServiceUrl;
    
    @Value("${gateway.order-service-url:http://localhost:8082}")
    private String orderServiceUrl;
    
    @Value("${gateway.coupon-service-url:http://localhost:8083}")
    private String couponServiceUrl;
    
    /**
     * 商品服务路由
     */
    @RequestMapping(value = {
        "/products/**",
        "/stores/**",
        "/store-products/**",
        "/inventory/**",
        "/inventory-cache/**",
        "/product-cache/**",
        "/cache-consistency/**"
    }, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> routeToProductService(HttpServletRequest request) {
        return route(request, productServiceUrl);
    }
    
    /**
     * 订单服务路由
     */
    @RequestMapping(value = {
        "/orders/**",
        "/payment/**",
        "/order-timeout/**"
    }, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> routeToOrderService(HttpServletRequest request) {
        return route(request, orderServiceUrl);
    }
    
    /**
     * 优惠券服务路由
     */
    @RequestMapping(value = {
        "/coupons/**"
    }, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> routeToCouponService(HttpServletRequest request) {
        return route(request, couponServiceUrl);
    }
    
    /**
     * 通用路由方法
     */
    private ResponseEntity<?> route(HttpServletRequest request, String targetServiceUrl) {
        try {
            // 获取请求路径（去掉 /api 前缀）
            String requestPath = request.getRequestURI();
            String targetPath = requestPath; // 保持原路径，因为各个服务的Controller已经定义了 /api/xxx
            
            // 构建目标URL
            String targetUrl = targetServiceUrl + targetPath;
            
            // 获取查询参数
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                targetUrl += "?" + queryString;
            }
            
            log.info("网关转发请求: {} -> {}", requestPath, targetUrl);
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                // 跳过一些不需要转发的头
                if (!headerName.equalsIgnoreCase("host") && 
                    !headerName.equalsIgnoreCase("content-length")) {
                    headers.add(headerName, request.getHeader(headerName));
                }
            }
            
            // 获取请求体
            String requestBody = null;
            if (request.getContentLength() > 0) {
                try {
                    requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.error("读取请求体失败", e);
                }
            }
            
            // 构建HTTP实体
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            // 根据请求方法转发
            HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
            ResponseEntity<String> response = restTemplate.exchange(
                URI.create(targetUrl),
                httpMethod,
                entity,
                String.class
            );
            
            log.info("网关转发响应: {} -> 状态码: {}, 响应体长度: {}", 
                targetUrl, response.getStatusCode(), 
                response.getBody() != null ? response.getBody().length() : 0);
            
            // 构建响应头，确保 Content-Type 正确
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            
            // 确保 Content-Type 是 application/json（如果后端返回的是 JSON）
            if (response.getBody() != null && 
                (response.getBody().trim().startsWith("{") || response.getBody().trim().startsWith("["))) {
                responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            }
            
            // 返回响应，保持原始状态码和响应体
            return ResponseEntity.status(response.getStatusCode())
                .headers(responseHeaders)
                .body(response.getBody());
            
        } catch (Exception e) {
            log.error("网关转发失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"code\":500,\"message\":\"网关转发失败: " + e.getMessage() + "\"}");
        }
    }
}

