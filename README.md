# 交易平台 (JiaoYi) - 在线点餐系统

一个基于 Spring Boot 的微服务架构在线点餐平台，支持商品管理、订单处理、支付集成和第三方配送服务。

## 🚀 项目简介

这是一个完整的在线点餐系统 Demo，包含以下核心功能：

- **商品管理**：商品、菜单、商户管理
- **订单处理**：订单创建、状态管理、分库分表
- **支付集成**：Stripe、支付宝支付
- **配送服务**：DoorDash 配送集成
- **优惠券系统**：优惠券发放和使用

## 🏗️ 技术架构

### 技术栈
- **后端框架**：Spring Boot 3.x
- **数据库**：MySQL 8.0 + ShardingSphere（分库分表）
- **缓存**：Redis
- **消息队列**：RocketMQ（可选）
- **支付**：Stripe、支付宝
- **配送**：DoorDash API

### 微服务架构
- **gateway-service**：网关服务（端口 8080）
- **product-service**：商品服务（端口 8081）
- **order-service**：订单服务（端口 8082）
- **coupon-service**：优惠券服务（端口 8083）

## 📦 快速开始

### 前置要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 环境配置

1. **克隆项目**
```bash
git clone https://github.com/your-username/jiaoyi.git
cd jiaoyi
```

2. **配置环境变量**

复制环境变量示例文件：
```bash
cp .env.example .env
```

编辑 `.env` 文件，填入你的配置：
```bash
# 数据库
DB_HOST=localhost
DB_USERNAME=root
DB_PASSWORD=your-password

# Stripe（支付）
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# 支付宝
ALIPAY_APP_ID=your-app-id
ALIPAY_PRIVATE_KEY=your-private-key
ALIPAY_PUBLIC_KEY=your-public-key

# DoorDash（配送）
DOORDASH_API_KEY=your-api-key
DOORDASH_API_SECRET=your-api-secret
```

3. **初始化数据库**

应用启动时会自动创建数据库表，或手动执行 SQL 脚本。

4. **启动服务**

```bash
# 编译项目
mvn clean package -DskipTests

# 启动 Product Service
cd product-service
java -jar target/product-service-1.0.0.jar

# 启动 Order Service（新终端）
cd order-service
java -jar target/order-service-1.0.0.jar

# 启动 Gateway Service（新终端）
cd gateway-service
java -jar target/gateway-service-1.0.0.jar
```

5. **访问服务**

- Gateway: http://localhost:8080
- Product Service: http://localhost:8081
- Order Service: http://localhost:8082

## 📚 文档

- [部署指南](docs/DEPLOYMENT.md)
- [项目路线图](docs/PROJECT_ROADMAP.md)
- [第90天生产准备清单](docs/DAY_90_PRODUCTION_READINESS.md)
- [DoorDash 集成文档](docs/DOORDASH_INTEGRATION.md)
- [费用计算说明](docs/FEE_CALCULATION.md)

## 🔐 安全说明

**重要**：本项目使用环境变量管理敏感信息（API 密钥、数据库密码等）。

- 不要将 `.env` 文件提交到 Git
- 生产环境使用环境变量或密钥管理服务
- 定期轮换 API 密钥

## 🧪 测试

```bash
# 运行测试
mvn test

# 跳过测试打包
mvn clean package -DskipTests
```

## 📊 监控

服务提供 Actuator 端点用于监控：

- Health: `http://localhost:PORT/actuator/health`
- Metrics: `http://localhost:PORT/actuator/metrics`

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

## 📄 许可证

MIT License

## 🙏 致谢

感谢所有开源项目的贡献者。






