# SKU级别销售改造完成总结

## ✅ 已完成改造

### Phase 1: 数据库和实体类 ✅
- ✅ OrderItem表添加 `sku_id`、`sku_name`、`sku_attributes` 字段
- ✅ OrderItem表添加 `idx_sku_id` 索引
- ✅ OrderItem实体添加 `skuId`、`skuName`、`skuAttributes` 字段
- ✅ CreateOrderRequest.OrderItemRequest 添加 `skuId` 字段
- ✅ OrderItemMapper.xml 添加 skuId 映射

### Phase 2: 库存服务改造 ✅
- ✅ InventoryService.checkAndLockStock 改为基于 skuId
- ✅ InventoryService.deductStock 改为基于 skuId
- ✅ InventoryService.unlockStock 改为基于 skuId
- ✅ InventoryService.checkAndLockStockBatch 改为接收 skuIds 列表
- ✅ InventoryService.deductStockBatch 改为接收 skuIds 列表
- ✅ InventoryService.unlockStockBatch 改为接收 skuIds 列表
- ✅ InventoryService.getInventoryBySkuId 新增方法

### Phase 3: 订单服务改造 ✅
- ✅ OrderService.createOrder 验证SKU并保存skuId
- ✅ OrderService.calculateOrderPrice 优先使用SKU价格
- ✅ OrderController.buildOrderItemsFromRequest 使用SKU价格和保存skuId
- ✅ OrderService.handleInventoryStatusChange 使用skuId扣减/解锁库存
- ✅ OrderTimeoutMessageService 使用skuId解锁库存
- ✅ PaymentController 使用skuId扣减库存
- ✅ ProductServiceClient 批量请求类添加 skuIds 字段

### Phase 4: 商品服务改造 ✅
- ✅ StoreProductController.getProductByMerchantIdAndId 返回SKU列表
- ✅ InventoryController 批量方法改为基于SKU
- ✅ InventoryController 单个方法改为基于SKU（添加skuId参数）

---

## 🔄 改造后的流程

### 创建订单流程
1. 接收订单请求（包含 productId + skuId）
2. 验证 skuId 不能为空
3. 查询商品信息和SKU信息
4. 优先使用SKU价格，如果没有则使用商品价格
5. 使用 skuId 锁定库存
6. 创建订单项（保存 productId + skuId + skuName + skuAttributes）

### 库存扣减流程
1. 支付成功后，从订单项获取 productId + skuId
2. 使用 skuId 扣减库存（SKU级别）
3. 订单取消时，使用 skuId 解锁库存

### 价格计算流程
1. 查询商品信息（包含SKU列表）
2. 如果提供了 skuId，从SKU列表中查找对应SKU
3. 优先使用 SKU价格（skuPrice）
4. 如果SKU没有价格，使用商品价格（unitPrice）

---

## 📝 修改的文件清单

### 数据库和实体类
- `order-service/src/main/java/com/jiaoyi/order/config/DatabaseInitializer.java`
- `order-service/src/main/java/com/jiaoyi/order/entity/OrderItem.java`
- `order-service/src/main/java/com/jiaoyi/order/dto/CreateOrderRequest.java`
- `order-service/src/main/resources/mapper/OrderItemMapper.xml`

### 库存服务
- `product-service/src/main/java/com/jiaoyi/product/service/InventoryService.java`
- `product-service/src/main/java/com/jiaoyi/product/controller/InventoryController.java`

### 订单服务
- `order-service/src/main/java/com/jiaoyi/order/service/OrderService.java`
- `order-service/src/main/java/com/jiaoyi/order/controller/OrderController.java`
- `order-service/src/main/java/com/jiaoyi/order/service/OrderTimeoutMessageService.java`
- `order-service/src/main/java/com/jiaoyi/order/controller/PaymentController.java`
- `order-service/src/main/java/com/jiaoyi/order/client/ProductServiceClient.java`

### 商品服务
- `product-service/src/main/java/com/jiaoyi/product/controller/StoreProductController.java`

---

## ⚠️ 注意事项

1. **skuId 必填**：创建订单时，skuId 不能为空
2. **SKU验证**：订单创建时会验证SKU是否存在
3. **价格优先级**：SKU价格 > 商品价格
4. **库存级别**：所有库存操作都基于SKU级别

---

## 🧪 测试建议

1. **创建订单测试**：
   - 测试传入 skuId 的订单创建
   - 测试不传 skuId 的订单创建（应该失败）
   - 测试SKU价格和商品价格的使用

2. **库存扣减测试**：
   - 测试支付成功后SKU库存扣减
   - 测试订单取消后SKU库存解锁

3. **价格计算测试**：
   - 测试使用SKU价格计算订单总价
   - 测试SKU没有价格时使用商品价格

---

## ✅ 完成标准

- ✅ OrderItem 表有 sku_id 字段
- ✅ OrderItem 实体有 skuId 字段
- ✅ 创建订单时必须传 skuId
- ✅ 库存扣减基于 skuId
- ✅ 商品查询返回 SKU 列表
- ✅ 价格计算优先使用 SKU 价格

**改造完成！** 🎉






