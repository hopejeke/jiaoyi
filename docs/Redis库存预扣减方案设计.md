# Redis 库存预扣减方案设计（P7 级别）

## 一、架构设计

### 1.1 整体流程

```
下单请求
  ↓
1. Redis 预扣减（Lua 脚本，原子操作）
  ├─ 成功 → 继续
  └─ 失败 → 返回库存不足
  ↓
2. 创建订单 + 写入 outbox（本地事务）
  ├─ 成功 → 订单创建成功
  └─ 失败 → Redis 回滚（补偿）
  ↓
3. 异步扣减 DB（outbox 消费者）
  ├─ 成功 → 删除 Redis 预扣记录
  └─ 失败 → 重试 + 告警
```

### 1.2 核心原则

- **Redis 作为缓存层**：快速响应，减少 DB 压力
- **MySQL 作为数据源**：最终一致性，保证数据准确
- **Outbox 模式**：保证 Redis → DB 的可靠投递

---

## 二、Redis 数据结构设计

### 2.1 Key 设计

```
库存 Key：
  inventory:stock:{storeId}:{productId}:{skuId}
  值：可用库存数量（Integer）

预扣记录 Key：
  inventory:lock:{orderId}:{skuId}
  值：预扣数量（Integer），TTL = 30分钟（订单超时时间）

库存扣减记录 Key（用于补偿）：
  inventory:deduct:{orderId}:{skuId}
  值：扣减数量（Integer），TTL = 7天
```

### 2.2 Lua 脚本（原子预扣减）

```lua
-- 预扣减库存（原子操作）
-- KEYS[1] = inventory:stock:{storeId}:{productId}:{skuId}
-- KEYS[2] = inventory:lock:{orderId}:{skuId}
-- ARGV[1] = 扣减数量
-- ARGV[2] = 订单ID
-- ARGV[3] = SKU ID

local stockKey = KEYS[1]
local lockKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local orderId = ARGV[2]
local skuId = ARGV[3]

-- 1. 检查是否已预扣过（幂等性）
local existingLock = redis.call('GET', lockKey)
if existingLock then
    return {1, tonumber(existingLock)}  -- 已预扣，返回成功和已扣数量
end

-- 2. 检查可用库存
local currentStock = tonumber(redis.call('GET', stockKey) or 0)
if currentStock < quantity then
    return {0, 0, 'INSUFFICIENT_STOCK', currentStock}  -- 库存不足
end

-- 3. 扣减库存
local newStock = currentStock - quantity
redis.call('SET', stockKey, newStock)

-- 4. 记录预扣（用于回滚）
redis.call('SET', lockKey, quantity, 'EX', 1800)  -- 30分钟过期

-- 5. 记录扣减日志（用于补偿）
local deductKey = 'inventory:deduct:' .. orderId .. ':' .. skuId
redis.call('SET', deductKey, quantity, 'EX', 604800)  -- 7天过期

return {1, quantity, 'SUCCESS', newStock}
```

---

## 三、数据一致性保证

### 3.1 场景 1：Redis 扣减成功，订单创建失败

```java
@Transactional
public Order createOrder(OrderRequest request) {
    // 1. Redis 预扣减
    RedisDeductResult result = redisInventoryService.preDeduct(request);
    if (!result.isSuccess()) {
        throw new InsufficientStockException("库存不足");
    }
    
    try {
        // 2. 创建订单 + 写入 outbox
        Order order = orderService.createOrder(request);
        outboxService.enqueue("DEDUCT_STOCK_DB", orderId, ...);
        return order;
        
    } catch (Exception e) {
        // 3. 订单创建失败，回滚 Redis
        redisInventoryService.rollbackPreDeduct(orderId, skuId, quantity);
        throw e;
    }
}
```

### 3.2 场景 2：Redis 扣减成功，DB 扣减失败

```java
// Outbox Handler（异步扣减 DB）
public void handleDeductStock(Outbox outbox) {
    DeductStockCommand command = parsePayload(outbox);
    
    try {
        // 1. 扣减 DB
        inventoryService.deductStock(command);
        
        // 2. DB 扣减成功，删除 Redis 预扣记录（可选，保留用于对账）
        redisInventoryService.removePreDeductLock(command.getOrderId(), command.getSkuId());
        
    } catch (Exception e) {
        // 3. DB 扣减失败，重试（outbox 自动重试）
        log.error("DB 扣减失败，将重试", e);
        throw e;
    }
}
```

### 3.3 场景 3：Redis 扣减成功，但服务挂了

**补偿机制**：

```java
// 定时任务：扫描 Redis 预扣记录，检查 DB 是否已扣减
@Scheduled(fixedDelay = 60000)  // 每分钟执行
public void reconcileInventory() {
    // 1. 扫描所有预扣记录
    Set<String> lockKeys = redisTemplate.keys("inventory:lock:*");
    
    for (String lockKey : lockKeys) {
        String orderId = extractOrderId(lockKey);
        String skuId = extractSkuId(lockKey);
        
        // 2. 检查 DB 是否已扣减
        InventoryTransaction transaction = transactionMapper.selectByOrderIdAndSkuId(orderId, skuId);
        
        if (transaction != null && transaction.getTransactionType() == OUT) {
            // DB 已扣减，删除 Redis 预扣记录
            redisTemplate.delete(lockKey);
        } else {
            // DB 未扣减，检查订单状态
            Order order = orderService.getOrder(orderId);
            if (order == null || order.getStatus() == CANCELLED) {
                // 订单不存在或已取消，回滚 Redis
                redisInventoryService.rollbackPreDeduct(orderId, skuId, ...);
            }
        }
    }
}
```

---

## 四、防止超卖

### 4.1 Redis 层面

- **Lua 脚本保证原子性**：检查 + 扣减在一个原子操作中
- **单线程模型**：Redis 单线程执行 Lua，无并发问题

### 4.2 DB 层面

- **SQL WHERE 条件**：`WHERE locked_stock >= quantity`（已有）
- **唯一索引幂等**：`(order_id, sku_id)` 防止重复扣减（已有）

### 4.3 数据修复

```sql
-- 如果 Redis 和 DB 不一致，修复脚本
UPDATE inventory i
SET i.current_stock = (
    SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'IN' THEN t.quantity ELSE -t.quantity END), 0)
    FROM inventory_transactions t
    WHERE t.product_id = i.product_id AND t.sku_id = i.sku_id
)
WHERE i.product_id = ? AND i.sku_id = ?;
```

---

## 五、幂等性保证

### 5.1 Redis 层面

- **预扣记录 Key**：`inventory:lock:{orderId}:{skuId}`
- **Lua 脚本检查**：如果已存在，直接返回成功（幂等）

### 5.2 DB 层面

- **唯一索引**：`(order_id, sku_id)` 防止重复扣减（已有）
- **CAS 更新**：`UPDATE ... WHERE transaction_type = 'LOCK'`（已有）

---

## 六、性能优化

### 6.1 Redis 预热

```java
// 启动时，从 DB 加载库存到 Redis
@PostConstruct
public void warmupRedis() {
    List<Inventory> inventories = inventoryMapper.selectAll();
    for (Inventory inv : inventories) {
        String key = "inventory:stock:" + inv.getStoreId() + ":" + inv.getProductId() + ":" + inv.getSkuId();
        redisTemplate.opsForValue().set(key, inv.getCurrentStock() - inv.getLockedStock());
    }
}
```

### 6.2 异步刷新

```java
// DB 扣减成功后，异步更新 Redis 库存
@Async
public void refreshRedisStock(Long productId, Long skuId) {
    Inventory inventory = inventoryMapper.selectByProductIdAndSkuId(productId, skuId);
    String key = "inventory:stock:" + inventory.getStoreId() + ":" + productId + ":" + skuId;
    redisTemplate.opsForValue().set(key, inventory.getCurrentStock() - inventory.getLockedStock());
}
```

---

## 七、监控和告警

### 7.1 关键指标

- Redis 预扣成功率
- DB 扣减成功率
- Redis 和 DB 数据不一致率
- 补偿任务执行次数

### 7.2 告警规则

- Redis 预扣失败率 > 5% → 告警
- DB 扣减失败率 > 1% → 告警
- Redis 和 DB 不一致数量 > 100 → 告警

---

## 八、总结

### 8.1 方案优势

- ✅ **高性能**：Redis 预扣减，响应时间 < 10ms
- ✅ **高可用**：DB 兜底，Redis 挂了也能恢复
- ✅ **数据一致性**：最终一致性 + 补偿机制
- ✅ **防超卖**：Lua 脚本原子性 + DB WHERE 条件

### 8.2 注意事项

- ⚠️ **Redis 数据丢失风险**：需要持久化（AOF + RDB）
- ⚠️ **补偿任务延迟**：需要监控和告警
- ⚠️ **数据修复成本**：需要定期对账

---

## 九、P7 级别要求

### 必须掌握

1. **数据一致性**：最终一致性、补偿机制、对账方案
2. **高并发设计**：Redis 预扣减、异步处理、削峰填谷
3. **容错设计**：回滚机制、重试策略、降级方案
4. **监控告警**：关键指标、异常检测、数据修复

### 加分项

- 分布式锁（Redisson）的使用
- 分库分表下的库存一致性
- 秒杀场景的特殊优化
