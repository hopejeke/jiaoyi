# 代码迁移清单

## 已完成 ✅

1. ✅ 父 POM 文件（多模块结构）
2. ✅ common 模块（ApiResponse, 异常处理）
3. ✅ 各服务的启动类
4. ✅ 各服务的配置文件（独立端口）
5. ✅ 实体类迁移（Store, StoreProduct, Inventory, InventoryTransaction, Order, OrderItem, OrderCoupon, Coupon, CouponUsage）
6. ✅ Feign Client 接口（ProductServiceClient, CouponServiceClient）

## 待迁移 ⏳

### product-service 模块

#### 实体类 ✅
- ✅ Store.java
- ✅ StoreProduct.java
- ✅ Inventory.java
- ✅ InventoryTransaction.java

#### Mapper 接口和 XML
- ✅ StoreMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/StoreMapper.java`
- ✅ StoreProductMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/StoreProductMapper.java`
- ✅ InventoryMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/InventoryMapper.java`
- ✅ InventoryTransactionMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/InventoryTransactionMapper.java`
- ✅ StoreMapper.xml → `product-service/src/main/resources/mapper/StoreMapper.xml`
- ✅ StoreProductMapper.xml → `product-service/src/main/resources/mapper/StoreProductMapper.xml`
- ✅ InventoryMapper.xml → `product-service/src/main/resources/mapper/InventoryMapper.xml`
- ✅ InventoryTransactionMapper.xml → `product-service/src/main/resources/mapper/InventoryTransactionMapper.xml`
- ✅ OutboxMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/OutboxMapper.java`
- ✅ OutboxNodeMapper.java → `product-service/src/main/java/com/jiaoyi/product/mapper/OutboxNodeMapper.java`
- ✅ OutboxMapper.xml → `product-service/src/main/resources/mapper/OutboxMapper.xml`
- ✅ OutboxNodeMapper.xml → `product-service/src/main/resources/mapper/OutboxNodeMapper.xml`

#### Service 层
- ✅ StoreService.java → `product-service/src/main/java/com/jiaoyi/product/service/StoreService.java`
- ✅ StoreProductService.java → `product-service/src/main/java/com/jiaoyi/product/service/StoreProductService.java`
- ✅ InventoryService.java → `product-service/src/main/java/com/jiaoyi/product/service/InventoryService.java`
- ✅ InventoryCacheService.java → `product-service/src/main/java/com/jiaoyi/product/service/InventoryCacheService.java`
- ✅ StoreProductCacheService.java → `product-service/src/main/java/com/jiaoyi/product/service/StoreProductCacheService.java`
- ✅ InventoryCacheUpdateMessageService.java → `product-service/src/main/java/com/jiaoyi/product/service/InventoryCacheUpdateMessageService.java`
- ✅ StoreProductCacheUpdateMessageService.java → `product-service/src/main/java/com/jiaoyi/product/service/StoreProductCacheUpdateMessageService.java`
- ✅ OutboxService.java → `product-service/src/main/java/com/jiaoyi/product/service/OutboxService.java`
- ✅ ClusterInfo.java → `product-service/src/main/java/com/jiaoyi/product/service/ClusterInfo.java`

#### Controller 层
- ✅ StoreController.java → `product-service/src/main/java/com/jiaoyi/product/controller/StoreController.java`
- ✅ StoreProductController.java → `product-service/src/main/java/com/jiaoyi/product/controller/StoreProductController.java`
- ✅ InventoryController.java → `product-service/src/main/java/com/jiaoyi/product/controller/InventoryController.java`
- ✅ InventoryCacheController.java → `product-service/src/main/java/com/jiaoyi/product/controller/InventoryCacheController.java`

#### 配置类
- ✅ ShardingSphereConfig.java → `product-service/src/main/java/com/jiaoyi/product/config/ShardingSphereConfig.java` (只配置 store_products 表)
- ✅ DataSourceConfig.java → `product-service/src/main/java/com/jiaoyi/product/config/DataSourceConfig.java`
- ✅ DatabaseInitializer.java → `product-service/src/main/java/com/jiaoyi/product/config/DatabaseInitializer.java` (只创建 store_products 相关表)
- ✅ OutboxThreadPoolConfig.java → `product-service/src/main/java/com/jiaoyi/product/config/OutboxThreadPoolConfig.java`
- ✅ RedisConfig.java → `product-service/src/main/java/com/jiaoyi/product/config/RedisConfig.java`
- ✅ RocketMQConfig.java → `product-service/src/main/java/com/jiaoyi/product/config/RocketMQConfig.java`

#### DTO
- ✅ StoreProductCacheUpdateMessage.java → `product-service/src/main/java/com/jiaoyi/product/dto/StoreProductCacheUpdateMessage.java`
- ✅ StoreProductTransactionArg.java → `product-service/src/main/java/com/jiaoyi/product/dto/StoreProductTransactionArg.java`
- ✅ InventoryCacheUpdateMessage.java → `product-service/src/main/java/com/jiaoyi/product/dto/InventoryCacheUpdateMessage.java`
- ✅ InventoryTransactionArg.java → `product-service/src/main/java/com/jiaoyi/product/dto/InventoryTransactionArg.java`

#### 其他
- ✅ Outbox.java → `product-service/src/main/java/com/jiaoyi/product/entity/Outbox.java`
- ✅ OutboxNode.java → `product-service/src/main/java/com/jiaoyi/product/entity/OutboxNode.java`
- ✅ OutboxStatusTypeHandler.java → `product-service/src/main/java/com/jiaoyi/product/handler/OutboxStatusTypeHandler.java`

### order-service 模块

#### 实体类 ✅
- ✅ Order.java
- ✅ OrderItem.java
- ✅ OrderCoupon.java
- ✅ OrderStatus.java

#### Mapper 接口和 XML
- OrderMapper.java → `order-service/src/main/java/com/jiaoyi/order/mapper/OrderMapper.java`
- OrderItemMapper.java → `order-service/src/main/java/com/jiaoyi/order/mapper/OrderItemMapper.java`
- OrderCouponMapper.java → `order-service/src/main/java/com/jiaoyi/order/mapper/OrderCouponMapper.java`
- OrderMapper.xml → `order-service/src/main/resources/mapper/OrderMapper.xml`
- OrderItemMapper.xml → `order-service/src/main/resources/mapper/OrderItemMapper.xml`
- OrderCouponMapper.xml → `order-service/src/main/resources/mapper/OrderCouponMapper.xml`

#### Service 层
- ✅ OrderService.java → `order-service/src/main/java/com/jiaoyi/order/service/OrderService.java` (需要注入 ProductServiceClient 和 CouponServiceClient)
- ✅ PaymentService.java → `order-service/src/main/java/com/jiaoyi/order/service/PaymentService.java`
- ✅ AlipayService.java → `order-service/src/main/java/com/jiaoyi/order/service/AlipayService.java`
- ✅ OrderTimeoutFallbackService.java → `order-service/src/main/java/com/jiaoyi/order/service/OrderTimeoutFallbackService.java`
- ✅ OrderTimeoutMessageService.java → `order-service/src/main/java/com/jiaoyi/order/service/OrderTimeoutMessageService.java`

#### Controller 层
- ✅ OrderController.java → `order-service/src/main/java/com/jiaoyi/order/controller/OrderController.java`
- ✅ PaymentController.java → `order-service/src/main/java/com/jiaoyi/order/controller/PaymentController.java`
- ✅ OrderTimeoutFallbackController.java → `order-service/src/main/java/com/jiaoyi/order/controller/OrderTimeoutFallbackController.java`
- ✅ OrderTimeoutMQController.java → `order-service/src/main/java/com/jiaoyi/order/controller/OrderTimeoutMQController.java`

#### 配置类
- ✅ ShardingSphereConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/ShardingSphereConfig.java` (配置 orders, order_items, order_coupons 表)
- ✅ DataSourceConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/DataSourceConfig.java`
- ✅ DatabaseInitializer.java → `order-service/src/main/java/com/jiaoyi/order/config/DatabaseInitializer.java` (只创建 orders 相关表)
- ⚠️ OutboxService.java → 暂不迁移到 order-service（order-service 不需要独立的 outbox 处理）
- ✅ OutboxThreadPoolConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/OutboxThreadPoolConfig.java`
- ✅ RedisConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/RedisConfig.java`
- ✅ RocketMQConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/RocketMQConfig.java`
- ✅ AlipayConfig.java → `order-service/src/main/java/com/jiaoyi/order/config/AlipayConfig.java`
- ✅ ClusterInfo.java → `order-service/src/main/java/com/jiaoyi/order/service/ClusterInfo.java`

#### DTO
- CreateOrderRequest.java → `order-service/src/main/java/com/jiaoyi/order/dto/CreateOrderRequest.java`
- OrderResponse.java → `order-service/src/main/java/com/jiaoyi/order/dto/OrderResponse.java`
- PaymentRequest.java → `order-service/src/main/java/com/jiaoyi/order/dto/PaymentRequest.java`
- PaymentResponse.java → `order-service/src/main/java/com/jiaoyi/order/dto/PaymentResponse.java`
- OrderTimeoutMessage.java → `order-service/src/main/java/com/jiaoyi/order/dto/OrderTimeoutMessage.java`

#### 其他
- Outbox.java → `order-service/src/main/java/com/jiaoyi/order/entity/Outbox.java`
- OutboxNode.java → `order-service/src/main/java/com/jiaoyi/order/entity/OutboxNode.java`
- OutboxMapper.java → `order-service/src/main/java/com/jiaoyi/order/mapper/OutboxMapper.java`
- OutboxNodeMapper.java → `order-service/src/main/java/com/jiaoyi/order/mapper/OutboxNodeMapper.java`
- OutboxMapper.xml → `order-service/src/main/resources/mapper/OutboxMapper.xml`
- OutboxNodeMapper.xml → `order-service/src/main/resources/mapper/OutboxNodeMapper.xml`
- OutboxStatusTypeHandler.java → `order-service/src/main/java/com/jiaoyi/order/handler/OutboxStatusTypeHandler.java`

### coupon-service 模块

#### 实体类 ✅
- ✅ Coupon.java
- ✅ CouponUsage.java

#### Mapper 接口和 XML
- CouponMapper.java → `coupon-service/src/main/java/com/jiaoyi/coupon/mapper/CouponMapper.java`
- CouponUsageMapper.java → `coupon-service/src/main/java/com/jiaoyi/coupon/mapper/CouponUsageMapper.java`
- CouponMapper.xml → `coupon-service/src/main/resources/mapper/CouponMapper.xml`
- CouponUsageMapper.xml → `coupon-service/src/main/resources/mapper/CouponUsageMapper.xml`

#### Service 层
- CouponService.java → `coupon-service/src/main/java/com/jiaoyi/coupon/service/CouponService.java`

#### Controller 层
- CouponController.java → `coupon-service/src/main/java/com/jiaoyi/coupon/controller/CouponController.java`

#### 配置类
- ✅ DataSourceConfig.java → `coupon-service/src/main/java/com/jiaoyi/coupon/config/DataSourceConfig.java`
- ✅ RedisConfig.java → `coupon-service/src/main/java/com/jiaoyi/coupon/config/RedisConfig.java`
- ✅ RocketMQConfig.java → `coupon-service/src/main/java/com/jiaoyi/coupon/config/RocketMQConfig.java`

## 迁移注意事项

1. **包名修改**: 所有代码的包名需要从 `com.jiaoyi.*` 改为对应的模块包名 ✅
2. **导入更新**: 更新所有 import 语句，引用 common 模块的类使用 `com.jiaoyi.common.*` 或 `com.jiaoyi.exception.*` ✅
3. **服务间调用**: OrderService 中调用商品和优惠券服务时，使用 Feign Client 替代直接调用 ✅
4. **ShardingSphere 配置**: 每个服务只配置自己需要的分片表 ✅
5. **Outbox 表**: product-service 和 order-service 共享同一个 outbox 表（在 jiaoyi 数据库中） ✅

## 已迁移到 common 模块的类

1. ✅ `ApiResponse.java` → `common/src/main/java/com/jiaoyi/common/ApiResponse.java`
2. ✅ `BusinessException.java` → `common/src/main/java/com/jiaoyi/common/exception/BusinessException.java`
3. ✅ `InsufficientStockException.java` → `common/src/main/java/com/jiaoyi/common/exception/InsufficientStockException.java`
4. ✅ `GlobalExceptionHandler.java` → `common/src/main/java/com/jiaoyi/common/exception/GlobalExceptionHandler.java`
5. ✅ `CartFingerprintUtil.java` → `common/src/main/java/com/jiaoyi/common/util/CartFingerprintUtil.java`
6. ✅ `PreventDuplicateSubmission.java` → `common/src/main/java/com/jiaoyi/common/annotation/PreventDuplicateSubmission.java`
7. ✅ `PreventDuplicateSubmissionAspect.java` → `common/src/main/java/com/jiaoyi/common/aspect/PreventDuplicateSubmissionAspect.java`

## 快速迁移脚本

可以使用以下 PowerShell 脚本批量复制文件（需要手动修改包名）：

```powershell
# 复制 product-service 相关文件
Copy-Item "src/main/java/com/jiaoyi/mapper/StoreMapper.java" "product-service/src/main/java/com/jiaoyi/product/mapper/" -Force
Copy-Item "src/main/java/com/jiaoyi/mapper/StoreProductMapper.java" "product-service/src/main/java/com/jiaoyi/product/mapper/" -Force
# ... 其他文件
```

