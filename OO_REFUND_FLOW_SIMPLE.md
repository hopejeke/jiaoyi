# OO 项目部分退款流程图（简化版）

## 完整业务流程图

```mermaid
graph TD
    Start([用户发起退款请求]) --> CheckType{退款类型?}
    
    CheckType -->|全额退款| FullRefund[计算全单总额]
    FullRefund --> FullStripe[调用 Stripe 全额退款<br/>reverse_transfer: true]
    FullStripe --> FullUpdate[更新订单状态 REFUNDED<br/>回退全部平台抽成]
    FullUpdate --> FullNotify[通知用户退款成功]
    FullNotify --> End1([结束])
    
    CheckType -->|部分退款| PartialInput[输入退款金额/选择退款商品]
    PartialInput --> ValidateAmount{校验退款金额}
    
    ValidateAmount -->|超过可退余额| Error1[返回错误: 退款金额超过可退余额]
    Error1 --> End2([结束])
    
    ValidateAmount -->|合法| CalculateDetail[计算退款明细]
    
    CalculateDetail --> Calc1[计算退款商品金额]
    CalculateDetail --> Calc2[计算应退税费]
    CalculateDetail --> Calc3[计算应回补抽成<br/>回补抽成 = 退款金额/原总额 × 总抽成]
    CalculateDetail --> CheckDelivery{是否退配送费?}
    
    CheckDelivery -->|是| Calc4[计算配送费退款]
    CheckDelivery -->|否| Calc5[配送费不退]
    
    Calc3 --> CallPayment{支付渠道}
    Calc4 --> CallPayment
    Calc5 --> CallPayment
    
    CallPayment -->|Stripe| StripeRefund[stripe.refunds.create<br/>amount: 退款金额<br/>reverse_transfer: true<br/>自动回补平台抽成]
    CallPayment -->|Alipay| AlipayRefund[alipay.refund<br/>refund_amount: 退款金额]
    
    StripeRefund --> UpdateStatus[更新订单状态]
    AlipayRefund --> UpdateStatus
    
    UpdateStatus --> Update1[更新 payStatus = Partial Refund]
    UpdateStatus --> Update2[更新已退总额<br/>already_refunded += refund_amount]
    UpdateStatus --> Update3[更新订单退款金额<br/>order.refundAmount]
    
    Update3 --> SaveLog[记录财务日志]
    SaveLog --> Log1[记录抽成回补明细<br/>commission_reversal]
    SaveLog --> Log2[记录退款明细<br/>refund_items]
    
    Log2 --> Webhook{Webhook回调}
    Webhook -->|Stripe| StripeCallback[处理 Stripe Webhook<br/>更新退款状态]
    Webhook -->|Alipay| AlipayCallback[处理 Alipay 回调<br/>更新退款状态]
    
    StripeCallback --> NotifyUser[发送推送给用户<br/>您的退款已原路返回]
    AlipayCallback --> NotifyUser
    
    NotifyUser --> UpdateUI[更新前端显示<br/>显示退款状态和金额]
    UpdateUI --> End3([结束])
    
    style Start fill:#e1f5ff
    style CheckType fill:#fff4e1
    style CalculateDetail fill:#e8f5e9
    style CallPayment fill:#fce4ec
    style UpdateStatus fill:#f3e5f5
    style NotifyUser fill:#e0f2f1
```

## 核心代码修改点

### 1. 修改 checkout.ts 的 doRefund 方法

```typescript
// 添加退款金额参数
interface DoRefundParam {
  chargePayment: PaymentDocument;
  refundPayment?: PaymentDocument;
  refundAmount?: number;  // 新增：部分退款金额
  reason?: string;        // 新增：退款原因
}

// 修改 Stripe 退款调用
const refundOptions: Stripe.RefundCreateOptions = {
  charge: payment.extra.id,
  reason: 'requested_by_customer'
};

if (refundAmount && refundAmount > 0) {
  refundOptions.amount = Math.round(refundAmount * 100); // 转换为分
  refundOptions.reverse_transfer = true; // 自动回补平台抽成
}

return stripe.refunds.create(refundOptions);
```

### 2. 抽成回补计算公式

```typescript
// 回补抽成 = (退款金额 / 原订单总额) × 总平台抽成
const commissionReversal = (refundAmount / originalTotal) * totalCommission;
```

### 3. 数据库字段

```typescript
// orders 表新增字段
refundAmount: number;  // 累计退款金额
refundStatus: string;  // 退款状态: 'NONE' | 'PARTIAL' | 'FULL'

// 新增 refunds 表
{
  orderId: string;
  refundAmount: number;
  refundType: 'FULL' | 'PARTIAL';
  status: 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
  commissionReversal: number;  // 抽成回补金额
}
```

## 给 Cursor 的指令模板

```
请帮我实现 OO 项目的部分退款功能，要求：

1. 支持全额退款和部分退款两种类型
2. 部分退款支持按商品退款和按金额退款
3. 计算平台抽成的回补金额：回补抽成 = (退款金额 / 原订单总额) × 总平台抽成
4. Stripe 退款时使用 reverse_transfer: true 自动回补平台抽成
5. 实现幂等性保证（基于 requestNo）
6. 实现并发控制（分布式锁）
7. 通过 Webhook 更新退款状态
8. 更新订单的 refundAmount 字段
9. 如果全额退款，更新订单状态为 REFUNDED

参考文件：OO_REFUND_FLOW.md
```




