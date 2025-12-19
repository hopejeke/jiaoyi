# Stripe Webhook 调试指南

## 问题 1：Webhook 签名验证失败

### 错误信息
```
SignatureVerificationException: No signatures found matching the expected signature for payload
```

### 原因
Stripe 使用 `webhook-secret` 对每个 Webhook 事件进行签名，后端需要验证签名以确保请求来自 Stripe。如果签名不匹配，说明：
1. 配置的 `webhook-secret` 与 Stripe 使用的 secret 不一致
2. 使用了错误的 secret（例如：使用 Dashboard 的 secret 但实际使用 CLI 转发）

### 解决方案

#### 方案 A：使用 Stripe CLI 转发（推荐用于本地开发）

1. **启动 Stripe CLI 转发：**
   ```bash
   stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook
   ```

2. **复制 CLI 显示的 webhook secret：**
   ```
   > Ready! Your webhook signing secret is whsec_xxxxxxxxxxxxx (^C to quit)
   ```

3. **更新 `application.properties`：**
   ```properties
   stripe.webhook-secret=whsec_xxxxxxxxxxxxx  # 使用 CLI 显示的 secret
   ```

4. **重启后端服务**

#### 方案 B：使用 Stripe Dashboard 配置的 Endpoint

1. **在 Stripe Dashboard 中配置 Webhook：**
   - 访问：https://dashboard.stripe.com/test/webhooks
   - 点击 "Add endpoint"
   - 输入你的 ngrok URL：`https://your-ngrok-url.ngrok-free.dev/api/payment/stripe/webhook`
   - 选择要监听的事件：`payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.succeeded`, `charge.failed`

2. **获取 Signing secret：**
   - 在 Webhook 详情页面，点击 "Reveal" 显示 Signing secret
   - 复制 `whsec_...` 开头的 secret

3. **更新 `application.properties`：**
   ```properties
   stripe.webhook-secret=whsec_xxxxxxxxxxxxx  # 使用 Dashboard 的 Signing secret
   ```

4. **重启后端服务**

#### 方案 C：临时跳过签名验证（仅开发环境）

**⚠️ 警告：仅用于本地开发测试，生产环境必须启用签名验证！**

1. **修改 `application.properties`：**
   ```properties
   stripe.webhook-secret=请替换  # 设置为包含"请替换"的字符串
   ```

2. **重启后端服务**

3. **代码会自动跳过签名验证**（见 `StripeWebhookController.java` 第 77 行）

### 如何判断使用哪个 Secret？

- **如果使用 `stripe listen` 命令转发：** 使用 CLI 显示的 secret
- **如果使用 Dashboard 配置的 endpoint：** 使用 Dashboard 的 Signing secret
- **两者不能混用！** 如果混用，签名验证会失败

### 验证签名是否配置正确

1. **查看后端日志：**
   ```
   ========== 收到 Stripe Webhook 回调 ==========
   验证 Webhook 签名...
   Webhook 签名验证成功
   ```

2. **如果看到 "签名验证失败"：**
   - 检查 `application.properties` 中的 `stripe.webhook-secret`
   - 确认使用的是正确的 secret（CLI 或 Dashboard）
   - 查看日志中的详细错误信息和解决方案

## 问题 2：支付成功但没有收到 Webhook 回调

### 排查步骤

#### 1. 检查 Stripe Dashboard 中的 Webhook 事件

1. 访问：https://dashboard.stripe.com/test/webhooks
2. 点击你的 Webhook 端点
3. 查看"事件交付"（Event Delivery）标签页
4. **关键检查**：
   - 是否有 `payment_intent.succeeded` 事件？
   - 如果有，状态是什么？（绿色 = 成功，红色 = 失败）
   - 如果失败，点击查看错误详情

**如果 Dashboard 显示事件失败（红色）：**
- 点击事件查看详情
- 查看错误信息：
  - `404 Not Found`：URL 路径错误
  - `500 Internal Server Error`：后端处理出错
  - `Timeout`：后端响应超时
  - `Connection refused`：ngrok 未运行或后端服务未运行

**如果 Dashboard 显示 0 个事件：**
- 说明 Stripe 根本没有发送 Webhook
- 可能原因：
  1. Payment Intent 创建失败
  2. 前端没有正确确认支付
  3. Payment Intent 状态不是 `succeeded`

#### 2. 检查 Payment Intent 是否成功创建

**在后端日志中查找：**
```
Payment Intent 创建成功，ID: pi_xxx, Client Secret: pi_xxx_secret_xxx
```

**如果没有这条日志：**
- Payment Intent 创建失败
- 检查 Stripe API Key 是否正确
- 检查后端日志中的错误信息

#### 3. 检查前端是否正确确认支付

**在前端控制台（F12）中检查：**
- 是否调用了 `stripe.confirmCardPayment()`？
- `paymentIntent.status` 是什么？
- 是否有错误信息？

**如果 `paymentIntent.status === 'succeeded'`：**
- 说明前端确认成功
- Stripe 应该会发送 Webhook
- 如果没有收到，检查 Dashboard

#### 4. 检查 ngrok 是否运行

```bash
# 检查进程
tasklist | findstr ngrok

# 访问 ngrok Web 界面
http://localhost:4040
```

**在 ngrok 界面中：**
- 查看是否有来自 Stripe 的请求
- 如果有请求但显示错误，查看错误详情

#### 5. 检查 Payment Intent 的元数据

**在 Stripe Dashboard 中：**
1. 访问：https://dashboard.stripe.com/test/payments
2. 找到你的 Payment Intent（通过订单ID或金额查找）
3. 点击查看详情
4. 检查 "Metadata" 部分：
   - 是否有 `orderId`？
   - `orderId` 的值是什么？

**如果没有 `orderId`：**
- Webhook 收到后无法处理
- 检查后端创建 Payment Intent 时是否设置了元数据

#### 6. 使用 Dashboard 发送测试事件

1. 在 Stripe Dashboard 的 Webhook 页面
2. 点击 "发送测试事件"（Send test event）
3. 选择 `payment_intent.succeeded`
4. 点击发送

**如果测试事件能收到：**
- 说明 Webhook 配置正确
- 问题在于真实支付时没有触发 Webhook

**如果测试事件也收不到：**
- 检查 ngrok 是否运行
- 检查后端服务是否运行
- 检查后端日志

#### 7. 检查后端日志

**查找以下日志：**
```
========== 收到 Stripe Webhook 回调 ==========
```

**如果没有这条日志：**
- Webhook 根本没有到达后端
- 检查 ngrok 是否运行
- 检查 Webhook URL 是否正确

**如果有这条日志但后续出错：**
- 查看错误堆栈
- 检查 Payment Intent ID 是否匹配
- 检查支付记录是否存在

### 常见问题

#### Q: Dashboard 显示 0 个事件？

**A:** 可能原因：
1. 没有进行支付测试
2. Payment Intent 创建失败
3. 前端没有正确确认支付

**解决：**
1. 检查后端日志，确认 Payment Intent 是否创建成功
2. 检查前端控制台，确认是否调用了 `confirmCardPayment`
3. 检查 Stripe Dashboard → Payments，看是否有支付记录

#### Q: Dashboard 显示事件失败（红色）？

**A:** 检查错误详情：
- `404`：URL 路径错误
- `500`：后端处理出错，查看后端日志
- `Timeout`：后端响应超时，检查后端处理时间

#### Q: 前端确认成功但 Dashboard 没有事件？

**A:** 可能原因：
1. Payment Intent 状态不是 `succeeded`（可能是 `requires_action` 或其他状态）
2. Stripe 延迟发送 Webhook（通常几秒内）

**解决：**
1. 检查前端控制台中的 `paymentIntent.status`
2. 等待几秒后刷新 Dashboard
3. 检查 Stripe Dashboard → Payments 中的 Payment Intent 状态

### 调试技巧

#### 1. 添加断点

在后端 `StripeWebhookController.handleWebhook` 方法的第一行添加断点：
```java
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(...) {
    // 在这里添加断点
    log.info("========== 收到 Stripe Webhook 回调 ==========");
    // ...
}
```

#### 2. 检查 Payment Intent 状态

在 Stripe Dashboard → Payments 中：
1. 找到你的 Payment Intent
2. 查看状态（Status）
3. 如果是 `succeeded`，应该会触发 Webhook

#### 3. 使用 Stripe CLI 实时查看事件

```bash
stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook --print-json
```

这会显示所有 Stripe 事件，包括是否成功发送到后端。

### 快速检查清单

- [ ] ngrok 正在运行
- [ ] 后端服务运行在 8082 端口
- [ ] 前端支付成功（`paymentIntent.status === 'succeeded'`）
- [ ] Stripe Dashboard → Payments 中有 Payment Intent 记录
- [ ] Payment Intent 状态是 `succeeded`
- [ ] Payment Intent 的 Metadata 中有 `orderId`
- [ ] Stripe Dashboard → Webhooks 中有事件记录
- [ ] 后端日志中有 "收到 Stripe Webhook 回调" 日志

