# DoorDash 配送集成方案

## 一、整体架构

### 角色职责

| 角色 | 职责 |
|------|------|
| **我们系统** | 下单、支付、订单状态管理、对账 |
| **DoorDash** | 接单、骑手分配、配送、履约状态 |
| **用户** | 向我们平台付款 |
| **商户** | 从我们平台结算收入 |

**核心原则：DoorDash ≠ 支付方，只是履约服务商**

## 二、交互流程

### 1. 用户下单流程

```
用户下单
  ↓
计算价格（包含预估配送费）
  ↓
用户支付
  ↓
支付成功后 → 创建 DoorDash 配送订单
  ↓
DoorDash 返回 delivery_id 和实际配送费
  ↓
更新订单信息（保存 delivery_id 和实际费用）
  ↓
发送订单到 POS
```

### 2. DoorDash API 调用

#### 创建配送订单
```java
POST /v2/deliveries
{
  "external_delivery_id": "order_123",
  "pickup_address": {...},
  "dropoff_address": {...},
  "order_info": {
    "order_value": 5000,  // 订单金额（分）
    "currency": "USD"
  },
  "tip": 300  // 小费（分，可选）
}
```

#### DoorDash 响应
```json
{
  "id": "dd_delivery_987",
  "status": "CREATED",
  "fee": {
    "total": 699  // 实际配送费（分）
  }
}
```

### 3. DoorDash Webhook 回调

DoorDash 会回调我们的接口，通知配送状态变化：

- `delivery.created` - 配送订单已创建
- `delivery.assigned` - 已分配骑手
- `delivery.picked_up` - 已取货
- `delivery.delivered` - 已送达
- `delivery.cancelled` - 已取消
- `delivery.failed` - 配送失败

## 三、费用计算与处理

### 费用结构

```java
priceInfo: {
    deliveryFeeToThird: {      // 给 DoorDash 的实际费用
        name: "DeliveryFee",
        value: 6.99
    },
    deliveryFeeToRes: {         // 给餐厅的费用（可能是负数，用于补偿差价）
        name: "DeliveryFee",
        value: -1.99
    },
    tipsToThird: 3.00,          // 给 DoorDash 的小费
    deliveryServiceFee: 0.00    // 服务费
}
```

### 费用差异处理策略

#### 场景 1：用户支付的配送费 < DoorDash 实际费用

**示例：**
- 用户支付：$5.00（我们预估的配送费）
- DoorDash 实际费用：$6.99
- 差价：$1.99

**处理方案：**
```java
deliveryFeeToThird = 6.99  // 给 DoorDash 的实际费用
deliveryFeeToRes = -1.99  // 餐厅需要补贴的差价（负数表示餐厅承担）
```

**资金流向：**
- 用户支付总额包含 $5.00 配送费
- 我们给 DoorDash 支付 $6.99
- 差价 $1.99 从商户结算中扣除

#### 场景 2：用户支付的配送费 > DoorDash 实际费用

**示例：**
- 用户支付：$8.00
- DoorDash 实际费用：$6.99
- 差价：$1.01

**处理方案：**
```java
deliveryFeeToThird = 6.99  // 给 DoorDash 的实际费用
deliveryFeeToRes = 1.01    // 餐厅获得的额外收入（正数）
```

#### 场景 3：配送失败/取消

**处理方案：**
- 如果 DoorDash 未取货：不收取配送费
- 如果已取货但失败：DoorDash 可能部分收费或全免（看协议）
- 用户取消：根据取消时间决定是否退配送费

## 四、实现细节

### 1. 订单创建时（支付前）

```java
// 计算预估配送费（给用户展示）
BigDecimal estimatedDeliveryFee = feeCalculationService.calculateDeliveryFee(order, subtotal);

// 用户支付时，先按预估费用收费
orderPrice.setDeliveryFee(estimatedDeliveryFee);
```

### 2. 支付成功后

```java
// 1. 创建 DoorDash 配送订单
DoorDashService.DoorDashDeliveryResponse deliveryResponse = 
    doorDashService.createDelivery(order, pickupAddress, dropoffAddress, tip);

// 2. 获取实际配送费
BigDecimal actualDeliveryFee = deliveryResponse.getActualFee();

// 3. 计算费用差异
BigDecimal estimatedFee = orderPrice.getDeliveryFee();
BigDecimal feeDifference = actualDeliveryFee.subtract(estimatedFee);

// 4. 构建 priceInfo（发送给 POS）
Map<String, Object> priceInfo = new HashMap<>();
priceInfo.put("deliveryFeeToThird", Map.of(
    "name", "DeliveryFee",
    "value", actualDeliveryFee
));
priceInfo.put("deliveryFeeToRes", Map.of(
    "name", "DeliveryFee",
    "value", feeDifference.negate()  // 负数表示餐厅承担差价
));

// 5. 保存 delivery_id 和实际费用到订单
order.setDeliveryId(deliveryResponse.getDeliveryId());
order.setAdditionalData(JSON.toJSONString(Map.of(
    "deliveryInfo", Map.of(
        "platform", "DOORDASH",
        "deliveryId", deliveryResponse.getDeliveryId()
    ),
    "priceInfo", priceInfo
)));
```

### 3. 费用结算（周期性对账）

DoorDash 会定期（每日/每周）提供账单：

```
账单内容：
- 成功配送单列表
- 每单配送费
- 失败单补偿/罚款
- 总计金额
```

**对账流程：**
1. 从 DoorDash 获取账单
2. 与系统内订单记录对账
3. 统一打款给 DoorDash

## 五、配置说明

### application.properties

```properties
# DoorDash API 配置
doordash.api.base-url=https://openapi.doordash.com
doordash.api.key=your_api_key
doordash.api.secret=your_api_secret

# DoorDash Webhook 配置
doordash.webhook.secret=your_webhook_secret
```

## 六、数据库字段

### orders 表需要添加字段

```sql
ALTER TABLE orders_X ADD COLUMN delivery_id VARCHAR(100) COMMENT 'DoorDash配送ID';
ALTER TABLE orders_X ADD COLUMN additional_data TEXT COMMENT '额外数据（JSON，包含deliveryInfo和priceInfo）';
```

## 七、关键点总结

1. **平台统一收款**：用户向我们支付，DoorDash 不参与支付链路
2. **API 创建配送**：支付成功后调用 DoorDash API 创建配送订单
3. **Webhook 驱动状态**：DoorDash 通过 Webhook 回调更新配送状态
4. **账单制结算**：DoorDash 周期性出账单，我们统一打款（避免实时分账复杂性）
5. **费用差异处理**：通过 `deliveryFeeToRes` 负数补偿差价，从商户结算中扣除
6. **幂等性保证**：所有 Webhook 回调都要做幂等处理

## 八、异常处理

### 配送失败
- DoorDash 未取货：取消配送，不收费
- 已取货失败：根据协议处理，可能需要部分退款

### API 调用失败
- 重试机制：最多重试 3 次
- 降级方案：如果 DoorDash API 不可用，可以：
  - 记录错误日志
  - 通知运营人员
  - 或者使用备用配送服务

### 费用差异过大
- 如果实际费用与预估费用差异超过阈值（如 50%），需要：
  - 记录告警
  - 通知运营人员
  - 可能需要调整预估算法












