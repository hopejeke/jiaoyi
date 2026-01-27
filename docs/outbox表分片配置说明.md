# Outbox 表分片配置说明

## 一、分片架构概览

### 1.1 物理结构

```
┌─────────────────────────────────────────────────────────────┐
│                    Outbox 表分片架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  数据库层（3个库）：                                           │
│  ├─ jiaoyi_order_0 (ds0)                                     │
│  │  ├─ outbox_00, outbox_01, ..., outbox_31 (32张表)        │
│  ├─ jiaoyi_order_1 (ds1)                                     │
│  │  ├─ outbox_00, outbox_01, ..., outbox_31 (32张表)        │
│  └─ jiaoyi_order_2 (ds2)                                     │
│     ├─ outbox_00, outbox_01, ..., outbox_31 (32张表)        │
│                                                               │
│  总计：3库 × 32表 = 96张物理表                                 │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 分片键

**分片键字段：`store_id`（Long类型）**

- ✅ **使用 `store_id` 作为分片键**（不是 `shard_id`）
- ✅ 与订单表（`orders`）、订单项表（`order_items`）保持一致
- ✅ 确保 outbox 记录与订单数据在同一分片，保证本地事务一致性

---

## 二、分片算法

### 2.1 数据库分片算法（`StoreIdDatabaseShardingAlgorithm`）

**配置位置**：`order-service/src/main/java/com/jiaoyi/order/config/ShardingSphereConfig.java`

```java
// 分库策略配置
tableRule.setDatabaseShardingStrategy(
    new StandardShardingStrategyConfiguration("store_id", "store_id_database")
);
```

**算法逻辑**：

```java
// 第一步：计算 shard_id（固定1024个虚拟桶）
int shardId = hash(store_id) & 1023;  // 结果：0-1023

// 第二步：计算数据库索引
int dsIndex = shardId % 3;  // 结果：0/1/2

// 第三步：路由到对应数据库
String dsName = "ds" + dsIndex;  // 结果：ds0/ds1/ds2
```

**示例**：

| store_id | hash(store_id) | shard_id (hash & 1023) | dsIndex (shard_id % 3) | 路由到数据库 |
|----------|----------------|------------------------|------------------------|--------------|
| 1001     | 123456789      | 789                    | 0                      | ds0          |
| 1002     | 987654321      | 321                    | 0                      | ds0          |
| 1003     | 456789123      | 123                    | 0                      | ds0          |
| 2001     | 111222333      | 333                    | 0                      | ds0          |
| 2002     | 222333444      | 444                    | 0                      | ds0          |
| 3001     | 333444555      | 555                    | 0                      | ds0          |

### 2.2 表分片算法（`StoreIdTableShardingAlgorithm`）

**配置位置**：`order-service/src/main/java/com/jiaoyi/order/config/ShardingSphereConfig.java`

```java
// 分表策略配置
tableRule.setTableShardingStrategy(
    new StandardShardingStrategyConfiguration("store_id", "store_id_table")
);
```

**算法逻辑**：

```java
// 第一步：计算 shard_id（固定1024个虚拟桶）
int shardId = hash(store_id) & 1023;  // 结果：0-1023

// 第二步：计算表索引
int tableIndex = shardId % 32;  // 结果：0-31

// 第三步：生成表名
String tableName = "outbox_" + String.format("%02d", tableIndex);
// 结果：outbox_00, outbox_01, ..., outbox_31
```

**示例**：

| store_id | shard_id | tableIndex (shard_id % 32) | 路由到表 |
|----------|----------|----------------------------|----------|
| 1001     | 789      | 21                         | outbox_21 |
| 1002     | 321      | 1                          | outbox_01 |
| 1003     | 123      | 27                         | outbox_27 |
| 2001     | 333      | 13                         | outbox_13 |
| 2002     | 444      | 28                         | outbox_28 |
| 3001     | 555      | 11                         | outbox_11 |

---

## 三、完整路由示例

### 3.1 插入 Outbox 记录

**业务代码**：`order-service/src/main/java/com/jiaoyi/order/service/OutboxHelper.java`

```java
// 1. 获取 storeId（从订单项中获取）
Long storeId = orderItems.get(0).getStoreId();  // 例如：storeId = 1001

// 2. 计算 shardId（用于扫描优化）
int shardId = ShardUtil.calculateShardId(storeId);  // 例如：shardId = 789

// 3. 写入 outbox（ShardingSphere 自动路由）
outboxService.enqueue(
    "DEDUCT_STOCK_HTTP",
    String.valueOf(orderId),
    payload,
    null, null, null,
    String.valueOf(storeId),  // shardingKey = "1001"
    shardId                   // shardId = 789
);
```

**ShardingSphere 路由过程**：

```
1. 接收到 INSERT INTO outbox (store_id, ...) VALUES (1001, ...)
   ↓
2. 提取分片键：store_id = 1001
   ↓
3. 数据库分片算法：
   - shard_id = hash(1001) & 1023 = 789
   - dsIndex = 789 % 3 = 0
   - 路由到：ds0 (jiaoyi_order_0)
   ↓
4. 表分片算法：
   - tableIndex = 789 % 32 = 21
   - 路由到：outbox_21
   ↓
5. 最终SQL：
   INSERT INTO jiaoyi_order_0.outbox_21 (store_id, ...) VALUES (1001, ...)
```

### 3.2 查询 Outbox 记录

**场景1：精确查询（带 store_id）**

```sql
-- 业务SQL（逻辑表）
SELECT * FROM outbox WHERE store_id = 1001 AND status = 'NEW';

-- ShardingSphere 路由后（物理表）
SELECT * FROM jiaoyi_order_0.outbox_21 WHERE store_id = 1001 AND status = 'NEW';
```

**场景2：范围查询（不带 store_id）**

```sql
-- 业务SQL（逻辑表）
SELECT * FROM outbox WHERE status = 'NEW' AND created_at > '2024-01-01';

-- ShardingSphere 路由后（广播到所有表）
SELECT * FROM jiaoyi_order_0.outbox_00 WHERE status = 'NEW' AND created_at > '2024-01-01';
SELECT * FROM jiaoyi_order_0.outbox_01 WHERE status = 'NEW' AND created_at > '2024-01-01';
...
SELECT * FROM jiaoyi_order_2.outbox_31 WHERE status = 'NEW' AND created_at > '2024-01-01';
-- 共96条SQL（3库 × 32表）
```

---

## 四、关键字段说明

### 4.1 Outbox 实体字段

**位置**：`outbox-starter/src/main/java/com/jiaoyi/outbox/entity/Outbox.java`

```java
public class Outbox {
    /**
     * 分片键（通用字段，业务方可以存任何分片键值，如 merchant_id、store_id 等）
     * 注意：这是业务分片键，ShardingSphere 会根据此字段路由到正确的分片库
     */
    private String shardingKey;  // 例如："1001"
    
    /**
     * 门店ID（用于分片，与订单和商品服务保持一致）
     * 注意：ShardingSphere 使用 store_id 作为分片键进行数据库和表路由
     */
    private Long storeId;  // 例如：1001L
    
    /**
     * 分片ID（用于扫描优化，不再用于分库路由，保留用于兼容）
     * 注意：分库路由现在使用 store_id，shard_id 仅用于扫描时的分片过滤
     */
    private Integer shardId;  // 例如：789（0-1023）
}
```

### 4.2 字段关系

```
shardingKey (String)  ──┐
                         ├──> 解析为 storeId (Long) ──> ShardingSphere 分片路由
storeId (Long) ──────────┘
                         │
                         └──> 计算 shardId (Integer) ──> 扫描优化（可选）
```

---

## 五、分片配置代码

### 5.1 ShardingSphere 配置

**位置**：`order-service/src/main/java/com/jiaoyi/order/config/ShardingSphereConfig.java`

```java
private ShardingTableRuleConfiguration createOutboxTableRule() {
    // 实际数据节点：ds0/1/2 每个库都有 32 张表（outbox_00..outbox_31）
    String actualDataNodes = buildActualDataNodes("outbox", 3, 32);
    // 结果：ds0.outbox_00,ds0.outbox_01,...,ds2.outbox_31
    
    ShardingTableRuleConfiguration tableRule = 
        new ShardingTableRuleConfiguration("outbox", actualDataNodes);
    
    // 使用 store_id 作为分片键（数据库路由）
    tableRule.setDatabaseShardingStrategy(
        new StandardShardingStrategyConfiguration("store_id", "store_id_database")
    );
    
    // 使用 store_id 作为分片键（表路由）
    tableRule.setTableShardingStrategy(
        new StandardShardingStrategyConfiguration("store_id", "store_id_table")
    );
    
    return tableRule;
}
```

### 5.2 分片算法配置

**数据库分片算法**：`StoreIdDatabaseShardingAlgorithm`

```java
Properties dbShardingProps = new Properties();
dbShardingProps.setProperty("strategy", "STANDARD");
dbShardingProps.setProperty("algorithmClassName", 
    "com.jiaoyi.order.config.StoreIdDatabaseShardingAlgorithm");
dbShardingProps.setProperty("ds-count", "3");      // 数据源数量：ds0, ds1, ds2
dbShardingProps.setProperty("ds-prefix", "ds");   // 数据源名称前缀

shardingRuleConfig.getShardingAlgorithms().put("store_id_database",
    new AlgorithmConfiguration("CLASS_BASED", dbShardingProps));
```

**表分片算法**：`StoreIdTableShardingAlgorithm`

```java
Properties tableShardingProps = new Properties();
tableShardingProps.setProperty("strategy", "STANDARD");
tableShardingProps.setProperty("algorithmClassName", 
    "com.jiaoyi.order.config.StoreIdTableShardingAlgorithm");
tableShardingProps.setProperty("table.count.per.db", "32");  // 每库32张表

shardingRuleConfig.getShardingAlgorithms().put("store_id_table",
    new AlgorithmConfiguration("CLASS_BASED", tableShardingProps));
```

---

## 六、分片一致性保证

### 6.1 与订单表保持一致

**关键点**：outbox 表使用与订单表相同的分片键（`store_id`），确保：

1. ✅ **同库事务**：订单和 outbox 记录在同一数据库，支持本地事务
2. ✅ **数据一致性**：订单创建和 outbox 写入在同一事务中完成
3. ✅ **查询效率**：查询订单相关的 outbox 记录时，无需跨库

**示例**：

```java
@Transactional
public void createOrder(Order order, List<OrderItem> orderItems) {
    // 1. 插入订单（路由到 jiaoyi_order_0.orders_21）
    orderMapper.insert(order);
    
    // 2. 插入订单项（路由到 jiaoyi_order_0.order_items_21）
    orderItemMapper.insertBatch(orderItems);
    
    // 3. 写入 outbox（路由到 jiaoyi_order_0.outbox_21）
    outboxService.enqueue(..., String.valueOf(order.getStoreId()), ...);
    
    // ✅ 所有操作在同一数据库（ds0），支持本地事务
}
```

### 6.2 与商品服务保持一致

**关键点**：outbox 表使用与商品服务相同的分片键（`store_id`），确保：

1. ✅ **库存扣减一致性**：订单创建和库存扣减 outbox 在同一分片
2. ✅ **查询效率**：查询商品相关的 outbox 记录时，无需跨库

---

## 七、扫描和清理

### 7.1 OutboxDispatcher 扫描

**位置**：`outbox-starter/src/main/java/com/jiaoyi/outbox/OutboxDispatcher.java`

**扫描策略**：

```java
// 由于 outbox 表已改为按 store_id 分片，无法按 shard_id 路由
// 因此扫描时需要广播到所有表（96张表）

// 扫描 NEW/FAILED 状态的任务
SELECT * FROM outbox 
WHERE status IN ('NEW', 'FAILED') 
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
LIMIT 100;

// ShardingSphere 会广播到所有96张表，然后合并结果
```

**性能优化**：

- ✅ 使用 `LIMIT` 限制每次扫描数量
- ✅ 使用 `next_retry_time` 过滤，避免重复扫描
- ✅ 使用 `FOR UPDATE SKIP LOCKED` 避免并发冲突

### 7.2 OutboxCleanupTask 清理

**位置**：`outbox-starter/src/main/java/com/jiaoyi/outbox/OutboxCleanupTask.java`

**清理策略**：

```java
// 按 shard_id 分批处理（避免长事务）
// 每批处理 32 个 shard_id（即一个表的所有 shard）

for (int shardIdStart = 0; shardIdStart < 1024; shardIdStart += 32) {
    int shardIdEnd = shardIdStart + 32;
    
    // 删除 SENT 状态的记录（按 shard_id 范围）
    DELETE FROM outbox 
    WHERE status = 'SENT' 
      AND shard_id >= ? AND shard_id < ?
      AND completed_at < ?;
    
    // ShardingSphere 会广播到所有表，但 WHERE shard_id 条件会过滤
}
```

---

## 八、总结

### 8.1 分片配置

| 维度 | 配置值 |
|------|--------|
| **分片键** | `store_id` (Long) |
| **数据库数量** | 3个（ds0, ds1, ds2） |
| **每库表数量** | 32张（outbox_00..outbox_31） |
| **总表数量** | 96张（3库 × 32表） |
| **数据库分片算法** | `store_id % 3`（通过 shard_id 计算） |
| **表分片算法** | `store_id % 32`（通过 shard_id 计算） |
| **虚拟桶数量** | 1024个（shard_id: 0-1023） |

### 8.2 关键特性

✅ **与订单表一致**：使用相同的分片键（`store_id`），保证同库事务  
✅ **与商品服务一致**：使用相同的分片键（`store_id`），保证数据一致性  
✅ **可扩展性**：固定1024个虚拟桶，支持未来扩容  
✅ **扫描优化**：使用 `shard_id` 字段进行扫描过滤，减少无效查询  

### 8.3 注意事项

⚠️ **扫描性能**：不带 `store_id` 的查询会广播到所有96张表，需要合理使用 `LIMIT`  
⚠️ **清理策略**：按 `shard_id` 分批清理，避免长事务  
⚠️ **分片键必填**：插入 outbox 时必须提供 `store_id`，否则会报错  

---

## 九、相关文件

- **ShardingSphere 配置**：`order-service/src/main/java/com/jiaoyi/order/config/ShardingSphereConfig.java`
- **数据库分片算法**：`order-service/src/main/java/com/jiaoyi/order/config/StoreIdDatabaseShardingAlgorithm.java`
- **表分片算法**：`order-service/src/main/java/com/jiaoyi/order/config/StoreIdTableShardingAlgorithm.java`
- **Outbox 实体**：`outbox-starter/src/main/java/com/jiaoyi/outbox/entity/Outbox.java`
- **业务使用示例**：`order-service/src/main/java/com/jiaoyi/order/service/OutboxHelper.java`



