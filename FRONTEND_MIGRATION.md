# 前端页面微服务适配方案

## 📋 问题说明

原单体应用的前端页面位于 `src/main/resources/static/`，使用相对路径调用后端API（如 `/api/orders`、`/api/products` 等）。

微服务拆分后：
- **product-service** (8081) - 商品、库存、店铺相关
- **order-service** (8082) - 订单、支付相关  
- **coupon-service** (8083) - 优惠券相关

前端页面需要统一入口来访问这些分散的微服务。

## ✅ 解决方案：API网关服务

创建了 **gateway-service** (8080) 作为统一入口：

### 1. 功能
- ✅ **静态资源服务**：提供前端HTML页面
- ✅ **API路由转发**：将前端请求转发到对应的微服务
- ✅ **统一入口**：前端只需访问 `http://localhost:8080`

### 2. 路由规则

| 前端请求路径 | 转发到 | 说明 |
|------------|--------|------|
| `/api/products/**` | `http://localhost:8081` | 商品服务 |
| `/api/stores/**` | `http://localhost:8081` | 店铺服务 |
| `/api/store-products/**` | `http://localhost:8081` | 店铺商品服务 |
| `/api/inventory/**` | `http://localhost:8081` | 库存服务 |
| `/api/inventory-cache/**` | `http://localhost:8081` | 库存缓存服务 |
| `/api/product-cache/**` | `http://localhost:8081` | 商品缓存服务 |
| `/api/cache-consistency/**` | `http://localhost:8081` | 缓存一致性服务 |
| `/api/orders/**` | `http://localhost:8082` | 订单服务 |
| `/api/payment/**` | `http://localhost:8082` | 支付服务 |
| `/api/order-timeout/**` | `http://localhost:8082` | 订单超时服务 |
| `/api/coupons/**` | `http://localhost:8083` | 优惠券服务 |

### 3. 前端页面位置

所有前端HTML页面已复制到：
```
gateway-service/src/main/resources/static/
```

包括：
- `index.html` - 首页
- `product-list.html` - 商品列表
- `product-detail.html` - 商品详情
- `product-admin.html` - 商品管理后台
- `orders.html` - 订单管理
- `payment.html` - 支付中心
- `tests.html` - 系统测试
- 其他测试页面...

### 4. 使用方式

#### 启动顺序
1. 启动 **product-service** (8081)
2. 启动 **order-service** (8082)
3. 启动 **coupon-service** (8083)
4. 启动 **gateway-service** (8080) - **最后启动**

#### 访问前端
打开浏览器访问：`http://localhost:8080`

- 首页：`http://localhost:8080/index.html`
- 商品列表：`http://localhost:8080/product-list.html`
- 订单管理：`http://localhost:8080/orders.html`
- 支付中心：`http://localhost:8080/payment.html`

#### API调用
前端页面**无需修改**，继续使用相对路径：
```javascript
// 这些请求会自动被网关转发到对应的微服务
fetch('/api/orders')           // → http://localhost:8082/api/orders
fetch('/api/products')          // → http://localhost:8081/api/products
fetch('/api/coupons')           // → http://localhost:8083/api/coupons
```

### 5. 配置说明

网关服务配置文件：`gateway-service/src/main/resources/application.properties`

```properties
# 各微服务地址（可根据实际情况修改）
gateway.product-service-url=http://localhost:8081
gateway.order-service-url=http://localhost:8082
gateway.coupon-service-url=http://localhost:8083
```

### 6. 优势

✅ **前端无需修改**：继续使用相对路径，无需改动代码  
✅ **统一入口**：所有请求通过网关，便于监控和日志  
✅ **易于扩展**：新增微服务只需在网关添加路由规则  
✅ **静态资源集中**：前端页面统一管理  

### 7. 注意事项

⚠️ **CORS问题**：如果前端页面和API不在同一域名，可能需要配置CORS。目前所有服务都在同一域名下，无需额外配置。

⚠️ **请求头转发**：网关会转发大部分请求头，但会跳过 `host` 和 `content-length`。

⚠️ **错误处理**：如果目标微服务不可用，网关会返回500错误。建议在生产环境添加重试和熔断机制。

## 🔄 后续优化建议

1. **使用 Spring Cloud Gateway**：当前使用简单的 RestTemplate 转发，可以升级为 Spring Cloud Gateway 获得更好的性能和功能
2. **负载均衡**：如果某个微服务有多个实例，可以集成 Ribbon 或 Spring Cloud LoadBalancer
3. **服务发现**：集成 Eureka 或 Nacos，实现自动服务发现
4. **限流和熔断**：添加限流和熔断机制，保护后端服务
5. **统一认证**：在网关层统一处理认证和授权


