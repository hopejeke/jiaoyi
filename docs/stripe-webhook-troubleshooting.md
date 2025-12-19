# Stripe Webhook 回调问题排查指南

## 问题：收不到 Stripe Webhook 回调

### 排查步骤

#### 1. 检查后端服务是否运行

```bash
# 访问健康检查端点
http://localhost:8082/actuator/health
```

如果返回 404，说明后端服务未运行或端口不对。

#### 2. 检查 ngrok 是否正常运行

```bash
# 检查 ngrok 进程
tasklist | findstr ngrok

# 或者访问 ngrok 的 Web 界面
http://localhost:4040
```

#### 3. 检查 Webhook URL 配置

**Stripe Dashboard 中的 Webhook URL 应该是：**
```
https://your-ngrok-url.ngrok-free.dev/api/payment/stripe/webhook
```

**注意：**
- 必须是 HTTPS（ngrok 免费版提供）
- 路径必须是 `/api/payment/stripe/webhook`
- 不能有尾随斜杠

#### 4. 检查后端日志

重启后端服务后，查看日志中是否有：

```
========== 收到 Stripe Webhook 回调 ==========
请求方法: POST
请求URL: ...
请求路径: /api/payment/stripe/webhook
```

**如果没有这些日志，说明 Webhook 根本没有到达后端！**

#### 5. 检查 Stripe Dashboard

1. 访问：https://dashboard.stripe.com/test/webhooks
2. 点击你的 Webhook 端点
3. 查看"事件"（Events）标签页
4. 检查是否有失败的请求（红色标记）

**常见错误：**
- `404 Not Found`：URL 路径错误
- `500 Internal Server Error`：后端处理出错
- `Timeout`：后端响应超时

#### 6. 测试 Webhook

**方法 1：使用 Stripe CLI（推荐）**

```bash
# 启动本地监听器
stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook

# 在另一个终端触发测试事件
stripe trigger payment_intent.succeeded
```

**方法 2：前端测试支付**

1. 使用测试卡号：`4242 4242 4242 4242`
2. 完成支付
3. 检查 Stripe Dashboard 中的 Webhook 事件

#### 7. 检查 Webhook Secret

在 `application.properties` 中：

```properties
stripe.webhook-secret=whsec_xxx
```

**注意：**
- 如果使用 Stripe CLI，Secret 以 `whsec_` 开头
- 如果使用 ngrok + Dashboard，Secret 也以 `whsec_` 开头
- 两个 Secret **不同**，不能混用！

#### 8. 检查防火墙/网络

- 确保本地防火墙允许 8082 端口
- 确保 ngrok 可以访问 localhost:8082
- 如果使用公司网络，可能有代理限制

### 常见问题及解决方案

#### 问题 1：后端日志没有任何 Webhook 记录

**原因：**
- ngrok 未运行
- Webhook URL 配置错误
- 网络问题

**解决：**
1. 检查 ngrok 是否运行：`tasklist | findstr ngrok`
2. 检查 Webhook URL 是否正确
3. 使用 Stripe CLI 测试：`stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook`

#### 问题 2：收到 Webhook 但处理失败

**检查后端日志：**
```
处理 Stripe Webhook 异常
异常堆栈: ...
```

**常见原因：**
- 支付记录不存在（Payment Intent ID 不匹配）
- 订单 ID 不在元数据中
- 数据库连接问题

**解决：**
1. 检查日志中的错误信息
2. 确认 Payment Intent 创建时设置了 `orderId` 元数据
3. 检查数据库连接

#### 问题 3：签名验证失败

**错误信息：**
```
Webhook 签名验证失败: ...
```

**原因：**
- Webhook Secret 配置错误
- 使用了错误的 Secret（CLI vs Dashboard）

**解决：**
1. 确认使用的是正确的 Webhook Secret
2. 如果使用 Stripe CLI，使用 CLI 显示的 Secret
3. 如果使用 Dashboard，使用 Dashboard 中的 Secret

#### 问题 4：订单状态不更新

**检查：**
1. Webhook 是否收到并处理成功
2. 后端日志中是否有"支付成功处理完成"
3. 数据库中订单状态是否更新

**可能原因：**
- Webhook 处理成功但订单更新失败
- 支付记录不存在
- 订单状态更新逻辑有问题

### 调试技巧

#### 1. 启用详细日志

在 `application.properties` 中：

```properties
logging.level.com.jiaoyi.order.controller.StripeWebhookController=DEBUG
logging.level.com.jiaoyi.order.service.PaymentService=DEBUG
```

#### 2. 使用 Stripe CLI 查看实时事件

```bash
stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook --print-json
```

#### 3. 检查 ngrok 请求日志

访问：http://localhost:4040/inspect/http

可以看到所有通过 ngrok 转发的请求。

#### 4. 手动测试 Webhook

使用 curl 或 Postman 发送测试请求：

```bash
curl -X POST http://localhost:8082/api/payment/stripe/webhook \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: test" \
  -d '{"type":"payment_intent.succeeded","id":"evt_test"}'
```

### 最佳实践

1. **开发环境**：使用 Stripe CLI
   ```bash
   stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook
   ```

2. **测试环境**：使用 ngrok + Dashboard
   - 配置真实的 Webhook URL
   - 使用 Dashboard 中的 Webhook Secret

3. **生产环境**：
   - 使用真实的公网 URL
   - 启用 HTTPS
   - 启用签名验证
   - 监控 Webhook 失败率

### 快速检查清单

- [ ] 后端服务运行在 8082 端口
- [ ] ngrok 正在运行（如果使用）
- [ ] Webhook URL 配置正确
- [ ] Webhook Secret 已配置
- [ ] 后端日志中有 Webhook 接收记录
- [ ] Stripe Dashboard 显示 Webhook 事件
- [ ] 没有 404/500 错误


