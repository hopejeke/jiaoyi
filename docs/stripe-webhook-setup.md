# Stripe Webhook 配置指南

## 问题：订单一直是待支付状态

### 原因分析

Stripe 支付有两种流程：

1. **异步支付（Payment Intent）**：
   - 前端使用 `clientSecret` 确认支付
   - 前端确认后，Stripe **异步发送 Webhook** 到后端
   - 后端收到 Webhook 后更新订单状态

2. **同步支付（Direct Charge）**：
   - 使用已保存的 Payment Method
   - 后端直接扣款，立即返回结果

### 当前问题

前端确认支付成功后，**订单状态需要等待 Stripe Webhook 回调**才能更新。

在**本地开发环境**中，Stripe 无法直接访问 `localhost`，所以 Webhook 无法送达！

## 解决方案

### 方案 1：使用 Stripe CLI（推荐用于开发）

1. **安装 Stripe CLI**：
   ```bash
   # Windows (使用 Chocolatey)
   choco install stripe
   
   # 或下载：https://github.com/stripe/stripe-cli/releases
   ```

2. **登录 Stripe CLI**：
   ```bash
   stripe login
   ```

3. **转发 Webhook 到本地**：
   ```bash
   stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook
   ```

4. **复制 Webhook Secret**：
   - CLI 会显示一个 `whsec_xxx` 的密钥
   - 配置到 `application.properties`：
     ```properties
     stripe.webhook-secret=whsec_xxx
     ```

5. **测试 Webhook**：
   ```bash
   stripe trigger payment_intent.succeeded
   ```

### 方案 2：使用 ngrok（用于测试）

1. **安装 ngrok**：
   ```bash
   # 下载：https://ngrok.com/download
   ```

2. **启动 ngrok**：
   ```bash
   ngrok http 8082
   ```

3. **配置 Stripe Webhook**：
   - 访问 https://dashboard.stripe.com/test/webhooks
   - 点击 "Add endpoint"
   - Endpoint URL: `https://your-ngrok-url.ngrok.io/api/payment/stripe/webhook`
   - 选择事件：`payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.succeeded`, `charge.failed`
   - 点击 "Add endpoint"

4. **复制 Webhook Secret**：
   - 在 Webhook 详情页面，复制 "Signing secret"
   - 配置到 `application.properties`

### 方案 3：前端确认后立即轮询（临时方案）

修改前端代码，在确认支付成功后立即开始轮询订单状态：

```javascript
// 确认支付成功后
const { paymentIntent, error: confirmError } = await stripe.confirmCardPayment(
    paymentResponse.clientSecret,
    {
        payment_method: paymentMethod.id
    }
);

if (confirmError) {
    throw new Error(confirmError.message);
}

// 支付确认成功，立即开始轮询订单状态
if (paymentIntent.status === 'succeeded') {
    showInfo('支付确认成功，等待订单状态更新...');
    startPaymentStatusPolling(orderId);
}
```

## 检查 Webhook 是否收到

### 1. 查看后端日志

```bash
# 应该看到类似日志：
收到 Stripe Webhook 回调
Stripe Webhook 事件类型: payment_intent.succeeded, ID: evt_xxx
Payment Intent 支付成功，Payment Intent ID: pi_xxx, 订单ID: xxx
```

### 2. 查看 Stripe Dashboard

1. 访问 https://dashboard.stripe.com/test/webhooks
2. 查看 Webhook 事件列表
3. 检查是否有失败的请求（红色标记）

### 3. 测试 Webhook

使用 Stripe CLI 测试：
```bash
stripe trigger payment_intent.succeeded
```

## 开发环境最佳实践

### 推荐流程：

1. **开发阶段**：使用 Stripe CLI 转发 Webhook
   ```bash
   stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook
   ```

2. **测试阶段**：使用 ngrok 配置真实 Webhook

3. **生产环境**：配置真实的 Webhook URL

## 快速测试步骤

1. **启动 Stripe CLI**：
   ```bash
   stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook
   ```

2. **复制 Webhook Secret** 到 `application.properties`

3. **重启后端服务**

4. **前端测试支付**：
   - 使用测试卡号：`4242 4242 4242 4242`
   - 确认支付后，应该很快收到 Webhook
   - 订单状态应该自动更新为"已支付"

## 常见问题

### Q: Webhook 一直收不到？
A: 
- 检查 Stripe CLI 是否运行
- 检查后端日志是否有错误
- 检查 Webhook URL 是否正确

### Q: 订单状态不更新？
A:
- 检查 Webhook 是否收到
- 检查后端日志中的错误信息
- 检查数据库中的订单状态

### Q: 生产环境怎么配置？
A:
- 使用真实的公网 URL
- 配置 HTTPS
- 启用 Webhook 签名验证


