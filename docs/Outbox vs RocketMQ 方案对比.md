# Outbox vs RocketMQ 方案对比

## 一、两种方案对比

### 方案 A：使用 Outbox（推荐）

```
下单流程：
1. Redis 预扣减（成功）
2. 创建订单 + 写入 outbox（本地事务，原子性）
   ├─ 订单创建成功
   └─ outbox 记录写入成功
3. 事务提交后，outbox 定时任务扫描
4. 发送 RocketMQ 消息（或直接调用 DB 扣减）
5. 消费者处理：扣减 DB
```

### 方案 B：直接使用 RocketMQ

```
下单流程：
1. Redis 预扣减（成功）
2. 创建订单 + 发送 RocketMQ（非事务）
   ├─ 订单创建成功
   └─ MQ 消息发送成功
3. 消费者处理：扣减 DB
```

---

## 二、关键问题分析

### 问题 1：订单创建失败，但 MQ 消息已发送

| 场景 | Outbox 方案 | RocketMQ 方案 |
|-----|------------|--------------|
| **订单创建失败** | outbox 不会写入（事务回滚） | ❌ MQ 消息可能已发送，导致 DB 扣减但无订单 |
| **处理方式** | 自动回滚，无需处理 | ⚠️ 需要消费者做幂等检查，或补偿机制 |

**结论**：Outbox 更安全 ✅

---

### 问题 2：订单创建成功，但 MQ 消息发送失败

| 场景 | Outbox 方案 | RocketMQ 方案 |
|-----|------------|--------------|
| **MQ 发送失败** | outbox 记录已写入，定时任务会重试 | ❌ 消息丢失，DB 永远不会扣减 |
| **处理方式** | 定时任务自动重试 | ⚠️ 需要手动补偿，或定时任务扫描订单 |

**结论**：Outbox 更可靠 ✅

---

### 问题 3：事务一致性

| 维度 | Outbox 方案 | RocketMQ 方案 |
|-----|------------|--------------|
| **订单 + 消息的原子性** | ✅ 本地事务保证 | ❌ 无法保证（分布式事务复杂） |
| **消息丢失风险** | ✅ 低（DB 持久化） | ⚠️ 高（MQ 可能丢失） |
| **消息重复风险** | ✅ 低（幂等处理） | ⚠️ 高（需要消费者幂等） |

**结论**：Outbox 一致性更好 ✅

---

## 三、性能对比

| 维度 | Outbox 方案 | RocketMQ 方案 |
|-----|------------|--------------|
| **下单响应时间** | 稍慢（需要写 DB） | 更快（直接发 MQ） |
| **吞吐量** | 受限于 DB 写入 | 受限于 MQ 发送 |
| **延迟** | 有延迟（定时扫描） | 延迟更低（实时发送） |

**结论**：RocketMQ 性能稍好，但差距不大 ⚖️

---

## 四、复杂度对比

| 维度 | Outbox 方案 | RocketMQ 方案 |
|-----|------------|--------------|
| **代码复杂度** | 中等（需要维护 outbox） | 简单（直接发 MQ） |
| **运维复杂度** | 中等（需要监控 outbox） | 简单（只需监控 MQ） |
| **问题排查** | 容易（DB 可查） | 较难（MQ 消息难追踪） |

**结论**：RocketMQ 更简单 ✅

---

## 五、推荐方案

### 场景 1：高一致性要求（推荐 Outbox）

**适用场景**：
- 金融、支付场景
- 库存扣减（不能丢失）
- 订单金额计算

**原因**：
- ✅ 保证"订单创建"和"消息发送"的原子性
- ✅ 消息不会丢失（DB 持久化）
- ✅ 问题可追溯（DB 可查）

**实现**：
```java
@Transactional
public Order createOrder(OrderRequest request) {
    // 1. Redis 预扣减
    redisInventoryService.preDeduct(request);
    
    try {
        // 2. 创建订单 + 写入 outbox（原子性）
        Order order = orderService.createOrder(request);
        outboxService.enqueue("DEDUCT_STOCK_DB", orderId, payload);
        return order;
        
    } catch (Exception e) {
        // 3. 失败回滚 Redis
        redisInventoryService.rollbackPreDeduct(orderId, skuId, quantity);
        throw e;
    }
}
```

---

### 场景 2：高吞吐量要求（可用 RocketMQ）

**适用场景**：
- 秒杀场景（极致性能）
- 日志、通知类消息
- 可容忍少量丢失

**原因**：
- ✅ 性能更好（不需要写 DB）
- ✅ 延迟更低（实时发送）
- ⚠️ 需要做好幂等和补偿

**实现**：
```java
public Order createOrder(OrderRequest request) {
    // 1. Redis 预扣减
    redisInventoryService.preDeduct(request);
    
    try {
        // 2. 创建订单
        Order order = orderService.createOrder(request);
        
        // 3. 发送 MQ（非事务，可能失败）
        try {
            rocketMQTemplate.send("DEDUCT_STOCK_TOPIC", message);
        } catch (Exception e) {
            // MQ 发送失败，记录日志，定时任务补偿
            log.error("MQ 发送失败，将补偿", e);
            compensationService.scheduleCompensation(orderId);
        }
        
        return order;
        
    } catch (Exception e) {
        // 4. 失败回滚 Redis
        redisInventoryService.rollbackPreDeduct(orderId, skuId, quantity);
        throw e;
    }
}
```

**补偿机制**：
```java
// 定时任务：扫描已创建但未扣减 DB 的订单
@Scheduled(fixedDelay = 60000)
public void compensateDeductStock() {
    // 1. 查询已创建但未扣减的订单
    List<Order> orders = orderMapper.selectUnprocessedOrders();
    
    for (Order order : orders) {
        // 2. 检查 Redis 是否有预扣记录
        if (redisInventoryService.hasPreDeductLock(order.getId())) {
            // 3. 发送 MQ 或直接扣减 DB
            sendDeductStockMessage(order);
        }
    }
}
```

---

## 六、混合方案（最佳实践）

### 方案：Outbox + RocketMQ

**流程**：
```
1. Redis 预扣减
2. 创建订单 + 写入 outbox（本地事务）
3. Outbox 定时任务 → 发送 RocketMQ
4. MQ 消费者 → 扣减 DB
```

**优势**：
- ✅ 保证事务一致性（outbox）
- ✅ 解耦业务和消息（MQ）
- ✅ 可扩展（可以多个消费者）

**实现**：
```java
// Outbox Handler
public void handleDeductStock(Outbox outbox) {
    DeductStockCommand command = parsePayload(outbox);
    
    // 发送 MQ（如果失败，outbox 会重试）
    rocketMQTemplate.send("DEDUCT_STOCK_TOPIC", message);
    
    // 标记 outbox 为已发送
    outboxService.markSent(outbox.getId());
}

// MQ 消费者
@RocketMQMessageListener(topic = "DEDUCT_STOCK_TOPIC")
public class DeductStockConsumer {
    public void consume(Message message) {
        // 扣减 DB（幂等处理）
        inventoryService.deductStock(command);
    }
}
```

---

## 七、最终推荐

### 对于你的库存系统：**推荐使用 Outbox**

**原因**：
1. ✅ **数据一致性要求高**：库存不能丢失，不能多扣
2. ✅ **已有 Outbox 基础设施**：你的项目已经有 outbox-starter
3. ✅ **可追溯性强**：问题排查容易（DB 可查）
4. ✅ **可靠性高**：消息不会丢失

**性能影响**：
- Outbox 写入是本地事务，性能影响很小（< 5ms）
- 相比 Redis 预扣减的收益（减少 DB 压力），这点开销可以接受

---

## 八、如果一定要用 RocketMQ

### 必须做的保障措施

1. **幂等性检查**（必须）
```java
@RocketMQMessageListener(topic = "DEDUCT_STOCK_TOPIC")
public class DeductStockConsumer {
    public void consume(Message message) {
        // 检查是否已扣减（幂等）
        if (transactionMapper.exists(orderId, skuId, OUT)) {
            return; // 已扣减，直接返回
        }
        
        // 扣减 DB
        inventoryService.deductStock(command);
    }
}
```

2. **补偿机制**（必须）
```java
// 定时任务：扫描已创建但未扣减的订单
@Scheduled(fixedDelay = 60000)
public void compensateDeductStock() {
    // 扫描逻辑...
}
```

3. **监控告警**（必须）
- MQ 消息发送失败率
- DB 扣减失败率
- 补偿任务执行次数

---

## 九、总结

| 方案 | 一致性 | 可靠性 | 性能 | 复杂度 | 推荐度 |
|-----|--------|--------|------|--------|--------|
| **Outbox** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ✅✅✅✅✅ |
| **RocketMQ** | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅✅✅ |
| **Outbox + MQ** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ✅✅✅✅✅ |

**最终建议**：**使用 Outbox**，因为你的项目已经有基础设施，且库存系统对一致性要求高。
