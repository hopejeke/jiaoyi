# 🛒 交易系统

基于Spring Boot构建的完整电商订单管理系统，包含订单管理、支付处理、库存管理、优惠券系统等功能。

## 🚀 功能特性

### 核心功能
- **订单管理**: 完整的订单生命周期管理
- **支付系统**: 支持支付宝、微信支付等多种支付方式
- **库存管理**: 智能库存锁定、扣减、释放机制
- **优惠券系统**: 灵活的优惠券管理和使用
- **用户管理**: 个人订单查看和管理
- **数据分析**: 订单统计和分析功能

### 技术栈
- **后端**: Spring Boot 3.2.0, MyBatis, MySQL 8.0
- **前端**: HTML5, CSS3, JavaScript (ES6+)
- **缓存**: Redis, Redisson
- **数据库**: MySQL 8.0
- **构建工具**: Maven 3.9

## 📱 页面功能

### 1. 首页 (`/`)
- 系统概览和快速导航
- 实时统计数据展示
- 功能模块介绍

### 2. 订单创建 (`/test-payment.html`)
- 创建新订单
- 优惠券使用
- 支付流程测试
- 模拟支付成功/失败

### 3. 我的订单 (`/my-orders.html`)
- 个人订单列表
- 订单状态筛选
- 订单操作（支付、取消、确认收货等）
- 分页显示

### 4. 订单管理 (`/order-list.html`)
- 管理员订单列表
- 多条件搜索筛选
- 订单状态管理
- 订单详情查看

### 5. 订单详情 (`/order-detail.html`)
- 订单详细信息展示
- 商品清单
- 收货信息
- 订单时间线
- 订单操作

## 🛠️ 快速开始

### 环境要求
- Java 21+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+

### 启动步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd jiaoyi
   ```

2. **配置数据库**
   ```bash
   # 创建数据库
   mysql -u root -p < src/main/resources/sql/schema.sql
   
   # 导入测试数据
   mysql -u root -p jiaoyi < src/main/resources/sql/data.sql
   ```

3. **配置Redis**
   ```bash
   # 启动Redis服务
   redis-server
   ```

4. **修改配置**
   ```yaml
   # src/main/resources/application.yml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/jiaoyi
       username: your_username
       password: your_password
     data:
       redis:
         host: localhost
         port: 6379
   ```

5. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

6. **访问系统**
   - 首页: http://localhost:8080/
   - 创建订单: http://localhost:8080/test-payment.html
   - 我的订单: http://localhost:8080/my-orders.html
   - 订单管理: http://localhost:8080/order-list.html
   - 订单详情: http://localhost:8080/order-detail.html

## 📊 API接口

### 订单相关
- `GET /api/orders` - 获取所有订单
- `GET /api/orders/page` - 分页查询订单（支持筛选）
- `GET /api/orders/{id}` - 获取订单详情
- `POST /api/orders` - 创建订单
- `PUT /api/orders/{id}/status` - 更新订单状态
- `POST /api/orders/{id}/pay` - 支付订单

### 用户订单
- `GET /api/orders/user/{userId}` - 获取用户订单列表
- `GET /api/orders/user/{userId}/page` - 分页查询用户订单
- `GET /api/orders/user/{userId}/status/{status}` - 按状态查询用户订单

### 支付相关
- `POST /api/payment/alipay/notify` - 支付宝支付回调
- `GET /api/payment/alipay/return` - 支付宝支付返回
- `GET /api/payment/query/{paymentNo}` - 查询支付结果

## 🎯 使用说明

### 创建订单
1. 访问 `http://localhost:8080/test-payment.html`
2. 填写订单信息（用户ID、收货信息、商品信息）
3. 可选择使用优惠券
4. 点击"创建订单"
5. 进行支付操作

### 查看订单
1. 访问 `http://localhost:8080/my-orders.html`
2. 输入用户ID
3. 查看订单列表，可按状态筛选
4. 点击"查看详情"查看订单详细信息

### 管理订单
1. 访问 `http://localhost:8080/order-list.html`
2. 使用搜索条件筛选订单
3. 查看订单列表和状态
4. 进行订单状态管理操作

## 🔧 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### Redis配置
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 5000ms
```

### 支付配置
```yaml
alipay:
  app-id: your_app_id
  private-key: your_private_key
  alipay-public-key: alipay_public_key
  gateway-url: https://openapi.alipay.com/gateway.do
  notify-url: http://your-domain.com/api/payment/alipay/notify
  return-url: http://your-domain.com/api/payment/alipay/return
```

## 📝 测试数据

系统预置了以下测试数据：

### 用户
- 用户ID: 1001 (测试用户)

### 商品
- 商品ID: 2001 (iPhone 15 Pro) - 库存: 150
- 商品ID: 2002 (质朴的钢鞋子) - 库存: 50

### 优惠券
- DISCOUNT10: 10%折扣券
- FIXED20: 20元固定优惠券
- NEWUSER: 新用户优惠券

## 🐛 常见问题

### 1. 端口被占用
```bash
# 查找占用8080端口的进程
netstat -ano | findstr :8080
# 结束进程
taskkill /PID <进程ID> /F
```

### 2. 数据库连接失败
- 检查MySQL服务是否启动
- 确认数据库配置信息正确
- 检查数据库用户权限

### 3. Redis连接失败
- 检查Redis服务是否启动
- 确认Redis配置信息正确
- 检查Redis端口是否开放

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交Issue和Pull Request来改进这个项目！

## 📞 联系方式

如有问题，请通过以下方式联系：
- 提交Issue
- 发送邮件

---

**注意**: 这是一个演示项目，生产环境使用前请进行充分的安全测试和性能优化。
