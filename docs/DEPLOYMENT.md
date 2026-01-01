# 生产环境部署指南

## 📋 前置要求

### 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+

### 服务依赖
- Product Service (端口 8081)
- Order Service (端口 8082)
- Gateway Service (端口 8080)
- Coupon Service (端口 8083，可选)

---

## 🔐 环境变量配置

### 必需的环境变量

创建 `.env` 文件或设置系统环境变量：

```bash
# 数据库配置
DB_HOST=your-db-host
DB_PORT=3306
DB_USERNAME=your-username
DB_PASSWORD=your-password

# Redis 配置
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# Stripe 配置（支付）
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# 支付宝配置
ALIPAY_APP_ID=your-app-id
ALIPAY_PRIVATE_KEY=your-private-key
ALIPAY_PUBLIC_KEY=your-public-key

# DoorDash 配置（配送）
DOORDASH_API_KEY=your-api-key
DOORDASH_API_SECRET=your-api-secret
DOORDASH_MOCK_ENABLED=false  # 生产环境设为 false

# 其他配置
SPRING_PROFILES_ACTIVE=prod
```

---

## 🚀 部署步骤

### 1. 数据库初始化

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE jiaoyi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE jiaoyi_0 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE jiaoyi_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE jiaoyi_2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 运行初始化脚本（应用启动时会自动创建表）
# 或手动执行 SQL 脚本
```

### 2. 编译项目

```bash
mvn clean package -DskipTests
```

### 3. 启动服务

#### 方式1：直接运行 JAR

```bash
# Product Service
java -jar product-service/target/product-service-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://${DB_HOST}:3306/jiaoyi \
  --spring.datasource.username=${DB_USERNAME} \
  --spring.datasource.password=${DB_PASSWORD}

# Order Service
java -jar order-service/target/order-service-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://${DB_HOST}:3306/jiaoyi \
  --spring.datasource.username=${DB_USERNAME} \
  --spring.datasource.password=${DB_PASSWORD}

# Gateway Service
java -jar gateway-service/target/gateway-service-1.0.0.jar \
  --spring.profiles.active=prod
```

#### 方式2：使用 Docker（如果配置了）

```bash
docker-compose up -d
```

### 4. 健康检查

```bash
# 检查服务状态
curl http://localhost:8081/actuator/health  # Product Service
curl http://localhost:8082/actuator/health  # Order Service
curl http://localhost:8080/actuator/health  # Gateway Service
```

---

## 📊 监控和日志

### 日志位置
- Product Service: `logs/product-service.log`
- Order Service: `logs/order-service.log`
- Gateway Service: `logs/gateway-service.log`

### 监控端点
- Health: `http://localhost:PORT/actuator/health`
- Metrics: `http://localhost:PORT/actuator/metrics`
- Info: `http://localhost:PORT/actuator/info`

---

## 🔧 常见问题

### 1. 数据库连接失败
- 检查数据库是否启动
- 检查连接字符串和凭据
- 检查防火墙规则

### 2. Redis 连接失败
- 检查 Redis 是否启动
- 检查 Redis 配置

### 3. 端口被占用
- 检查端口占用：`netstat -an | grep PORT`
- 修改 `application.properties` 中的端口配置

---

## 🔄 更新部署

1. 停止旧服务
2. 备份数据库
3. 部署新版本
4. 启动服务
5. 验证健康检查

---

## 📞 支持

如有问题，请查看：
- 日志文件
- 项目文档：`docs/` 目录
- GitHub Issues






