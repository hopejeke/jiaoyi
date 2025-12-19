# 配送规则检查流程

## 一、流程设计

按照"商家设置基础规则（距离、时段）+ DoorDash 做最终验证和报价"的方案实现。

### 完整流程

```
用户输入配送地址
    ↓
【步骤1】商家基础规则检查（第一层筛选）
    ├─ 检查配送时段
    │   ├─ 不在配送时段内？ → 拒绝，提示："当前时段不在配送时间内"
    │   └─ 在配送时段内？ → 继续
    │
    ├─ 检查配送距离或邮编区域
    │   ├─ 按邮编区域（ZONE_RATE）
    │   │   ├─ 邮编不在配置区域？ → 拒绝，提示："该邮编不在配送范围内"
    │   │   └─ 邮编在配置区域？ → 继续
    │   │
    │   └─ 按距离（VARIABLE_RATE）
    │       ├─ 距离超过最大配送距离？ → 拒绝，提示："配送距离超过最大配送范围"
    │       └─ 距离在范围内？ → 继续
    │
    └─ 通过商家规则 → 继续
    ↓
【步骤2】判断是否使用 DoorDash
    ├─ 商户未配置 DoorDash？ → 使用本地计算配送费
    │   └─ 调用 FeeCalculationService.calculateDeliveryFee()
    │
    └─ 商户配置了 DoorDash？ → 调用 DoorDash 报价 API
        ↓
【步骤3】DoorDash 报价验证（第二层验证）
    ├─ DoorDash 拒绝（地址超出范围/无法配送）？
    │   └─ 返回错误："DoorDash 无法配送此地址"
    │
    └─ DoorDash 接受并返回报价？
        ├─ 保存 quoted_fee
        ├─ 计算用户支付费用 = quoted_fee + buffer（10%）
        └─ 返回配送费给前端
    ↓
【步骤4】用户确认并下单
    ↓
【步骤5】支付成功后创建 DoorDash 配送订单
```

## 二、实现细节

### 1. 商家基础规则检查

**服务类：** `DeliveryRuleService`

**检查项：**
- ✅ 配送时段检查（`delivery_time_slots`）
- ✅ 邮编区域检查（`delivery_zone_rate`）
- ✅ 配送距离检查（`delivery_maximum_distance`）

**调用位置：** `OrderService.calculateOrderPrice()`

### 2. 配送时段配置

**字段：** `MerchantFeeConfig.deliveryTimeSlots` (JSON)

**配置格式：**

```json
// 方式1：每日统一时段
{
  "daily": {
    "start": "09:00",
    "end": "22:00"
  }
}

// 方式2：按星期分别配置
{
  "monday": {"start": "09:00", "end": "22:00"},
  "tuesday": {"start": "09:00", "end": "22:00"},
  "wednesday": {"start": "09:00", "end": "22:00"},
  "thursday": {"start": "09:00", "end": "22:00"},
  "friday": {"start": "09:00", "end": "23:00"},
  "saturday": {"start": "10:00", "end": "23:00"},
  "sunday": {"start": "10:00", "end": "22:00"}
}
```

**规则：**
- 如果字段为空或 NULL，表示全天可配送
- 如果配置了时段，订单时间必须在配送时段内

### 3. 配送距离检查

**检查逻辑：**
- 如果配置了 `deliveryMaximumDistance`，计算商户到客户的距离
- 如果距离超过最大配送距离，拒绝配送
- 使用 `GoogleMapsService.calculateDistance()` 计算距离

### 4. 邮编区域检查

**检查逻辑：**
- 如果配置了 `deliveryZoneRate`（ZONE_RATE 类型），检查邮编是否在配置的区域列表中
- 如果邮编不在任何区域，拒绝配送

## 三、代码修改

### 1. 新增文件

- `DeliveryRuleService.java` - 配送规则检查服务
- `add_delivery_time_slots.sql` - 数据库迁移脚本

### 2. 修改文件

- `MerchantFeeConfig.java` - 添加 `deliveryTimeSlots` 字段
- `OrderService.java` - 修改 `calculateOrderPrice()` 方法，先检查商家规则，再调用 DoorDash API
- `DatabaseInitializer.java` - 添加 `delivery_time_slots` 字段到建表语句
- `MerchantFeeConfigMapper.xml` - 添加 `delivery_time_slots` 字段映射

## 四、优势

1. **性能优化**：商家规则先筛选，减少不必要的 DoorDash API 调用
2. **成本控制**：降低 DoorDash API 调用费用
3. **用户体验**：快速反馈，错误信息明确
4. **双重保障**：商家规则 + DoorDash 验证，确保地址可配送
5. **灵活配置**：商家可以灵活设置配送时段和范围

## 五、错误提示

| 检查项 | 错误提示 |
|--------|---------|
| 配送时段 | "当前时段不在配送时间内，配送时间: 09:00-22:00" |
| 邮编区域 | "该邮编不在配送范围内" |
| 配送距离 | "配送距离超过最大配送范围（5 英里）" |
| DoorDash 拒绝 | "DoorDash 无法配送此地址: [具体原因]" |

## 六、数据库迁移

执行以下 SQL 脚本添加配送时段字段：

```sql
-- 执行 add_delivery_time_slots.sql
ALTER TABLE merchant_fee_config_0 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';
ALTER TABLE merchant_fee_config_1 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';
ALTER TABLE merchant_fee_config_2 ADD COLUMN delivery_time_slots TEXT COMMENT '配送时段配置（JSON格式）';
```

## 七、使用示例

### 商家配置配送时段

```json
{
  "daily": {
    "start": "09:00",
    "end": "22:00"
  }
}
```

### 商家配置配送距离

```java
MerchantFeeConfig config = new MerchantFeeConfig();
config.setMerchantId("merchant_001");
config.setDeliveryMaximumDistance(new BigDecimal("5.0")); // 5 英里
config.setMerchantLatitude(new BigDecimal("40.7128"));
config.setMerchantLongitude(new BigDecimal("-74.0060"));
```

### 商家配置邮编区域

```json
{
  "deliveryZoneRate": [
    {"zipcodes": ["10001", "10002"], "price": 5.0},
    {"zipcodes": ["10003", "10004"], "price": 8.0}
  ]
}
```


