# 微服务拆分迁移指南

## 模块划分

### 1. common 模块（公共模块）
- **位置**: `common/`
- **职责**: 共享代码
- **包含**:
  - `ApiResponse` - 统一响应格式
  - `BusinessException` - 业务异常
  - `InsufficientStockException` - 库存不足异常
  - `GlobalExceptionHandler` - 全局异常处理

### 2. product-service 模块（商品服务）
- **位置**: `product-service/`
- **端口**: 8081
- **职责**: 商品、库存、店铺管理
- **包含**:
  - **实体**: `Store`, `StoreProduct`, `Inventory`, `InventoryTransaction`
  - **Mapper**: `StoreMapper`, `StoreProductMapper`, `InventoryMapper`, `InventoryTransactionMapper`
  - **Service**: `StoreService`, `StoreProductService`, `InventoryService`, `InventoryCacheService`
  - **Controller**: `StoreController`, `StoreProductController`, `InventoryController`, `InventoryCacheController`
  - **配置**: `ShardingSphereConfig` (只配置 store_products 表分片)

### 3. order-service 模块（订单服务）
- **位置**: `order-service/`
- **端口**: 8082
- **职责**: 订单、订单项、支付管理
- **包含**:
  - **实体**: `Order`, `OrderItem`, `OrderCoupon`, `OrderStatus`
  - **Mapper**: `OrderMapper`, `OrderItemMapper`, `OrderCouponMapper`
  - **Service**: `OrderService`, `PaymentService`, `AlipayService`, `OrderTimeoutFallbackService`
  - **Controller**: `OrderController`, `PaymentController`, `OrderTimeoutFallbackController`
  - **配置**: `ShardingSphereConfig` (配置 orders, order_items, order_coupons, coupon_usage 表分片)

### 4. coupon-service 模块（优惠券服务）
- **位置**: `coupon-service/`
- **端口**: 8083
- **职责**: 优惠券、优惠券使用记录管理
- **包含**:
  - **实体**: `Coupon`, `CouponUsage`
  - **Mapper**: `CouponMapper`, `CouponUsageMapper`
  - **Service**: `CouponService`
  - **Controller**: `CouponController`

## 服务间通信

使用 Spring Cloud OpenFeign 进行服务间调用：

### Feign Client 接口

1. **ProductServiceClient** (在 order-service 中)
   - `getProductById(Long productId)` - 获取商品信息
   - `checkStock(Long productId, Integer quantity)` - 检查库存
   - `lockStock(Long productId, Integer quantity)` - 锁定库存
   - `unlockStock(Long productId, Integer quantity)` - 解锁库存
   - `deductStock(Long productId, Integer quantity)` - 扣减库存

2. **CouponServiceClient** (在 order-service 中)
   - `getCouponByCode(String couponCode)` - 根据优惠券代码获取优惠券
   - `validateCoupon(String couponCode, Long userId, BigDecimal orderAmount)` - 验证优惠券
   - `useCoupon(String couponCode, Long userId, Long orderId, BigDecimal discountAmount)` - 使用优惠券

## 迁移步骤

1. ✅ 创建父 POM 和多模块结构
2. ✅ 创建 common 模块
3. ✅ 创建各服务的启动类和配置文件
4. ⏳ 迁移实体类到对应模块
5. ⏳ 迁移 Mapper 接口和 XML
6. ⏳ 迁移 Service 层
7. ⏳ 迁移 Controller 层
8. ⏳ 迁移配置类（ShardingSphereConfig, DataSourceConfig 等）
9. ⏳ 创建 Feign Client 接口
10. ⏳ 更新包名和导入
11. ⏳ 迁移 Mapper XML 文件
12. ⏳ 测试各服务独立启动

## 注意事项

1. **包名修改**: 所有代码的包名需要从 `com.jiaoyi.*` 改为对应的模块包名
   - product-service: `com.jiaoyi.product.*`
   - order-service: `com.jiaoyi.order.*`
   - coupon-service: `com.jiaoyi.coupon.*`

2. **ShardingSphere 配置**: 每个服务只配置自己需要的分片表

3. **Outbox 模式**: 每个服务都需要独立的 Outbox 表（outbox_node, outbox）

4. **数据库连接**: 各服务共享同一个数据库，但通过 ShardingSphere 路由到不同的分片表

5. **服务发现**: 当前使用硬编码的服务地址，后续可以集成 Nacos/Eureka


