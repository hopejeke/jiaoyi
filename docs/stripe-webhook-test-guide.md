# Stripe Webhook 测试指南

## 当前状态

从 Stripe Dashboard 看到：
- ✅ Webhook 已配置：`https://dorotha-gloomful-elfrieda.ngrok-free.dev/api/payment/stripe/webhook`
- ✅ 状态：使用中
- ❌ 事件数：0（没有事件被触发）

## 测试方法

### 方法 1：使用 Dashboard 发送测试事件（最简单）

1. 在 Stripe Dashboard 的 Webhook 页面
2. 点击右上角的 **"发送测试事件"**（Send test event）按钮
3. 选择事件类型：`payment_intent.succeeded`
4. 点击发送

**检查：**
- Dashboard 应该显示事件已发送
- 后端日志应该显示：`========== 收到 Stripe Webhook 回调 ==========`
- ngrok 界面（http://localhost:4040）应该显示请求

### 方法 2：前端真实支付测试

1. **确保后端服务运行**：`http://localhost:8082/actuator/health`
2. **确保 ngrok 运行**：检查 `tasklist | findstr ngrok`
3. **前端测试支付**：
   - 访问信用卡支付页面
   - 使用测试卡号：`4242 4242 4242 4242`
   - 完成支付
4. **检查 Dashboard**：
   - 应该看到 `payment_intent.succeeded` 事件
   - 如果显示失败（红色），点击查看错误详情

### 方法 3：使用 Stripe CLI（推荐用于开发）

```bash
# 1. 启动本地监听器
stripe listen --forward-to http://localhost:8082/api/payment/stripe/webhook

# 2. 在另一个终端触发测试事件
stripe trigger payment_intent.succeeded
```

**注意：** 如果使用 Stripe CLI，需要使用 CLI 显示的 Webhook Secret，不是 Dashboard 中的。

## 排查步骤

### 1. 检查 ngrok 是否运行

```bash
# 检查进程
tasklist | findstr ngrok

# 访问 ngrok Web 界面
http://localhost:4040
```

### 2. 检查后端服务

```bash
# 访问健康检查
http://localhost:8082/actuator/health
```

### 3. 检查 Webhook Secret

确保 `application.properties` 中的 Secret 与 Dashboard 中的一致：
```properties
stripe.webhook-secret=whsec_OzfW3gtDLLkqFW7mdQ2LQz1wb445xAlI
```

### 4. 查看后端日志

重启后端服务后，查看日志中是否有：
```
========== 收到 Stripe Webhook 回调 ==========
```

### 5. 检查 Dashboard 事件详情

如果事件显示失败（红色）：
1. 点击事件查看详情
2. 查看错误信息
3. 常见错误：
   - `404 Not Found`：URL 路径错误
   - `500 Internal Server Error`：后端处理出错
   - `Timeout`：后端响应超时

## 常见问题

### Q: Dashboard 显示 0 个事件？

**A:** 可能原因：
1. 没有进行支付测试
2. Payment Intent 创建失败
3. 前端没有正确确认支付
4. ngrok 未运行，Webhook 无法送达

**解决：**
1. 使用 Dashboard 的"发送测试事件"功能
2. 检查前端支付流程
3. 检查后端日志

### Q: 事件显示失败？

**A:** 检查：
1. ngrok 是否运行
2. 后端服务是否运行
3. Webhook URL 是否正确
4. 后端日志中的错误信息

### Q: 收到 Webhook 但订单状态不更新？

**A:** 检查：
1. 后端日志中是否有"支付成功处理完成"
2. Payment Intent 的 metadata 中是否有 `orderId`
3. 数据库中支付记录是否存在

## 快速测试清单

- [ ] ngrok 正在运行
- [ ] 后端服务运行在 8082 端口
- [ ] Webhook Secret 已配置（与 Dashboard 一致）
- [ ] 使用 Dashboard 的"发送测试事件"测试
- [ ] 后端日志显示收到 Webhook
- [ ] Dashboard 显示事件成功（绿色）

## 下一步

1. **立即测试**：使用 Dashboard 的"发送测试事件"功能
2. **检查日志**：查看后端是否收到 Webhook
3. **如果收到**：说明配置正确，可以进行真实支付测试
4. **如果收不到**：检查 ngrok 和后端服务












