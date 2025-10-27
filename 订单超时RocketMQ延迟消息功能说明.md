# 🚀 订单30分钟超时RocketMQ延迟消息功能

## ✨ 功能概述

使用RocketMQ的延迟消息功能实现订单创建后30分钟未支付自动取消，相比RabbitMQ更简单，无需额外插件。

## 🔧 技术实现

### 1. 依赖配置
- **RocketMQ Spring Boot Starter**: 消息队列支持
- **RocketMQ延迟消息**: 内置延迟消息功能
- **Redisson**: 分布式锁防止重复处理

### 2. 核心组件

#### RocketMQConfig
- 配置Topic和Tag
- 定义消费者组
- 简化配置管理

#### OrderTimeoutMessageService
- `sendOrderTimeoutMessage()`: 发送30分钟延迟消息（使用MessageBuilder构建Spring Message）
- `onMessage()`: 处理超时消息
- `cancelTimeoutOrder()`: 取消订单并释放资源

#### OrderTimeoutMessage
- 订单超时消息DTO
- 包含订单ID、订单号、用户ID等信息

## 🚀 使用方法

### 1. 安装RocketMQ

#### Windows
```bash
# 下载RocketMQ
wget https://archive.apache.org/dist/rocketmq/4.9.4/rocketmq-all-4.9.4-bin-release.zip
unzip rocketmq-all-4.9.4-bin-release.zip
cd rocketmq-all-4.9.4-bin-release

# 启动NameServer
start mqnamesrv.cmd

# 启动Broker
start mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true
```

#### Linux/Mac
```bash
# 下载RocketMQ
wget https://archive.apache.org/dist/rocketmq/4.9.4/rocketmq-all-4.9.4-bin-release.zip
unzip rocketmq-all-4.9.4-bin-release.zip
cd rocketmq-all-4.9.4-bin-release

# 启动NameServer
nohup sh mqnamesrv &

# 启动Broker
nohup sh mqbroker -n localhost:9876 autoCreateTopicEnable=true &
```

### 2. 启动应用
```bash
mvn spring-boot:run
```

### 3. 测试页面
访问 `http://localhost:8080/order-timeout-rocketmq-test.html` 进行测试：

#### 创建测试订单
1. 填写订单信息
2. 点击"创建测试订单"
3. 系统自动发送30分钟延迟消息

#### 手动发送延迟消息
1. 填写订单信息
2. 选择延迟时间（1分钟、5分钟、30分钟等）
3. 点击"发送RocketMQ测试延迟消息"

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
发送订单超时延迟消息，订单ID: 123, 订单号: ORD123456789, 延迟: 30分钟
订单超时延迟消息发送成功，订单ID: 123, 延迟级别: 12

接收到订单超时消息，订单ID: 123, 订单号: ORD123456789
订单超时取消成功，订单ID: 123, 订单号: ORD123456789
```

### RocketMQ控制台
访问 `http://localhost:8080` 查看：
- Topic状态：`order-timeout-topic`
- 消费者组：`order-timeout-consumer-group`
- 消息流量和消费情况

## ⚙️ 配置说明

### 延迟时间配置
RocketMQ延迟级别：
- 1级：1秒
- 2级：5秒
- 3级：10秒
- 4级：30秒
- 5级：1分钟
- 6级：2分钟
- 7级：3分钟
- 8级：4分钟
- 9级：5分钟
- 10级：6分钟
- 11级：7分钟
- 12级：8分钟
- 13级：9分钟
- 14级：10分钟
- 15级：20分钟
- 16级：30分钟
- 17级：1小时
- 18级：2小时

### RocketMQ配置
在 `application.yml` 中修改：
```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: order-timeout-producer-group
  consumer:
    group: order-timeout-consumer-group
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
- RocketMQ自动消息确认
- 支持消息重试机制
- 消息持久化存储

## 📈 性能优势

### 1. 相比RabbitMQ的优势
- ✅ 无需安装延迟插件
- ✅ 延迟级别更丰富
- ✅ 性能更高
- ✅ 配置更简单
- ✅ 更好的监控支持

### 2. 相比定时任务的优势
- ✅ 更精确的延迟时间
- ✅ 减少数据库查询压力
- ✅ 支持分布式部署
- ✅ 消息持久化
- ✅ 更好的扩展性

## 🎯 业务价值

- ✅ 自动释放超时订单占用的库存
- ✅ 提高库存周转率
- ✅ 改善用户体验
- ✅ 减少人工干预
- ✅ 保证数据一致性
- ✅ 支持高并发场景

## 🔧 故障排除

### 1. 延迟消息不生效
- 检查RocketMQ服务是否启动
- 检查Topic是否创建
- 查看RocketMQ日志

### 2. 消息重复处理
- 检查分布式锁配置
- 查看Redis连接状态
- 检查消息消费逻辑

### 3. 性能问题
- 调整RocketMQ配置
- 优化消息处理逻辑
- 监控队列长度

## 🆚 技术对比

| 特性 | 定时任务 | RabbitMQ | RocketMQ |
|------|----------|----------|----------|
| 精确度 | 低 | 高 | 高 |
| 性能 | 低 | 中 | 高 |
| 配置复杂度 | 低 | 高 | 中 |
| 插件依赖 | 无 | 需要 | 无 |
| 监控支持 | 差 | 中 | 好 |
| 分布式支持 | 差 | 好 | 好 |

现在你的订单系统具备了基于RocketMQ的延迟消息超时取消功能！🎉
