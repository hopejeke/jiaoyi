# SKU级别销售改造方案

## 📋 问题分析

### 当前状态
- ❌ 订单项（OrderItem）只有 `productId`，没有 `skuId`
- ❌ 库存扣减基于 `productId`，不是 `skuId`
- ❌ 创建订单时只传 `productId`，不传 `skuId`
- ⚠️ Inventory 表虽然有 `skuId` 字段，但可能大部分记录都是 null（商品级别库存）

### 目标状态
- ✅ 订单项支持 `skuId`
- ✅ 库存扣减基于 `skuId`（如果提供了 skuId）
- ✅ 创建订单时必须传 `skuId`（如果商品有 SKU）
- ✅ 库存管理按 SKU 级别

---

## 🔧 改造方案

### 1. 数据库表结构改造

#### 1.1 OrderItem 表添加 skuId 字段

```sql
-- 添加 sku_id 字段（允许为 NULL，兼容旧数据）
ALTER TABLE order_items_0 ADD COLUMN sku_id BIGINT COMMENT 'SKU ID（关联 product_sku.id）';
ALTER TABLE order_items_1 ADD COLUMN sku_id BIGINT COMMENT 'SKU ID（关联 product_sku.id）';
ALTER TABLE order_items_2 ADD COLUMN sku_id BIGINT COMMENT 'SKU ID（关联 product_sku.id）';

-- 添加索引
ALTER TABLE order_items_0 ADD INDEX idx_sku_id (sku_id);
ALTER TABLE order_items_1 ADD INDEX idx_sku_id (sku_id);
ALTER TABLE order_items_2 ADD INDEX idx_sku_id (sku_id);
```

#### 1.2 Inventory 表确保支持 SKU

Inventory 表已经有 `sku_id` 字段，但需要确保：
- 有唯一索引：`(store_id, product_id, sku_id)` 或 `(store_id, sku_id)`
- 如果 `sku_id` 为 NULL，表示商品级别库存（兼容旧数据）

---

### 2. 实体类改造

#### 2.1 OrderItem 实体添加 skuId

```java
public class OrderItem {
    // ... 现有字段 ...
    
    /**
     * SKU ID（如果商品有SKU，必须提供）
     */
    private Long skuId;
    
    /**
     * SKU名称（冗余字段，用于显示）
     */
    private String skuName;
    
    /**
     * SKU属性（JSON格式，冗余字段，用于显示）
     */
    private String skuAttributes;
}
```

#### 2.2 CreateOrderRequest.OrderItemRequest 添加 skuId

```java
public static class OrderItemRequest {
    // ... 现有字段 ...
    
    /**
     * SKU ID（如果商品有SKU，必须提供）
     */
    private Long skuId;
}
```

---

### 3. 业务逻辑改造

#### 3.1 订单创建流程改造

**当前流程**：
1. 接收订单请求（只有 productId）
2. 查询商品信息（基于 productId）
3. 锁定库存（基于 productId）
4. 创建订单项（只有 productId）

**改造后流程**：
1. 接收订单请求（productId + skuId）
2. 验证 SKU 是否存在且属于该商品
3. 查询 SKU 信息（价格、属性等）
4. 锁定库存（基于 skuId，如果提供了 skuId）
5. 创建订单项（productId + skuId）

#### 3.2 库存扣减逻辑改造

**当前逻辑**：
```java
inventoryMapper.selectByProductId(productId)
inventoryMapper.lockStock(productId, quantity)
```

**改造后逻辑**：
```java
// 优先使用 skuId
if (skuId != null) {
    inventoryMapper.selectBySkuId(skuId)
    inventoryMapper.lockStockBySkuId(skuId, quantity)
} else {
    // 兼容旧数据：商品级别库存
    inventoryMapper.selectByProductId(productId)
    inventoryMapper.lockStock(productId, quantity)
}
```

#### 3.3 价格计算改造

**当前逻辑**：
- 从商品表获取价格（StoreProduct.unitPrice）

**改造后逻辑**：
- 如果提供了 skuId，优先使用 SKU 价格（ProductSku.skuPrice）
- 如果 SKU 没有价格，使用商品价格（StoreProduct.unitPrice）

---

### 4. API 接口改造

#### 4.1 创建订单接口

**请求参数**：
```json
{
  "orderItems": [
    {
      "productId": 123,
      "skuId": 456,  // 新增：必须提供（如果商品有SKU）
      "quantity": 2
    }
  ]
}
```

#### 4.2 商品查询接口

需要返回 SKU 列表：
```json
{
  "id": 123,
  "productName": "T恤",
  "unitPrice": 99.00,
  "skus": [  // 新增：SKU列表
    {
      "id": 456,
      "skuCode": "TEE-RED-L",
      "skuName": "红色 L码",
      "skuPrice": 99.00,
      "skuAttributes": {"color": "红色", "size": "L"},
      "currentStock": 10
    }
  ]
}
```

---

## 📝 实施步骤

### Phase 1: 数据库和实体类改造（1-2天）

1. ✅ 修改 OrderItem 表结构（添加 skuId 字段）
2. ✅ 修改 OrderItem 实体类（添加 skuId 字段）
3. ✅ 修改 CreateOrderRequest（添加 skuId 字段）
4. ✅ 修改 OrderItemMapper.xml（添加 skuId 映射）

### Phase 2: 库存服务改造（2-3天）

1. ✅ 修改 InventoryService：
   - `checkAndLockStock` 支持 skuId
   - `deductStock` 支持 skuId
   - `unlockStock` 支持 skuId
2. ✅ 修改 InventoryMapper：
   - 添加 `selectBySkuId` 方法
   - 添加 `lockStockBySkuId` 方法
   - 添加 `deductStockBySkuId` 方法

### Phase 3: 订单服务改造（2-3天）

1. ✅ 修改 OrderService.createOrder：
   - 验证 SKU 是否存在
   - 查询 SKU 信息（价格、属性）
   - 使用 skuId 锁定库存
   - 保存 skuId 到订单项
2. ✅ 修改价格计算逻辑：
   - 优先使用 SKU 价格

### Phase 4: 商品服务改造（1-2天）

1. ✅ 修改商品查询接口：
   - 返回 SKU 列表
   - 返回每个 SKU 的库存信息

### Phase 5: 测试和数据迁移（2-3天）

1. ✅ 单元测试
2. ✅ 集成测试
3. ✅ 数据迁移（如果有旧数据需要迁移）

---

## 🔄 兼容性处理

### 向后兼容

1. **skuId 字段允许为 NULL**
   - 旧订单项没有 skuId，仍然可以正常显示
   - 旧库存记录没有 skuId，仍然可以正常扣减

2. **库存扣减逻辑兼容**
   - 如果提供了 skuId，优先使用 SKU 库存
   - 如果没有 skuId，使用商品级别库存（兼容旧数据）

3. **API 接口兼容**
   - 创建订单时，如果商品没有 SKU，可以不传 skuId
   - 如果商品有 SKU，必须传 skuId

---

## ⚠️ 注意事项

1. **数据一致性**
   - 确保 SKU 属于正确的商品
   - 确保 SKU 库存正确

2. **性能考虑**
   - SKU 查询需要添加索引
   - 库存扣减需要考虑并发

3. **业务规则**
   - 如果商品有 SKU，用户必须选择 SKU 才能下单
   - 如果商品没有 SKU，可以直接下单（商品级别）

---

## 📊 影响范围

### 需要修改的文件

1. **数据库表结构**
   - `order_items_0/1/2` 表

2. **实体类**
   - `OrderItem.java`
   - `CreateOrderRequest.java`

3. **Mapper**
   - `OrderItemMapper.xml`
   - `InventoryMapper.java`
   - `InventoryMapper.xml`

4. **Service**
   - `OrderService.java`
   - `InventoryService.java`
   - `ProductService.java`（商品查询）

5. **Controller**
   - `OrderController.java`
   - `ProductController.java`（商品查询）

---

## 🎯 优先级

**高优先级**（必须完成）：
1. 数据库表结构改造
2. 实体类改造
3. 订单创建流程改造
4. 库存扣减逻辑改造

**中优先级**（建议完成）：
1. 商品查询接口改造（返回SKU列表）
2. 价格计算逻辑改造

**低优先级**（可选）：
1. 数据迁移（旧数据）
2. 性能优化

---

## 📅 预计时间

- **Phase 1**: 1-2天
- **Phase 2**: 2-3天
- **Phase 3**: 2-3天
- **Phase 4**: 1-2天
- **Phase 5**: 2-3天

**总计**: 8-13天

---

## ✅ 完成标准

- [ ] OrderItem 表有 skuId 字段
- [ ] OrderItem 实体有 skuId 字段
- [ ] 创建订单时可以传 skuId
- [ ] 库存扣减基于 skuId（如果提供了）
- [ ] 商品查询返回 SKU 列表
- [ ] 价格计算优先使用 SKU 价格
- [ ] 测试通过






