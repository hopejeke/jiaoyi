# DoorDash 配送集成完整方案（行业主流做法）

## 一、核心流程

### 1. 下单前/下单中：调用 DoorDash 报价

```java
// 在 calculateOrderPrice() 中
if ("DELIVERY".equalsIgnoreCase(orderType)) {
    // 调用 DoorDash 报价 API
    DoorDashQuoteResponse quote = doorDashService.quoteDelivery(
        pickupAddress, 
        dropoffAddress, 
        subtotal
    );
    
    // 保存 quoted_fee
    deliveryFeeQuoted = quote.getQuotedFee();
    
    // 用户支付的配送费 = quoted_fee + buffer（如 10%）
    deliveryFee = deliveryFeeQuoted.add(buffer);
}
```

**保存到订单：**
- `delivery_fee_quoted` = DoorDash 报价费用
- `delivery_fee_charged_to_user` = 用户实际支付的费用（quoted_fee + buffer）

### 2. 用户支付：按 quoted_fee（+buffer）收钱

用户支付时，配送费已经确定（基于 DoorDash 报价），直接按此金额收费。

### 3. 支付成功后：创建 DoorDash 配送订单

```java
// 在 handlePaymentSuccess() 中
if ("DELIVERY".equalsIgnoreCase(order.getOrderType())) {
    // 创建 DoorDash 配送订单
    DoorDashDeliveryResponse delivery = doorDashService.createDelivery(
        order, 
        pickupAddress, 
        dropoffAddress, 
        tip
    );
    
    // 保存 delivery_id
    order.setDeliveryId(delivery.getDeliveryId());
}
```

### 4. 履约中：Webhook 驱动状态流转

DoorDash 通过 Webhook 回调更新配送状态：
- `delivery.created` → 配送订单已创建
- `delivery.assigned` → 已分配骑手
- `delivery.picked_up` → 已取货
- `delivery.delivered` → 已送达
- `delivery.cancelled` → 已取消
- `delivery.failed` → 配送失败

### 5. 结算：DoorDash 出账单，对账后统一付款

**账单周期：** 每日/每周

**账单内容：**
- 成功配送单列表
- 每单配送费（`delivery_fee_billed`）
- 异常项：
  - `waiting_fee` - 等待费用（商户出餐慢）
  - `extra_fee` - 额外费用（用户改址等）
  - `cancellation_fee` - 取消费用

**对账流程：**
1. 从 DoorDash 获取账单
2. 与系统内订单记录对账
3. 统一打款给 DoorDash

**保存到订单：**
- `delivery_fee_billed` = DoorDash 账单中的实际费用

### 6. 差额处理：按规则归因

```java
// 计算差额
BigDecimal variance = deliveryFeeBilled.subtract(deliveryFeeQuoted);

// 按规则归因
Map<String, Object> varianceAttribution = new HashMap<>();

if (waitingFee > 0) {
    // 商户出餐慢 → waiting fee 计商户
    varianceAttribution.put("waitingFee", Map.of(
        "amount", waitingFee,
        "attribution", "MERCHANT"
    ));
}

if (extraFee > 0) {
    // 用户改址 → extra fee 计用户（或平台兜底）
    varianceAttribution.put("extraFee", Map.of(
        "amount", extraFee,
        "attribution", "USER" // 或 "PLATFORM"
    ));
}

if (platformSubsidy > 0) {
    // 平台补贴 → 计营销成本
    varianceAttribution.put("platformSubsidy", Map.of(
        "amount", platformSubsidy,
        "attribution", "PLATFORM"
    ));
}

// 保存到订单
order.setDeliveryFeeVariance(JSON.toJSONString(varianceAttribution));
```

## 二、数据库字段

### orders 表需要添加的字段

```sql
ALTER TABLE orders_X ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID';
ALTER TABLE orders_X ADD COLUMN delivery_fee_quoted DECIMAL(10,2) COMMENT 'DoorDash报价费用';
ALTER TABLE orders_X ADD COLUMN delivery_fee_charged_to_user DECIMAL(10,2) COMMENT '用户实际支付的配送费';
ALTER TABLE orders_X ADD COLUMN delivery_fee_billed DECIMAL(10,2) COMMENT 'DoorDash账单费用';
ALTER TABLE orders_X ADD COLUMN delivery_fee_variance TEXT COMMENT '配送费差额归因（JSON）';
ALTER TABLE orders_X ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）';
```

## 三、费用处理示例

### 场景 1：正常配送

```
DoorDash 报价：$6.99
用户支付：$7.69（$6.99 + 10% buffer）
DoorDash 账单：$6.99

差额：$0.00（无差额）
归因：无
```

### 场景 2：商户出餐慢

```
DoorDash 报价：$6.99
用户支付：$7.69
DoorDash 账单：$8.99（包含 $2.00 waiting fee）

差额：$2.00
归因：
  waitingFee: {
    amount: 2.00,
    attribution: "MERCHANT"  // 从商户结算中扣除
  }
```

### 场景 3：用户改址

```
DoorDash 报价：$6.99
用户支付：$7.69
DoorDash 账单：$8.49（包含 $1.50 extra fee）

差额：$1.50
归因：
  extraFee: {
    amount: 1.50,
    attribution: "USER"  // 或 "PLATFORM"（平台兜底）
  }
```

### 场景 4：平台补贴

```
DoorDash 报价：$6.99
用户支付：$5.99（平台补贴 $1.00）
DoorDash 账单：$6.99

差额：$1.00
归因：
  platformSubsidy: {
    amount: 1.00,
    attribution: "PLATFORM"  // 计营销成本
  }
```

## 四、关键代码位置

1. **报价获取**：`OrderService.calculateOrderPrice()` → `DoorDashService.quoteDelivery()`
2. **创建配送**：`PaymentService.handlePaymentSuccess()` → `DoorDashService.createDelivery()`
3. **状态更新**：`DoorDashWebhookController.handleWebhook()`
4. **账单对账**：需要新增 `DoorDashBillingService.reconcileBill()`
5. **差额归因**：需要新增 `DeliveryFeeVarianceService.attributeVariance()`

## 五、配置说明

### application.properties

```properties
# DoorDash API 配置
doordash.api.base-url=https://openapi.doordash.com
doordash.api.key=your_api_key
doordash.api.secret=your_api_secret

# DoorDash Webhook 配置
doordash.webhook.secret=your_webhook_secret

# 配送费 buffer 配置（应对费用波动）
doordash.delivery-fee.buffer-percentage=10  # 10% buffer
```

## 六、已实现的功能

### 1. 核心服务
- ✅ `DoorDashService` - DoorDash API 交互（报价、创建配送、查询状态、取消）
- ✅ `DoorDashWebhookController` - Webhook 回调处理
- ✅ `DoorDashBillingService` - 账单对账服务
- ✅ `DeliveryFeeVarianceService` - 差额归因服务

### 2. 订单流程集成
- ✅ `OrderService.calculateOrderPrice()` - 下单前调用 DoorDash 报价
- ✅ `PaymentService.handlePaymentSuccess()` - 支付成功后创建 DoorDash 配送订单
- ✅ 订单实体扩展 - 添加配送费相关字段

### 3. 数据库
- ✅ 添加 DoorDash 相关字段（delivery_id, delivery_fee_quoted 等）
- ✅ SQL 脚本：`add_doordash_fields.sql`

### 4. API 接口
- ✅ `GET /api/doordash/billing/reconcile` - 账单对账接口

## 七、配置说明

### application.properties

```properties
# DoorDash API 配置
doordash.api.base-url=https://openapi.doordash.com
doordash.api.key=your_api_key
doordash.api.secret=your_api_secret

# DoorDash Webhook 配置
doordash.webhook.secret=your_webhook_secret

# 配送费 buffer 配置（应对费用波动）
doordash.delivery-fee.buffer-percentage=10  # 10% buffer
```

## 八、优势总结

1. **用户体验好**：下单前就知道配送费，价格透明
2. **费用可控**：通过 buffer 应对费用波动
3. **责任清晰**：差额按规则归因，谁的责任谁承担
4. **对账简单**：账单制结算，避免实时分账复杂性
5. **异常处理完善**：支持等待费、改址费、取消费等异常情况

