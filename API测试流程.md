# 电商订单系统 API 测试流程

## 📋 完整订单流程

### 1. 提交订单（锁定库存）

**接口**: `POST /orders`

**请求体**:
```json
{
  "userId": 94,
  "receiverName": "张三",
  "receiverPhone": "13800138000",
  "receiverAddress": "北京市朝阳区xxx街道",
  "remark": "请尽快发货",
  "orderItems": [
    {
      "productId": 2002,
      "productName": "质朴的钢鞋子",
      "productImage": "https://example.com/shoe.jpg",
      "unitPrice": 634.75,
      "quantity": 10
    }
  ]
}
```

**响应**:
```json
{
  "success": true,
  "message": "订单创建成功",
  "data": {
    "id": 1,
    "orderNo": "ORD1703123456789ABCDEFGH",
    "userId": 94,
    "status": "PENDING",
    "totalAmount": 6347.50,
    "receiverName": "张三",
    "receiverPhone": "13800138000",
    "receiverAddress": "北京市朝阳区xxx街道",
    "remark": "请尽快发货",
    "createTime": "2024-01-01 10:00:00",
    "orderItems": [...]
  }
}
```

**库存变化**:
- 锁定库存：`locked_stock = locked_stock + 10`
- 当前库存：`current_stock` 不变
- 可用库存：`current_stock - locked_stock` 减少10

---

### 2. 支付订单（调用第三方支付）

**接口**: `POST /orders/{orderId}/pay`

**请求**: `POST /orders/1/pay`
```json
{
  "paymentMethod": "ALIPAY",
  "amount": 634750,
  "channel": "WEB",
  "clientIp": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "remark": "支付宝支付"
}
```

**响应**:
```json
{
  "success": true,
  "message": "支付处理成功",
  "data": {
    "paymentNo": "PAY1703123456789ABCDEFGH",
    "status": "SUCCESS",
    "paymentMethod": "ALIPAY",
    "amount": 634750,
    "payTime": "2024-01-01 10:05:00",
    "thirdPartyTradeNo": "TP1703123456789",
    "payUrl": "https://pay.example.com/success?orderId=1",
    "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
    "remark": "模拟支付处理"
  }
}
```

**库存变化**:
- 当前库存：`current_stock = current_stock - 10`
- 锁定库存：`locked_stock = locked_stock - 10`
- 可用库存：`current_stock - locked_stock` 减少10

---

### 3. 取消订单（解锁库存）

**接口**: `PUT /orders/{orderId}/cancel`

**请求**: `PUT /orders/1/cancel`

**响应**:
```json
{
  "success": true,
  "message": "订单取消成功",
  "data": null
}
```

**库存变化**:
- 锁定库存：`locked_stock = locked_stock - 10`
- 当前库存：`current_stock` 不变
- 可用库存：`current_stock - locked_stock` 增加10

---

## 🔍 库存记录表变化

### 锁定库存时
```sql
INSERT INTO inventory_transactions (
    product_id, order_id, transaction_type, quantity,
    before_stock, after_stock, before_locked, after_locked, remark
) VALUES (
    2002, NULL, 'LOCK', 10,
    50, 50, 0, 10, '下单锁定库存'
);
```

### 支付成功时
```sql
INSERT INTO inventory_transactions (
    product_id, order_id, transaction_type, quantity,
    before_stock, after_stock, before_locked, after_locked, remark
) VALUES (
    2002, 1, 'OUT', 10,
    50, 40, 10, 0, '支付成功扣减库存'
);
```

### 取消订单时
```sql
INSERT INTO inventory_transactions (
    product_id, order_id, transaction_type, quantity,
    before_stock, after_stock, before_locked, after_locked, remark
) VALUES (
    2002, 1, 'UNLOCK', 10,
    50, 50, 10, 0, '订单取消解锁库存'
);
```

---

## 🚀 快速测试

```bash
# 启动应用
test-payment.bat

# 或者手动启动
mvn clean compile -U -s settings.xml
mvn spring-boot:run -s settings.xml
```

**API测试**：
1. 提交订单：`POST /orders` （锁定库存）
2. 支付订单：`POST /orders/1/pay` （调用第三方支付）
3. 取消订单：`PUT /orders/1/cancel` （解锁库存）

**支付方式**：
- `ALIPAY`: 支付宝支付
- `WECHAT`: 微信支付  
- `BANK`: 银行卡支付

**支付状态**：
- `PENDING`: 待支付
- `SUCCESS`: 支付成功
- `FAILED`: 支付失败

---

## 📊 订单状态流转

```
PENDING (待支付) → PAID (已支付) → SHIPPED (已发货) → DELIVERED (已送达)
     ↓
CANCELLED (已取消)
```

- **PENDING → PAID**: 支付成功，扣减库存
- **PENDING → CANCELLED**: 取消订单，解锁库存
- **其他状态变更**: 不影响库存
