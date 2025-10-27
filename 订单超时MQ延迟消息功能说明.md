# 🐰 订单30分钟超时MQ延迟消息功能

## ✨ 功能概述

使用RabbitMQ的延迟消息插件实现订单创建后30分钟未支付自动取消的功能，相比定时任务更加精确和高效。

## 🔧 技术实现

### 1. 依赖配置
- **Spring Boot AMQP**: RabbitMQ消息队列支持
- **RabbitMQ延迟插件**: 支持延迟消息
- **Redisson**: 分布式锁防止重复处理

### 2. 核心组件

#### RabbitMQConfig
- 配置延迟消息交换机和队列
- 设置消息转换器和确认机制
- 支持延迟消息插件

#### OrderTimeoutMessageService
- `sendOrderTimeoutMessage()`: 发送30分钟延迟消息
- `handleOrderTimeout()`: 处理超时消息
- `cancelTimeoutOrder()`: 取消订单并释放资源

#### OrderTimeoutMessage
- 订单超时消息DTO
- 包含订单ID、订单号、用户ID等信息

## 🚀 使用方法

### 1. 安装RabbitMQ延迟插件

#### Windows
```bash
# 下载延迟插件
wget https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.12.0/rabbitmq_delayed_message_exchange-3.12.0.ez

# 复制到插件目录
copy rabbitmq_delayed_message_exchange-3.12.0.ez %RABBITMQ_HOME%\plugins\

# 启用插件
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

#### Linux/Mac
```bash
# 下载延迟插件
wget https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.12.0/rabbitmq_delayed_message_exchange-3.12.0.ez

# 复制到插件目录
sudo cp rabbitmq_delayed_message_exchange-3.12.0.ez /usr/lib/rabbitmq/lib/rabbitmq_server-*/plugins/

# 启用插件
sudo rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

### 2. 启动RabbitMQ
```bash
# 启动RabbitMQ服务
rabbitmq-server
```

### 3. 测试页面
访问 `http://localhost:8080/order-timeout-mq-test.html` 进行测试：

#### 创建测试订单
1. 填写订单信息
2. 点击"创建测试订单"
3. 系统自动发送30分钟延迟消息

#### 手动发送延迟消息
1. 填写订单信息
2. 选择延迟时间（1分钟、5分钟、30分钟等）
3. 点击"发送测试延迟消息"

#### 订单操作
- **支付订单**: 模拟支付，避免超时取消
- **手动取消**: 立即取消订单
- **刷新列表**: 查看最新订单状态

## 📊 测试步骤

### 1. 快速测试（1分钟延迟）
1. 创建订单
2. 选择"1分钟"延迟
3. 发送测试延迟消息
4. 等待1分钟观察订单自动取消

### 2. 正常流程测试
1. 创建订单 → 自动发送30分钟延迟消息
2. 等待30分钟 → 订单自动取消
3. 创建订单 → 立即支付 → 订单不会取消

### 3. 手动测试
1. 创建订单 → 点击"手动取消订单" → 立即取消
2. 创建订单 → 点击"支付订单" → 订单状态更新

## 🔍 监控和日志

### 关键日志
```
订单创建完成，订单ID: 123, 订单号: ORD123456789
订单超时延迟消息已发送，订单将在30分钟后自动取消（如果未支付），订单ID: 123, 订单号: ORD123456789

接收到订单超时消息，订单ID: 123, 订单号: ORD123456789
订单超时取消成功，订单ID: 123, 订单号: ORD123456789
```

### RabbitMQ管理界面
访问 `http://localhost:15672` 查看：
- 队列状态：`order.timeout.queue`
- 延迟队列：`order.timeout.delay.queue`
- 消息流量和消费情况

## ⚙️ 配置说明

### 延迟时间配置
在 `OrderService.java` 中修改：
```java
// 发送订单超时延迟消息（30分钟后自动取消）
orderTimeoutMessageService.sendOrderTimeoutMessage(order.getId(), orderNo, request.getUserId(), 30);
```

### RabbitMQ配置
在 `application.yml` 中修改：
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

## 🛡️ 安全机制

### 1. 分布式锁
- 使用Redisson分布式锁防止重复处理
- 锁超时时间: 30秒
- 等待时间: 3秒

### 2. 状态检查
- 处理前重新查询订单状态
- 确保订单仍然是待支付状态
- 避免处理已支付或已取消的订单

### 3. 消息确认
- 生产者消息确认
- 消费者手动确认
- 消息重试机制

## 📈 性能优势

### 1. 相比定时任务的优势
- ✅ 更精确的延迟时间
- ✅ 减少数据库查询压力
- ✅ 支持分布式部署
- ✅ 消息持久化
- ✅ 更好的扩展性

### 2. 资源优化
- 按需处理，不占用定时任务线程
- 消息队列天然支持高并发
- 支持消息重试和死信处理

## 🎯 业务价值

- ✅ 自动释放超时订单占用的库存
- ✅ 提高库存周转率
- ✅ 改善用户体验
- ✅ 减少人工干预
- ✅ 保证数据一致性
- ✅ 支持高并发场景

## 🔧 故障排除

### 1. 延迟消息不生效
- 检查RabbitMQ延迟插件是否安装
- 检查交换机配置是否正确
- 查看RabbitMQ日志

### 2. 消息重复处理
- 检查分布式锁配置
- 查看Redis连接状态
- 检查消息确认机制

### 3. 性能问题
- 调整RabbitMQ连接池配置
- 优化消息处理逻辑
- 监控队列长度

现在你的订单系统具备了基于MQ的延迟消息超时取消功能！🎉
