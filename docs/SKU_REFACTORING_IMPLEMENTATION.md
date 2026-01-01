# SKU级别销售改造实施计划

## 🎯 改造目标

将当前的商品级别销售改为SKU级别销售，确保：
- 订单项记录SKU信息
- 库存扣减基于SKU
- 价格计算优先使用SKU价格
- 向后兼容（支持没有SKU的商品）

---

## 📋 改造步骤（按顺序执行）

### Phase 1: 数据库和实体类改造 ✅ 进行中

#### 1.1 数据库表结构
- ✅ 修改 `order_items` 表，添加 `sku_id`、`sku_name`、`sku_attributes` 字段
- ✅ 添加 `idx_sku_id` 索引
- ✅ 在 `DatabaseInitializer` 中添加字段更新逻辑（兼容旧表）

#### 1.2 实体类改造
- [ ] `OrderItem` 实体添加 `skuId`、`skuName`、`skuAttributes` 字段
- [ ] `CreateOrderRequest.OrderItemRequest` 添加 `skuId` 字段（可选，兼容没有SKU的商品）

#### 1.3 Mapper改造
- [ ] `OrderItemMapper.xml` 添加 `sku_id`、`sku_name`、`sku_attributes` 映射
- [ ] INSERT 和 SELECT 语句包含新字段

---

### Phase 2: 库存服务改造

#### 2.1 InventoryService 方法改造
- [ ] `checkAndLockStock(Long productId, Integer quantity)` 
  → 改为 `checkAndLockStock(Long productId, Long skuId, Integer quantity)`
- [ ] `deductStock(Long productId, Integer quantity, Long orderId)`
  → 改为 `deductStock(Long productId, Long skuId, Integer quantity, Long orderId)`
- [ ] `unlockStock(Long productId, Integer quantity, Long orderId)`
  → 改为 `unlockStock(Long productId, Long skuId, Integer quantity, Long orderId)`
- [ ] 批量方法也要改造

#### 2.2 InventoryMapper 添加SKU相关方法
- [ ] `selectBySkuId(Long skuId)` - 根据SKU ID查询库存
- [ ] `lockStockBySkuId(Long skuId, Integer quantity)` - 锁定SKU库存
- [ ] `deductStockBySkuId(Long skuId, Integer quantity)` - 扣减SKU库存
- [ ] `unlockStockBySkuId(Long skuId, Integer quantity)` - 解锁SKU库存

#### 2.3 库存扣减逻辑
```java
// 优先使用 skuId
if (skuId != null) {
    // 使用 SKU 级别库存
    inventory = inventoryMapper.selectBySkuId(skuId);
    inventoryMapper.lockStockBySkuId(skuId, quantity);
} else {
    // 兼容：商品级别库存
    inventory = inventoryMapper.selectByProductId(productId);
    inventoryMapper.lockStock(productId, quantity);
}
```

---

### Phase 3: 订单服务改造

#### 3.1 订单创建流程改造
- [ ] 接收订单请求时，验证SKU是否存在（如果提供了skuId）
- [ ] 查询SKU信息（价格、属性、名称）
- [ ] 使用SKU信息创建订单项
- [ ] 使用SKU ID锁定库存

#### 3.2 价格计算改造
- [ ] `calculateOrderPrice` 方法：
  - 如果提供了 `skuId`，优先使用 `ProductSku.skuPrice`
  - 如果SKU没有价格，使用商品价格 `StoreProduct.unitPrice`
- [ ] `createOrder` 方法：
  - 查询SKU价格
  - 保存SKU信息到订单项

#### 3.3 SKU验证逻辑
```java
// 如果提供了 skuId，验证SKU是否存在且属于该商品
if (itemRequest.getSkuId() != null) {
    ProductSku sku = productSkuService.getSkuById(itemRequest.getSkuId());
    if (sku == null || !sku.getProductId().equals(itemRequest.getProductId())) {
        throw new BusinessException("SKU不存在或不属于该商品");
    }
    // 使用SKU价格
    unitPrice = sku.getSkuPrice() != null ? sku.getSkuPrice() : product.getUnitPrice();
} else {
    // 兼容：没有SKU的商品，使用商品价格
    unitPrice = product.getUnitPrice();
}
```

---

### Phase 4: 商品服务改造

#### 4.1 商品查询接口改造
- [ ] `getProductByMerchantIdAndId` 返回SKU列表
- [ ] 每个SKU包含：id、skuCode、skuName、skuPrice、skuAttributes、currentStock

#### 4.2 响应格式
```json
{
  "id": 123,
  "productName": "T恤",
  "unitPrice": 99.00,
  "skus": [
    {
      "id": 456,
      "skuCode": "TEE-RED-L",
      "skuName": "红色 L码",
      "skuPrice": 99.00,
      "skuAttributes": {"color": "红色", "size": "L"},
      "currentStock": 10,
      "lockedStock": 0
    }
  ]
}
```

---

## 🔄 兼容性处理

### 向后兼容策略

1. **skuId 字段允许为 NULL**
   - 旧订单项没有 skuId，仍然可以正常显示
   - 创建订单时，如果商品没有SKU，可以不传 skuId

2. **库存扣减逻辑兼容**
   - 如果提供了 skuId，优先使用 SKU 库存
   - 如果没有 skuId，使用商品级别库存（兼容旧数据）

3. **API 接口兼容**
   - 创建订单时，skuId 是可选的
   - 如果商品有 SKU，前端应该传 skuId
   - 如果商品没有 SKU，可以不传 skuId

---

## 📝 具体实施顺序

### Step 1: 数据库表结构（已完成部分）
- ✅ 修改 `DatabaseInitializer` 添加 sku_id 字段
- [ ] 创建SQL迁移脚本（用于生产环境）

### Step 2: 实体类和Mapper
- [ ] 修改 `OrderItem` 实体
- [ ] 修改 `CreateOrderRequest`
- [ ] 修改 `OrderItemMapper.xml`

### Step 3: 库存服务
- [ ] 修改 `InventoryService` 方法签名
- [ ] 添加SKU相关Mapper方法
- [ ] 修改库存扣减逻辑

### Step 4: 订单服务
- [ ] 修改订单创建流程
- [ ] 添加SKU验证逻辑
- [ ] 修改价格计算逻辑

### Step 5: 商品服务
- [ ] 修改商品查询接口返回SKU列表

### Step 6: 测试
- [ ] 单元测试
- [ ] 集成测试
- [ ] 兼容性测试（没有SKU的商品）

---

## ⚠️ 注意事项

1. **数据一致性**
   - 确保SKU属于正确的商品
   - 确保SKU库存正确

2. **性能考虑**
   - SKU查询需要添加索引
   - 库存扣减需要考虑并发

3. **业务规则**
   - 如果商品有SKU，用户必须选择SKU才能下单
   - 如果商品没有SKU，可以直接下单（商品级别）

---

## 🎯 完成标准

- [ ] OrderItem 表有 sku_id 字段
- [ ] OrderItem 实体有 skuId 字段
- [ ] 创建订单时可以传 skuId
- [ ] 库存扣减基于 skuId（如果提供了）
- [ ] 商品查询返回 SKU 列表
- [ ] 价格计算优先使用 SKU 价格
- [ ] 向后兼容（没有SKU的商品仍然可以下单）
- [ ] 测试通过






