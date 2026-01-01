# SKU改造进度

## ✅ 已完成

### Phase 1: 数据库和实体类
- ✅ OrderItem表添加sku_id、sku_name、sku_attributes字段
- ✅ OrderItem实体添加skuId、skuName、skuAttributes字段
- ✅ CreateOrderRequest添加skuId字段
- ✅ OrderItemMapper.xml添加skuId映射

### Phase 2: 库存服务（部分完成）
- ✅ InventoryService.checkAndLockStock 改为基于skuId
- ✅ InventoryService.deductStock 改为基于skuId
- ✅ InventoryService.unlockStock 改为基于skuId
- ⚠️ 批量方法还需要修改（需要传入skuIds列表）

## 🔄 进行中

### Phase 2: 批量方法改造
- [ ] checkAndLockStockBatch - 需要改为接收skuIds列表
- [ ] deductStockBatch - 需要改为接收skuIds列表
- [ ] unlockStockBatch - 需要改为接收skuIds列表

### Phase 3: 订单服务改造
- [ ] OrderService.createOrder - 验证SKU、使用SKU价格
- [ ] OrderService.calculateOrderPrice - 使用SKU价格
- [ ] ProductServiceClient - 修改Feign接口，添加skuId参数

## 📝 待办

### Phase 4: 商品服务改造
- [ ] 商品查询接口返回SKU列表

---

## ⚠️ 注意事项

1. **批量方法改造**：需要修改方法签名，接收skuIds列表而不是productIds
2. **调用方改造**：OrderService中调用库存方法的地方需要传入skuId
3. **Feign接口**：ProductServiceClient需要修改接口定义






