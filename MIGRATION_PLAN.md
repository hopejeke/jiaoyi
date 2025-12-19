# Online Order V2 Backend 迁移计划

## 项目概述

将 `online-order-v2-backend` (Node.js + TypeScript + MongoDB) 的业务逻辑迁移到 `jiaoyi` 项目的技术架构 (Java + Spring Boot + MySQL + ShardingSphere + Redis + RocketMQ)。

## 技术架构对比

### 原项目 (online-order-v2-backend)
- **语言**: Node.js + TypeScript
- **数据库**: MongoDB
- **缓存**: Redis
- **消息队列**: 无（或自定义）
- **框架**: Restify

### 目标项目 (jiaoyi)
- **语言**: Java + Spring Boot
- **数据库**: MySQL + ShardingSphere（分库分表）
- **缓存**: Redis
- **消息队列**: RocketMQ
- **ORM**: MyBatis

## 核心业务模型映射

### 1. Merchant（餐馆）→ Store（店铺）
- MongoDB `Merchant` → MySQL `merchants` 表
- 分片键：`merchantId`
- 主要字段：
  - `merchantId` (String) → `merchant_id` (VARCHAR)
  - `name` (String) → `name` (VARCHAR)
  - `pickUpOpenTime` (Array) → JSON 字段或关联表
  - `deliveryOpenTime` (Array) → JSON 字段或关联表
  - `pickUpPrepareTime` (Object) → JSON 字段
  - `deliveryPrepareTime` (Object) → JSON 字段
  - `paymentAcceptance` (Array) → JSON 字段
  - `timeZone` (String) → `time_zone` (VARCHAR)

### 2. RestaurantService（餐馆服务）→ StoreService（店铺服务）
- MongoDB `RestaurantService` → MySQL `store_services` 表
- 分片键：`merchantId`
- 主要字段：
  - `merchantId` (String) → `merchant_id` (VARCHAR)
  - `serviceType` (String) → `service_type` (VARCHAR) - PICKUP/DELIVERY/SELF_DINE_IN
  - `paymentAcceptance` (Array) → JSON 字段
  - `prepareTime` (Object) → JSON 字段
  - `openTime` (Array) → JSON 字段或关联表
  - `activate` (Boolean) → `activate` (TINYINT)

### 3. MenuInfo（菜单信息）→ MenuItem（菜单项）
- MongoDB `MenuInfo` → MySQL `menu_items` 表
- 分片键：`merchantId`
- 主要字段：
  - `merchantId` (String) → `merchant_id` (VARCHAR)
  - `itemId` (Number) → `item_id` (BIGINT)
  - `imgInfo` (Object) → JSON 字段

### 4. Order（订单）→ Order（订单）
- MongoDB `Order` → MySQL `orders` 表
- 分片键：`merchantId` 或 `userId`（根据查询模式决定）
- 主要字段：
  - `merchantId` (String) → `merchant_id` (VARCHAR)
  - `userId` (String) → `user_id` (BIGINT)
  - `orderItems` (Array) → `order_items` 关联表
  - `orderPrice` (Object) → JSON 字段或拆分字段
  - `orderStatus` (Number) → `status` (INT)
  - `localOrderStatus` (Number) → `local_status` (INT)
  - `orderType` (String) → `order_type` (VARCHAR) - PICKUP/DELIVERY/SELF_DINE_IN

### 5. User（用户）→ User（用户）
- MongoDB `User` → MySQL `users` 表
- 分片键：`userId`（如果按用户查询多）或不分片
- 主要字段：
  - `email` (String) → `email` (VARCHAR)
  - `phone` (String) → `phone` (VARCHAR)
  - `name` (String) → `name` (VARCHAR)
  - `deliveryAddress` (Object) → JSON 字段或关联表
  - `paymentMethods` (Array) → `payment_methods` 关联表

## 数据库设计

### 分片策略
- **Merchant/Store**: 按 `merchantId` 分片（与现有 `store_products` 保持一致）
- **Order**: 按 `merchantId` 分片（订单主要按餐馆查询）
- **User**: 不分片或按 `userId` 分片（根据查询模式）

### 表结构设计原则
1. JSON 字段用于存储复杂结构（如营业时间、价格配置等）
2. 关联表用于存储数组数据（如订单项、支付方式等）
3. 使用乐观锁（version 字段）防止并发问题
4. 逻辑删除（is_delete 字段）替代物理删除

## 迁移步骤

### Phase 1: 核心实体和数据库设计
1. ✅ 分析 online-order-v2-backend 的核心业务模型
2. ✅ 设计 MySQL 数据库表结构
3. ✅ 创建数据库初始化脚本
4. ✅ 配置 ShardingSphere 分片规则

### Phase 2: 核心业务实现
1. ✅ Merchant 实体、Mapper、Service、Controller
2. ✅ StoreService 实体、Mapper、Service、Controller
3. ✅ MenuItem 实体、Mapper、Service、Controller
4. ✅ Order 和 OrderItem 实体、Mapper、Service、Controller
5. ✅ User 实体、Mapper、Service、Controller

### Phase 3: 缓存和消息队列集成
1. ⏳ Redis 缓存策略设计（可复用现有的缓存机制）
2. ⏳ RocketMQ 消息队列集成（可复用现有的 Outbox 模式）
3. ⏳ 订单状态变更消息发送

### Phase 4: 前端页面
1. ⏳ Customer 端（食客端）
2. ⏳ Dashboard 端（餐馆老板端）
3. ⏳ OMS 端（运营管理端）

## 注意事项

1. **数据迁移**: 需要编写数据迁移脚本，将 MongoDB 数据迁移到 MySQL
2. **API 兼容性**: 尽量保持 API 接口的兼容性，减少前端改动
3. **性能优化**: 利用 ShardingSphere 的分片能力，优化查询性能
4. **缓存策略**: 设计合理的缓存策略，减少数据库压力
5. **消息队列**: 使用 RocketMQ 实现异步处理和事件驱动

## 技术栈保持

- ✅ Spring Boot 3.x
- ✅ MyBatis
- ✅ ShardingSphere 5.x
- ✅ Redis
- ✅ RocketMQ
- ✅ MySQL 8.x

