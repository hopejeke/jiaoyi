# 电商交易系统 - 下单接口文档

## 项目概述

这是一个基于Spring Boot的电商交易系统，提供完整的订单管理功能。

## 技术栈

- Spring Boot 2.7.14
- Spring Data JPA
- MySQL 8.0
- Maven
- Lombok
- Jackson

## 快速开始

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 8.0+

### 2. 数据库配置

1. 创建数据库：
```sql
CREATE DATABASE jiaoyi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改 `src/main/resources/application.yml` 中的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jiaoyi?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

应用将在 http://localhost:8080 启动。

## API接口文档

### 1. 创建订单

**接口地址：** `POST /api/orders`

**请求参数：**
```json
{
  "userId": 1001,
  "receiverName": "张三",
  "receiverPhone": "13800138001",
  "receiverAddress": "北京市朝阳区xxx街道xxx号",
  "remark": "请尽快发货",
  "orderItems": [
    {
      "productId": 2001,
      "productName": "iPhone 15 Pro",
      "productImage": "https://example.com/iphone15pro.jpg",
      "unitPrice": 299.00,
      "quantity": 1
    },
    {
      "productId": 2002,
      "productName": "MacBook Air M2",
      "productImage": "https://example.com/macbook.jpg",
      "unitPrice": 599.00,
      "quantity": 1
    }
  ]
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "订单创建成功",
  "data": {
    "id": 1,
    "orderNo": "ORD2023120112345678",
    "userId": 1001,
    "status": "PENDING",
    "totalAmount": 898.00,
    "receiverName": "张三",
    "receiverPhone": "13800138001",
    "receiverAddress": "北京市朝阳区xxx街道xxx号",
    "remark": "请尽快发货",
    "createTime": "2023-12-01T10:30:00",
    "updateTime": "2023-12-01T10:30:00",
    "orderItems": [
      {
        "id": 1,
        "productId": 2001,
        "productName": "iPhone 15 Pro",
        "productImage": "https://example.com/iphone15pro.jpg",
        "unitPrice": 299.00,
        "quantity": 1,
        "subtotal": 299.00
      },
      {
        "id": 2,
        "productId": 2002,
        "productName": "MacBook Air M2",
        "productImage": "https://example.com/macbook.jpg",
        "unitPrice": 599.00,
        "quantity": 1,
        "subtotal": 599.00
      }
    ]
  }
}
```

### 2. 根据订单号查询订单

**接口地址：** `GET /api/orders/orderNo/{orderNo}`

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "orderNo": "ORD2023120112345678",
    "userId": 1001,
    "status": "PENDING",
    "totalAmount": 898.00,
    "receiverName": "张三",
    "receiverPhone": "13800138001",
    "receiverAddress": "北京市朝阳区xxx街道xxx号",
    "remark": "请尽快发货",
    "createTime": "2023-12-01T10:30:00",
    "updateTime": "2023-12-01T10:30:00",
    "orderItems": [...]
  }
}
```

### 3. 根据订单ID查询订单

**接口地址：** `GET /api/orders/{orderId}`

### 4. 查询用户订单列表

**接口地址：** `GET /api/orders/user/{userId}`

### 5. 分页查询用户订单

**接口地址：** `GET /api/orders/user/{userId}/page?page=0&size=10&sortBy=createTime&sortDir=desc`

### 6. 根据状态查询用户订单

**接口地址：** `GET /api/orders/user/{userId}/status/{status}`

**状态值：**
- PENDING: 待支付
- PAID: 已支付
- SHIPPED: 已发货
- DELIVERED: 已送达
- CANCELLED: 已取消

### 7. 更新订单状态

**接口地址：** `PUT /api/orders/{orderId}/status?status=PAID`

### 8. 取消订单

**接口地址：** `PUT /api/orders/{orderId}/cancel`

## 库存管理接口

### 1. 查询商品库存

**接口地址：** `GET /api/inventory/product/{productId}`

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "productId": 2001,
    "productName": "iPhone 15 Pro",
    "currentStock": 100,
    "lockedStock": 0,
    "availableStock": 100,
    "minStock": 10,
    "maxStock": 1000,
    "createTime": "2023-12-01T10:30:00",
    "updateTime": "2023-12-01T10:30:00"
  }
}
```

### 2. 查询库存不足的商品

**接口地址：** `GET /api/inventory/low-stock`

### 3. 检查库存是否充足

**接口地址：** `POST /api/inventory/check-stock`

**请求参数：**
```json
{
  "productId": 2001,
  "quantity": 5
}
```

## 测试用例

### 使用curl测试创建订单

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1001,
    "receiverName": "张三",
    "receiverPhone": "13800138001",
    "receiverAddress": "北京市朝阳区xxx街道xxx号",
    "remark": "请尽快发货",
    "orderItems": [
      {
        "productId": 2001,
        "productName": "iPhone 15 Pro",
        "productImage": "https://example.com/iphone15pro.jpg",
        "unitPrice": 299.00,
        "quantity": 1
      }
    ]
  }'
```

### 使用curl测试查询订单

```bash
curl -X GET http://localhost:8080/api/orders/orderNo/ORD2023120112345678
```

## 数据库表结构

### orders表
- id: 主键
- order_no: 订单号（唯一）
- user_id: 用户ID
- status: 订单状态
- total_amount: 订单总金额
- receiver_name: 收货人姓名
- receiver_phone: 收货人电话
- receiver_address: 收货地址
- remark: 备注
- create_time: 创建时间
- update_time: 更新时间

### order_items表
- id: 主键
- order_id: 订单ID（外键）
- product_id: 商品ID
- product_name: 商品名称
- product_image: 商品图片
- unit_price: 商品单价
- quantity: 购买数量
- subtotal: 小计金额

## 项目结构

```
src/main/java/com/jiaoyi/
├── JiaoyiApplication.java          # 启动类
├── common/
│   └── ApiResponse.java            # 统一响应格式
├── controller/
│   └── OrderController.java        # 订单控制器
├── dto/
│   ├── CreateOrderRequest.java    # 创建订单请求DTO
│   └── OrderResponse.java         # 订单响应DTO
├── entity/
│   ├── Order.java                 # 订单实体
│   ├── OrderItem.java             # 订单项实体
│   └── OrderStatus.java           # 订单状态枚举
├── exception/
│   ├── BusinessException.java     # 业务异常
│   └── GlobalExceptionHandler.java # 全局异常处理器
├── repository/
│   ├── OrderRepository.java       # 订单数据访问层
│   └── OrderItemRepository.java   # 订单项数据访问层
└── service/
    └── OrderService.java          # 订单服务层
```

## 注意事项

1. 确保MySQL服务已启动
2. 修改数据库连接配置
3. 首次启动会自动创建表结构
4. 生产环境建议修改日志级别
5. 建议配置数据库连接池参数
