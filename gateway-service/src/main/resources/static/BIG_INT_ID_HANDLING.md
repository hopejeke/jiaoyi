# 大整数 ID 处理指南

## 问题说明

JavaScript 的 `Number` 类型只能安全表示 ±2^53 的整数（-9007199254740991 到 9007199254740991）。

雪花算法生成的 ID（如 `1207322489043026000`）超过这个范围，会导致精度丢失：
- `1207322489043026000` 会被转换为 `1207322489043026000`（丢失精度）

## 解决方案

### 后端配置

所有服务（order-service、product-service）都已配置：
1. **序列化**：所有 `Long` 类型自动序列化为字符串（输出到前端）
2. **反序列化**：支持从字符串反序列化为 `Long`（接收前端数据）

### 前端处理

#### 1. 接收后端数据
后端返回的所有 ID 字段（`id`、`orderId`、`productId`、`merchantId`、`storeId`、`userId` 等）都是字符串类型，直接使用即可：

```javascript
// ✅ 正确：直接使用字符串
const orderId = order.id; // 已经是字符串
const productId = product.id; // 已经是字符串

// ❌ 错误：不要转换为数字
const orderId = parseInt(order.id); // 会导致精度丢失！
const productId = Number(product.id); // 会导致精度丢失！
```

#### 2. 发送到后端
发送到后端时，ID 字段保持字符串类型：

```javascript
// ✅ 正确：保持字符串类型
fetch('/api/orders', {
    method: 'POST',
    body: JSON.stringify({
        orderId: String(orderId), // 确保是字符串
        productId: String(productId), // 确保是字符串
        userId: userId // 如果已经是字符串，直接使用
    })
});

// ❌ 错误：不要转换为数字
fetch('/api/orders', {
    method: 'POST',
    body: JSON.stringify({
        orderId: parseInt(orderId), // 会导致精度丢失！
        productId: Number(productId) // 会导致精度丢失！
    })
});
```

#### 3. URL 参数
从 URL 参数获取 ID 时，已经是字符串：

```javascript
// ✅ 正确：URL 参数本身就是字符串
const urlParams = new URLSearchParams(window.location.search);
const orderId = urlParams.get('orderId'); // 已经是字符串

// 使用工具函数
const orderId = LongIdUtils.getUrlParamAsId('orderId');
```

#### 4. 使用工具函数
已创建 `js/long-id-utils.js` 工具函数：

```javascript
// 引入工具函数（在 HTML 中）
<script src="js/long-id-utils.js"></script>

// 使用工具函数
const orderId = LongIdUtils.toIdString(order.id);
const productId = LongIdUtils.getIdAsString(product, 'id');
const orderId = LongIdUtils.getUrlParamAsId('orderId');
const safeOrder = LongIdUtils.ensureIdsAsStrings(order);
const safeProducts = LongIdUtils.ensureArrayIdsAsStrings(products);
```

## 涉及的 ID 字段

以下字段都是 `Long` 类型，需要作为字符串处理：
- `id` - 通用 ID
- `orderId` - 订单 ID
- `productId` - 商品 ID
- `merchantId` - 商户 ID（注意：这个字段是 String 类型，不需要转换）
- `storeId` - 店铺 ID
- `userId` - 用户 ID
- `couponId` - 优惠券 ID
- `skuId` - SKU ID
- `inventoryId` - 库存 ID
- 其他所有 `Long` 类型的 ID 字段

## 检查清单

在编写前端代码时，确保：
- [ ] 不使用 `parseInt()` 转换任何 ID
- [ ] 不使用 `Number()` 转换任何 ID
- [ ] 所有 ID 比较使用字符串比较：`id1 === id2`
- [ ] 所有 ID 传递保持字符串类型
- [ ] URL 参数中的 ID 直接使用，不转换
- [ ] 从后端接收的 ID 直接使用，不转换

## 示例

### 正确示例

```javascript
// 接收后端数据
const order = await response.json();
const orderId = order.data.id; // 已经是字符串

// 发送到后端
await fetch(`/api/orders/${orderId}`, {
    method: 'GET'
});

// 在 URL 中使用
window.location.href = `orders.html?orderId=${orderId}`;

// 从 URL 获取
const urlParams = new URLSearchParams(window.location.search);
const orderId = urlParams.get('orderId'); // 已经是字符串

// 比较 ID
if (order1.id === order2.id) {
    // 正确：字符串比较
}
```

### 错误示例

```javascript
// ❌ 错误：转换为数字会导致精度丢失
const orderId = parseInt(order.id);
const productId = Number(product.id);

// ❌ 错误：数字比较可能不准确
if (parseInt(order1.id) === parseInt(order2.id)) {
    // 如果 ID 超过安全范围，比较可能不准确
}
```

## 后端支持

后端已配置支持：
1. 接收字符串类型的 ID，自动转换为 `Long`
2. 输出 `Long` 类型的 ID 为字符串

因此，前端只需要确保：
- 接收时：直接使用字符串
- 发送时：保持字符串类型

无需任何转换！






