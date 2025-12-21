# 项目功能清单与开发路线图

## 当前项目状态

### ✅ 已完成功能

1. **配送费计算与锁价**
   - ✅ 下单页调用 DoorDash 算费 + ETA
   - ✅ 保存报价凭证（quote_id）和过期时间（5分钟）
   - ✅ 报价过期检查和重新报价机制
   - ✅ 费用差异处理（平台承担）

2. **DoorDash API 集成**
   - ✅ `quoteDelivery` - 获取报价
   - ✅ `acceptQuote` - 接受报价锁定价格
   - ✅ `createDelivery` - 创建配送订单
   - ✅ `getDeliveryStatus` - 查询配送状态
   - ✅ `cancelDelivery` - 取消配送

3. **Webhook 回调处理**
   - ✅ `DoorDashWebhookController` - 处理 DoorDash 回调
   - ✅ 支持事件：created, assigned, picked_up, delivered, cancelled, failed
   - ✅ Mock Webhook 测试接口

4. **订单状态管理**
   - ✅ 订单状态枚举：PENDING, PAID, WAITING_ACCEPT, PREPARING, DELIVERING, COMPLETED, CANCELLED, REFUNDED
   - ✅ 状态流转逻辑

5. **数据库支持**
   - ✅ DoorDash 相关字段（delivery_id, delivery_fee_quoted, delivery_fee_charged_to_user 等）
   - ✅ 报价时间和 quote_id 字段

---

## 必做 MVP（能跑通一单）

### 1. ✅ 配送费计算与锁价
- ✅ 下单页：调 DoorDash 算费 + ETA
- ✅ 保存"报价凭证 + 过期时间"
- ⚠️ **待完善**：提交时校验过期就重算并让用户确认差价（前端需要实现）

### 2. ⚠️ 支付闭环
- ✅ 创建订单 → 支付 → 支付成功才进入制作/派单
- ⚠️ **待完善**：支付回调幂等（需要检查是否完全实现）

### 3. ⚠️ 派单
- ✅ 支付成功后：用锁价创建配送（accept/create delivery）
- ✅ 保存：delivery_id、tracking_url、配送费
- ⚠️ **待完善**：保存 DoorDash 返回的参考号

### 4. ⚠️ Webhook 回调 + 状态机
- ✅ 已实现基本回调处理
- ⚠️ **待完善**：
  - 幂等性检查（event_id 去重）
  - 状态机验证（确保状态流转合法）
  - 异常处理（回调失败重试）

### 5. ❌ 用户端订单详情
- ❌ 展示：制作中/配送中/预计送达时间
- ❌ tracking 链接（或内嵌）

---

## 上线必补（不然很容易翻车）

### 1. ❌ 取消/退款策略
- ❌ 派单前可取消全退
- ❌ 派单后取消：按 DoorDash 是否支持取消、是否产生费用，明确规则
- ❌ 退款要异步、可重试、可查状态

### 2. ❌ 异常补偿与幂等
- ❌ "支付成功但派单失败"：自动重试 + 超过次数转人工/自动退款
- ❌ webhook 丢失：定时任务兜底拉状态/超时关单
- ❌ 所有外部调用：幂等键、去重表（event_id / delivery_id）

### 3. ❌ 商家/后厨最小操作
- ❌ 订单列表（只显示已支付的可做单）
- ❌ "接单/开始制作/出餐完成(READY)" 按钮

---

## 简历加分（做到就很好讲）

### 1. ❌ 费用与对账
- ⚠️ 订单里存：菜品金额、平台抽成、用户配送费（已有）、DoorDash 最终成本（待完善）
- ❌ 简单报表：配送费收了多少、成本多少、毛利多少（后台页面）

### 2. ❌ 通知
- ❌ 用户：下单成功、骑手取货、送达（短信/站内/Push）
- ❌ 商家：新单提醒

### 3. ❌ 可观测性
- ❌ traceId 打通：下单→支付→派单→回调
- ❌ 关键指标：派单成功率、回调延迟、退款成功率、失败重试次数、P95/P99

---

## 开发优先级

### Phase 1: MVP 完善（1-2周）
1. **支付回调幂等性检查**（1天）
2. **Webhook 幂等性 + 状态机验证**（2天）
3. **用户端订单详情页**（3天）
4. **前端：报价过期重算并确认差价**（2天）

### Phase 2: 上线必补（2-3周）
1. **取消/退款策略**（3天）
2. **异常补偿与幂等**（5天）
3. **商家/后厨操作页面**（3天）

### Phase 3: 简历加分（2-3周）
1. **费用与对账报表**（3天）
2. **通知系统**（3天）
3. **可观测性**（5天）

---

## 详细任务清单

### MVP 完善

#### 1. 支付回调幂等性检查
- [ ] 检查 `PaymentService.handlePaymentSuccess()` 的幂等性实现
- [ ] 添加支付记录去重表（payment_id, third_party_trade_no）
- [ ] 测试重复回调场景

#### 2. Webhook 幂等性 + 状态机
- [ ] 添加 webhook 事件去重表（event_id, delivery_id, event_type, processed_at）
- [ ] 实现状态机验证（确保状态流转合法）
- [ ] 添加回调失败重试机制

#### 3. 用户端订单详情页
- [ ] 创建订单详情页面（前端）
- [ ] 展示订单状态、配送状态、预计送达时间
- [ ] 集成 DoorDash tracking 链接或内嵌地图

#### 4. 前端：报价过期处理
- [ ] 前端检查报价是否过期
- [ ] 如果过期，重新获取报价
- [ ] 显示费用差异，让用户确认

### 上线必补

#### 1. 取消/退款策略
- [ ] 实现订单取消接口
- [ ] 实现 DoorDash 配送取消逻辑
- [ ] 实现退款服务（异步、可重试）
- [ ] 退款状态查询接口

#### 2. 异常补偿与幂等
- [ ] 派单失败自动重试机制
- [ ] 重试超过次数转人工/自动退款
- [ ] 定时任务：拉取 DoorDash 状态（兜底 webhook）
- [ ] 超时关单逻辑
- [ ] 外部调用幂等键实现

#### 3. 商家/后厨操作
- [ ] 商家订单列表页面（只显示已支付）
- [ ] 接单按钮（WAITING_ACCEPT → PREPARING）
- [ ] 开始制作按钮（PREPARING）
- [ ] 出餐完成按钮（PREPARING → READY，等待骑手取货）

### 简历加分

#### 1. 费用与对账
- [ ] 完善订单费用字段（菜品金额、平台抽成、DoorDash 成本）
- [ ] 创建费用报表页面
- [ ] 实现报表查询接口（配送费收入/成本/毛利）

#### 2. 通知系统
- [ ] 集成短信/站内/Push 通知服务
- [ ] 用户通知：下单成功、骑手取货、送达
- [ ] 商家通知：新单提醒

#### 3. 可观测性
- [ ] 实现 traceId 传递（下单→支付→派单→回调）
- [ ] 添加关键指标监控
- [ ] 实现指标收集和展示

---

## 技术实现建议

### 幂等性实现
```java
// 1. 支付回调幂等
CREATE TABLE payment_callback_log (
    id BIGINT PRIMARY KEY,
    payment_id BIGINT,
    third_party_trade_no VARCHAR(100),
    callback_data TEXT,
    processed_at DATETIME,
    UNIQUE KEY uk_payment_trade_no (payment_id, third_party_trade_no)
);

// 2. Webhook 事件去重
CREATE TABLE doordash_webhook_log (
    id BIGINT PRIMARY KEY,
    event_id VARCHAR(100) UNIQUE,
    delivery_id VARCHAR(100),
    event_type VARCHAR(50),
    payload TEXT,
    processed_at DATETIME,
    INDEX idx_delivery_id (delivery_id)
);
```

### 状态机验证
```java
// 订单状态流转规则
PENDING → PAID → WAITING_ACCEPT → PREPARING → DELIVERING → COMPLETED
PENDING → PAID → PREPARING → DELIVERING → COMPLETED
任何状态 → CANCELLED（可取消）
任何状态 → REFUNDED（可退款）
```

### 异常补偿
```java
// 派单失败重试
@Scheduled(fixedDelay = 60000) // 每分钟检查一次
public void retryFailedDeliveryCreation() {
    // 查询支付成功但未创建配送的订单
    // 重试创建配送（最多3次）
    // 超过3次转人工处理或自动退款
}
```

---

## 下一步行动

1. **立即开始**：支付回调幂等性检查（最关键）
2. **本周完成**：Webhook 幂等性 + 状态机验证
3. **下周完成**：用户端订单详情页
4. **逐步推进**：按照优先级完成其他功能





