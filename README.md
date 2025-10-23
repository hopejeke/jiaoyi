# 电商交易系统 (jiaoyi)

一个基于Spring Boot的电商交易系统，提供完整的订单管理功能。

## 功能特性

- ✅ 创建订单（自动检查库存）
- ✅ 查询订单（按订单号、订单ID、用户ID）
- ✅ 分页查询订单
- ✅ 按状态查询订单
- ✅ 更新订单状态（自动处理库存）
- ✅ 取消订单（自动解锁库存）
- ✅ 库存管理（查询、检查、预警）
- ✅ 库存变动记录
- ✅ 统一异常处理
- ✅ 参数校验
- ✅ 数据库事务管理

## 技术栈

- Spring Boot 3.2.0
- MyBatis 3.0.2
- PageHelper 1.4.7
- MySQL 8.0
- Maven
- Lombok
- Jackson
- Java 21

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+

### 1. 数据库配置

创建数据库：
```sql
CREATE DATABASE jiaoyi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 修改配置

编辑 `src/main/resources/application.yml` 中的数据库连接信息。

### 3. 启动应用

**方式一：使用Maven**
```bash
mvn spring-boot:run
```

**方式二：使用启动脚本（Windows）**
```bash
start.bat
```

**方式三：打包运行**
```bash
mvn clean package
java -jar target/jiaoyi-1.0.0.jar
```

### 4. 访问应用

应用启动后访问：http://localhost:8080

## API接口

### 创建订单
```
POST /api/orders
```

### 查询订单
```
GET /api/orders/{orderId}
GET /api/orders/orderNo/{orderNo}
```

### 查询用户订单
```
GET /api/orders/user/{userId}
GET /api/orders/user/{userId}/page
```

### 更新订单状态
```
PUT /api/orders/{orderId}/status?status=PAID
```

### 取消订单
```
PUT /api/orders/{orderId}/cancel
```

详细API文档请参考 [API文档.md](API文档.md)

## 项目结构

```
src/main/java/com/jiaoyi/
├── JiaoyiApplication.java          # 启动类
├── common/                          # 通用组件
├── controller/                      # 控制器层
├── dto/                            # 数据传输对象
├── entity/                         # 实体类
├── exception/                      # 异常处理
├── repository/                     # 数据访问层
└── service/                       # 服务层
```

## 故障排除

### Maven依赖问题

如果遇到MySQL依赖下载失败的问题，请按以下步骤解决：

1. **快速修复**：
   ```bash
   fix-maven.bat
   ```

2. **完全重建**：
   ```bash
   rebuild.bat
   ```

3. **手动清理**：
   ```bash
   # 清理Maven缓存
   rmdir /s /q "%USERPROFILE%\.m2\repository\mysql"
   
   # 强制更新依赖
   mvn clean compile -U -s settings.xml
   ```

### 常见问题

1. **依赖下载失败**：使用 `-U` 参数强制更新
2. **网络问题**：项目已配置阿里云镜像加速
3. **版本冲突**：已指定具体版本号避免冲突
4. **IntelliJ IDEA配置问题**：
   - 如果遇到 "Source root is duplicated" 错误
   - 运行 `fix-idea.bat` 或 `reset-idea.bat`
   - 重新导入项目：File -> Open -> 选择pom.xml -> Open as Project
5. **Java版本兼容性问题**：
   - 项目已升级到Java 21和Spring Boot 3.x
   - 运行 `upgrade-to-java21.bat` 完成升级
   - 确保使用Java 21 JDK
   - 注意：包名从javax.*改为jakarta.*
6. **MyBatis升级**：
   - 项目已从JPA升级到MyBatis
   - 运行 `upgrade-to-mybatis.bat` 完成升级
   - 使用XML文件管理SQL语句
   - 支持更灵活的SQL控制

## 开发说明

1. 项目使用Spring Boot自动配置
2. 数据库表结构会在首次启动时自动创建
3. 支持开发、生产环境配置
4. 包含完整的异常处理和参数校验
5. 提供单元测试示例
6. 已配置阿里云Maven镜像加速下载

## 许可证

MIT License
