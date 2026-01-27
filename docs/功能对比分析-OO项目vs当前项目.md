# OO项目 vs 当前项目功能对比分析

## 一、已实现功能对比

### ✅ 当前项目已实现（与OO项目对应）

| 功能模块 | OO项目 | 当前项目 | 状态 |
|---------|--------|---------|------|
| **订单管理** | `order.ts` | `OrderController`, `OrderService` | ✅ 已实现 |
| **支付处理** | `payment.ts`, `stripePay.ts` | `PaymentController`, `PaymentService`, `StripeService` | ✅ 已实现 |
| **退款处理** | `checkout.ts` (DoRefund) | `RefundController`, `RefundService` | ✅ 已实现 |
| **配送服务** | `checkout.ts` (DoorDash) | `DoorDashService`, `DeliveryService` | ✅ 已实现 |
| **商品管理** | `menu.ts` | `ProductService`, `StoreProductService` | ✅ 已实现 |
| **库存管理** | `menu.ts` | `InventoryService` | ✅ 已实现 |
| **优惠券** | `promotion.ts` | `CouponService` | ✅ 已实现 |
| **高峰限流** | `checkout.ts` (judgeCapabilityOfOrder) | `PeakHourRejectionService` | ✅ 已实现 |
| **Stripe集成** | `stripe.ts`, `stripeService.ts` | `StripeService`, `StripeWebhookController` | ✅ 已实现 |
| **Google Maps** | `googleMapsServer.ts` | `GoogleMapsService` | ✅ 已实现 |
| **Outbox模式** | 无 | `outbox-starter` | ✅ 已实现（更先进） |
| **分库分表** | 无 | ShardingSphere + 路由表 | ✅ 已实现（更先进） |

---

## 二、缺失功能清单（需要实现）

### 🔴 核心业务功能

#### 1. **购物车（Shopping Cart）**
- **OO项目位置**: `shoppingCart.ts`
- **功能描述**:
  - 添加/更新购物车
  - 按桌码（tableId）管理购物车
  - 购物车过期时间管理
  - 购物车持久化存储
- **当前状态**: ❌ 未实现
- **优先级**: 🔴 高
- **实现建议**:
  ```java
  // 需要创建：
  - ShoppingCart 实体
  - ShoppingCartController
  - ShoppingCartService
  - ShoppingCartMapper
  - 购物车表（可按 tableId 分片）
  ```

#### 2. **钱包（Wallet）**
- **OO项目位置**: `wallet.ts`
- **功能描述**:
  - 保存用户信用卡信息（Stripe PaymentMethod）
  - 管理多张信用卡
  - 信用卡过期清理
  - 快速支付（使用已保存的卡）
- **当前状态**: ❌ 未实现（前端有保存卡片逻辑，但后端无钱包服务）
- **优先级**: 🔴 高
- **实现建议**:
  ```java
  // 需要创建：
  - Wallet 实体（存储 Stripe PaymentMethod ID）
  - WalletController
  - WalletService
  - WalletMapper
  - 钱包表（按 userId 分片）
  ```

#### 3. **礼品卡（Gift Card）**
- **OO项目位置**: `gift-card.ts`
- **功能描述**:
  - 礼品卡库存管理（面值卡、商品卡）
  - 购买礼品卡
  - 使用礼品卡支付
  - 礼品卡余额查询
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - GiftCardInventory 实体
  - GiftCard 实体（用户持有的礼品卡）
  - GiftCardController
  - GiftCardService
  - GiftCardMapper
  ```

#### 4. **促销活动（Promotion）**
- **OO项目位置**: `promotion.ts`
- **功能描述**:
  - 促销活动配置（满减、折扣、买赠等）
  - 促销策略（叠加、互斥）
  - 时间范围控制（每日时间段）
  - 促销活动查询和计算
- **当前状态**: ⚠️ 部分实现（只有优惠券，缺少完整的促销活动系统）
- **优先级**: 🔴 高
- **实现建议**:
  ```java
  // 需要扩展：
  - Promotion 实体（促销活动）
  - PromotionStrategy 实体（促销策略）
  - PromotionController
  - PromotionService
  - 促销计算引擎（与订单价格计算集成）
  ```

#### 5. **热门商品（Popular Items）**
- **OO项目位置**: `popular-item.ts`
- **功能描述**:
  - 基于订单数据统计热门商品
  - 按时间周期更新（如每周）
  - Redis缓存热门商品列表
  - 支持在线订单和堂食分别统计
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - PopularItem 实体
  - PopularItemController
  - PopularItemService
  - 定时任务（统计热门商品）
  - Redis缓存
  ```

#### 6. **桌码二维码（Table QR）**
- **OO项目位置**: `table-qr.ts`
- **功能描述**:
  - 生成桌码二维码
  - 扫码绑定桌码和商户
  - 桌码订单管理
  - 堂食订单支持
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - TableQR 实体
  - TableQRController
  - TableQRService
  - 二维码生成服务
  ```

---

### 🟡 用户和通知功能

#### 7. **用户管理（User）**
- **OO项目位置**: `user.ts`
- **功能描述**:
  - 用户注册/登录
  - 用户信息管理
  - 用户会话管理
  - 用户权限控制
- **当前状态**: ⚠️ 部分实现（可能有基础用户功能，但需要确认）
- **优先级**: 🔴 高
- **实现建议**:
  ```java
  // 需要确认/创建：
  - User 实体
  - UserController
  - UserService
  - 认证授权模块（JWT/OAuth2）
  ```

#### 8. **通知系统（Notification）**
- **OO项目位置**: `notification.ts`
- **功能描述**:
  - 订单状态通知
  - 促销活动通知
  - 系统公告
  - 未读消息计数
  - 消息已读标记
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - Notification 实体
  - NotificationController
  - NotificationService
  - 消息推送服务（集成APNs/FCM）
  ```

#### 9. **推送服务（Push）**
- **OO项目位置**: `push.ts`, `pushServer.ts`
- **功能描述**:
  - iOS推送（APNs）
  - Android推送（FCM）
  - 推送消息模板
  - Badge计数管理
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - PushService（集成APNs/FCM SDK）
  - 推送消息模板
  - 设备Token管理
  ```

#### 10. **用户消息中心（User Message Center）**
- **OO项目位置**: `userMessageCenter.ts`
- **功能描述**:
  - 用户消息列表
  - 消息分类（公告、促销、新闻）
  - 未读消息统计
  - 消息状态管理
- **当前状态**: ❌ 未实现
- **优先级**: 🟢 低
- **实现建议**:
  ```java
  // 需要创建：
  - UserMessageCenter 实体
  - UserMessageCenterController
  - UserMessageCenterService
  ```

---

### 🟢 运营和分析功能

#### 11. **流量追踪（Track）**
- **OO项目位置**: `track.ts`
- **功能描述**:
  - 页面访问统计（PV）
  - 用户行为追踪
  - 订单转化率分析
  - 二维码访问统计
  - 流量来源分析
- **当前状态**: ❌ 未实现
- **优先级**: 🟢 低
- **实现建议**:
  ```java
  // 需要创建：
  - Track 实体
  - TrackController
  - TrackService
  - 数据分析服务
  ```

#### 12. **黑名单客户（Block Customer）**
- **OO项目位置**: `blockCustomer.ts`
- **功能描述**:
  - 商户黑名单管理
  - 按手机号/邮箱封禁
  - 封禁原因记录
  - 下单时检查黑名单
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - BlockCustomer 实体
  - BlockCustomerController
  - BlockCustomerService
  - 下单时黑名单检查逻辑
  ```

#### 13. **交易记录（Transaction）**
- **OO项目位置**: `transaction.ts`
- **功能描述**:
  - 支付交易记录
  - 退款交易记录
  - 交易对账
  - 交易查询
- **当前状态**: ⚠️ 部分实现（有Payment记录，但缺少Transaction聚合）
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要扩展：
  - Transaction 实体（聚合Payment）
  - TransactionController
  - TransactionService
  ```

---

### 🔵 第三方集成功能

#### 14. **微信集成（WeChat）**
- **OO项目位置**: `wechat/wechat.ts`, `weixin.ts`
- **功能描述**:
  - 微信登录
  - 微信支付
  - 微信用户信息
  - 微信公众号集成
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中（取决于业务需求）
- **实现建议**:
  ```java
  // 需要创建：
  - WeChatService
  - WeChatController
  - 微信SDK集成
  ```

#### 15. **Google订单（Google Food Order）**
- **OO项目位置**: `google/googleOrderCheckout.ts`, `google/googleOrderUpload.ts`
- **功能描述**:
  - Google订单同步
  - Google订单状态更新
  - Google订单验证
- **当前状态**: ❌ 未实现
- **优先级**: 🟢 低（特定业务需求）
- **实现建议**:
  ```java
  // 需要创建：
  - GoogleOrderService
  - GoogleOrderController
  - Google API集成
  ```

#### 16. **Riskified反欺诈**
- **OO项目位置**: `riskified/riskified.ts`
- **功能描述**:
  - 订单风险评估
  - 反欺诈检测
  - Riskified API集成
- **当前状态**: ❌ 未实现
- **优先级**: 🟢 低（特定业务需求）
- **实现建议**:
  ```java
  // 需要创建：
  - RiskifiedService
  - 风险评估逻辑
  ```

---

### 🟣 高级功能

#### 17. **分单支付（Split Order Payment）**
- **OO项目位置**: `分单支付技术方案.md`
- **功能描述**:
  - 订单分单（按订单项/按比例/按价格）
  - 子订单独立支付
  - 父订单状态聚合
  - POS系统同步
- **当前状态**: ❌ 未实现
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - SubOrderGroup 实体
  - ShareProportion 实体
  - SplitOrderController
  - SplitOrderService
  - 分单支付逻辑
  ```

#### 18. **POS分单机制**
- **OO项目位置**: `POS分单机制说明.md`
- **功能描述**:
  - 订单项拆分
  - 按打印机分单
  - 厨房显示系统（KDS）集成
- **当前状态**: ❌ 未实现
- **优先级**: 🟢 低（POS系统功能）
- **实现建议**:
  ```java
  // 需要创建：
  - OrderItemPrinter 实体
  - 打印机分单逻辑
  - KDS集成
  ```

#### 19. **定时任务服务（Task Service）**
- **OO项目位置**: `oo2-task/`
- **功能描述**:
  - 定时任务调度
  - 后台任务处理
  - 任务状态管理
- **当前状态**: ⚠️ 部分实现（有Spring Scheduled，但缺少独立任务服务）
- **优先级**: 🟡 中
- **实现建议**:
  ```java
  // 需要创建：
  - TaskService（独立微服务）
  - 任务调度器（Quartz/XXL-Job）
  - 任务管理界面
  ```

---

## 三、功能优先级建议

### 🔴 高优先级（核心业务，必须实现）

1. **购物车（Shopping Cart）** - 用户下单前必须功能
2. **钱包（Wallet）** - 提升支付体验，减少重复输入
3. **促销活动（Promotion）** - 营销核心功能
4. **用户管理（User）** - 基础功能，如果缺失需要补充

### 🟡 中优先级（重要功能，建议实现）

5. **礼品卡（Gift Card）** - 增加收入渠道
6. **热门商品（Popular Items）** - 提升用户体验
7. **桌码二维码（Table QR）** - 支持堂食场景
8. **通知系统（Notification）** - 提升用户粘性
9. **推送服务（Push）** - 实时通知用户
10. **黑名单客户（Block Customer）** - 风控功能
11. **交易记录（Transaction）** - 财务对账
12. **分单支付（Split Order Payment）** - 多人聚餐场景

### 🟢 低优先级（可选功能，按需实现）

13. **流量追踪（Track）** - 数据分析
14. **用户消息中心（User Message Center）** - 消息管理
15. **微信集成（WeChat）** - 特定市场
16. **Google订单（Google Food Order）** - 特定业务
17. **Riskified反欺诈** - 特定需求
18. **POS分单机制** - POS系统功能
19. **定时任务服务（Task Service）** - 基础设施

---

## 四、实现建议

### 阶段一：核心功能（1-2个月）

1. ✅ 购物车服务
2. ✅ 钱包服务
3. ✅ 促销活动系统
4. ✅ 用户管理完善

### 阶段二：重要功能（2-3个月）

5. ✅ 礼品卡系统
6. ✅ 热门商品统计
7. ✅ 通知和推送系统
8. ✅ 分单支付

### 阶段三：运营功能（3-4个月）

9. ✅ 流量追踪
10. ✅ 黑名单管理
11. ✅ 交易记录聚合
12. ✅ 第三方集成（微信等）

---

## 五、技术债务对比

### OO项目的技术特点
- Node.js/TypeScript
- MongoDB
- Restify框架
- 单体架构

### 当前项目的技术特点
- Java/Spring Boot
- MySQL + ShardingSphere
- 微服务架构
- Outbox模式（更先进）
- 分库分表（更先进）

### 优势
✅ 当前项目在架构上更先进：
- 微服务架构，更易扩展
- 分库分表，支持更大规模
- Outbox模式，保证最终一致性
- 事务管理更完善

### 需要补充
⚠️ 需要补充业务功能：
- 购物车、钱包等核心功能
- 促销活动系统
- 通知推送系统

---

## 六、总结

当前项目在**架构设计**和**技术选型**上比OO项目更先进，但在**业务功能完整性**上还有较大差距。

**主要缺失：**
- 🔴 购物车、钱包、促销活动（核心业务）
- 🟡 通知推送、热门商品、礼品卡（重要功能）
- 🟢 流量追踪、第三方集成（运营功能）

**建议优先实现：**
1. 购物车服务（用户下单必需）
2. 钱包服务（提升支付体验）
3. 促销活动系统（营销核心）
4. 用户管理完善（基础功能）

这些功能实现后，当前项目将具备完整的在线点餐系统能力。



