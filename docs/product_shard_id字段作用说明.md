# product_shard_id 字段作用说明

## 问题

既然已经有了 `store_id` 用于分表，为什么还需要 `product_shard_id` 字段？

## 核心原因

`product_shard_id` 是一个**虚拟桶（Virtual Bucket）**设计，用于实现**灵活扩容**和**查询优化**。

## 1. 虚拟桶设计（Virtual Bucket）

### 设计理念

```
store_id (业务键)
    ↓ hash(store_id) & 1023
product_shard_id (0-1023，虚拟桶)
    ↓ 通过路由表映射
物理库/表 (可动态调整)
```

**核心思想：**
- `product_shard_id` = `hash(store_id) & 1023`（固定 1024 个虚拟桶）
- 物理库/表的映射关系存储在 `product_shard_bucket_route` 路由表中
- **扩容时只需修改路由表，无需修改数据**

### 为什么需要虚拟桶？

**场景：从 3 个库扩容到 6 个库**

**如果没有虚拟桶（直接使用 store_id）：**
```
旧路由：store_id % 3 -> ds0/ds1/ds2
新路由：store_id % 6 -> ds0/ds1/ds2/ds3/ds4/ds5
问题：需要重新计算所有数据的分片，数据迁移量大
```

**使用虚拟桶：**
```
旧路由：product_shard_id % 3 -> ds0/ds1/ds2
新路由：修改路由表，将部分 product_shard_id 映射到新库
优势：只需迁移部分虚拟桶的数据，数据迁移量可控
```

## 2. ShardingSphere 分片键

### 商品表等（直接使用 product_shard_id）

```java
// ShardingSphereConfig.java
tableRule.setDatabaseShardingStrategy(
    new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_database"));
tableRule.setTableShardingStrategy(
    new StandardShardingStrategyConfiguration("product_shard_id", "product_shard_id_table"));
```

**为什么需要存储 `product_shard_id`？**

1. **ShardingSphere 需要字段存在**
   - ShardingSphere 在执行 SQL 时，需要从 SQL 中提取分片键的值
   - 如果字段不存在，无法提取分片键，无法路由

2. **查询性能优化**
   ```sql
   -- 如果字段存在，可以直接使用索引
   SELECT * FROM store_products WHERE product_shard_id = 123;
   
   -- 如果字段不存在，需要计算哈希（无法使用索引）
   SELECT * FROM store_products WHERE hash(store_id) & 1023 = 123;
   ```

3. **索引优化**
   ```sql
   -- 可以在 product_shard_id 上建索引
   CREATE INDEX idx_product_shard_id ON store_products(product_shard_id);
   
   -- 复合索引优化查询
   CREATE INDEX idx_shard_status ON store_products(product_shard_id, status);
   ```

### Outbox 表（使用 store_id，但存储 product_shard_id）

```java
// Outbox 表使用 store_id 作为分片键
tableRule.setDatabaseShardingStrategy(
    new StandardShardingStrategyConfiguration("store_id", "store_id_database"));
tableRule.setTableShardingStrategy(
    new StandardShardingStrategyConfiguration("store_id", "store_id_table"));

// 但内部会计算 product_shard_id
int productShardId = ProductShardUtil.calculateProductShardId(storeId);
```

**为什么 Outbox 表也存储 `product_shard_id`？**

1. **查询优化**：用于 outbox claim 查询的索引
   ```sql
   -- idx_claim 索引包含 product_shard_id
   CREATE INDEX idx_claim ON outbox(
       product_shard_id, status, next_retry_time, lock_until, id
   );
   ```

2. **扫描优化**：按分片扫描，避免全表扫描
   ```java
   // OutboxDispatcher 按 product_shard_id 扫描
   for (int shardId = 0; shardId < shardCount; shardId++) {
       // 只扫描该分片的任务
   }
   ```

## 3. 两种分片方式的对比

### 方式一：直接使用 product_shard_id（商品表、库存表）

**优点：**
- ✅ ShardingSphere 直接使用字段值，性能最好
- ✅ 查询时可以直接使用 `product_shard_id` 作为条件
- ✅ 索引优化效果好

**缺点：**
- ❌ 插入时必须先计算并设置 `product_shard_id`

**使用场景：**
- `store_products`（商品表）
- `product_sku`（SKU表）
- `inventory`（库存表）
- `inventory_transactions`（库存变动记录表）

### 方式二：使用 store_id，存储 product_shard_id（Outbox 表）

**优点：**
- ✅ 业务层只需要 `store_id`，更直观
- ✅ ShardingSphere 自动计算 `product_shard_id`
- ✅ 仍然可以存储 `product_shard_id` 用于查询优化

**缺点：**
- ❌ ShardingSphere 需要计算哈希（但性能影响很小）

**使用场景：**
- `outbox`（Outbox 表）

## 4. 为什么不能只用 store_id？

### 问题1：无法灵活扩容

如果直接使用 `store_id % 3` 分库：
- 扩容到 6 个库时，需要重新计算所有数据的分片
- 数据迁移量大，风险高

使用 `product_shard_id`：
- 固定 1024 个虚拟桶
- 扩容时只需调整路由映射
- 数据迁移量可控

### 问题2：查询性能差

```sql
-- 没有 product_shard_id 字段
SELECT * FROM store_products 
WHERE hash(store_id) & 1023 = 123;  -- 无法使用索引，全表扫描

-- 有 product_shard_id 字段
SELECT * FROM store_products 
WHERE product_shard_id = 123;  -- 可以使用索引，性能好
```

### 问题3：无法建复合索引

```sql
-- 无法建索引（因为 hash(store_id) 不是字段）
CREATE INDEX idx_hash_store_status ON store_products(hash(store_id), status);  -- ❌ 错误

-- 可以建索引（product_shard_id 是字段）
CREATE INDEX idx_shard_status ON store_products(product_shard_id, status);  -- ✅ 正确
```

## 5. 实际使用示例

### 商品表查询

```java
// 查询某个分片的商品
List<StoreProduct> products = storeProductMapper.selectByShardId(productShardId);

// SQL（使用索引）
SELECT * FROM store_products_00 
WHERE product_shard_id = 123  -- 使用 idx_product_shard_id 索引
```

### Outbox 表查询

```java
// Claim 查询（使用复合索引）
SELECT id FROM outbox_00
WHERE product_shard_id = 123 
  AND status = 'NEW'
  AND next_retry_time <= NOW()
FOR UPDATE SKIP LOCKED
LIMIT 10;

-- 使用 idx_claim(product_shard_id, status, next_retry_time, lock_until, id) 索引
```

## 6. 总结

| 方面 | 只用 store_id | 使用 product_shard_id |
|------|--------------|---------------------|
| **扩容灵活性** | ❌ 需要重新计算所有数据 | ✅ 只需调整路由映射 |
| **查询性能** | ❌ 无法使用索引 | ✅ 可以使用索引 |
| **索引优化** | ❌ 无法建复合索引 | ✅ 可以建复合索引 |
| **ShardingSphere** | ⚠️ 需要计算哈希 | ✅ 直接使用字段值 |
| **存储空间** | ✅ 节省一个字段 | ❌ 多一个字段（但值得） |

**结论：**

`product_shard_id` 字段虽然可以从 `store_id` 计算，但**存储这个字段是必要的**，因为：

1. ✅ **虚拟桶设计**：支持灵活扩容，无需大规模数据迁移
2. ✅ **查询性能**：可以使用索引，避免全表扫描
3. ✅ **索引优化**：可以建复合索引，优化复杂查询
4. ✅ **ShardingSphere**：作为分片键，直接使用字段值性能更好

**存储空间成本很小（一个 INT 字段），但带来的收益很大！**


