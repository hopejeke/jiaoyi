# selectByUserId 性能问题说明

## 问题

`OrderMapper#selectByUserId` 查询会遍历所有库和表，性能很差。

## 原因

### SQL 查询
```sql
SELECT * FROM orders WHERE user_id = #{userId} ORDER BY create_time DESC
```

### 分片配置
- **分片键**：`store_id`
- **数据库数量**：3 个（ds0, ds1, ds2）
- **每库表数量**：32 张（orders_00..orders_31）
- **总表数量**：3 × 32 = 96 张表

### 执行情况

由于查询条件中没有 `store_id`（分片键），ShardingSphere 无法确定路由，会**广播查询**到所有数据节点：

```
查询执行：
├── ds0.orders_00: SELECT * FROM orders_00 WHERE user_id = ? ORDER BY create_time DESC
├── ds0.orders_01: SELECT * FROM orders_01 WHERE user_id = ? ORDER BY create_time DESC
├── ...
├── ds0.orders_31: SELECT * FROM orders_31 WHERE user_id = ? ORDER BY create_time DESC
├── ds1.orders_00: SELECT * FROM orders_00 WHERE user_id = ? ORDER BY create_time DESC
├── ...
└── ds2.orders_31: SELECT * FROM orders_31 WHERE user_id = ? ORDER BY create_time DESC

共执行：3 库 × 32 表 = 96 次查询
```

然后 ShardingSphere 会合并所有结果，并按 `create_time DESC` 排序。

## 性能影响

| 方面 | 影响 |
|------|------|
| **查询次数** | 96 次（所有库和表） |
| **数据库压力** | 高（所有库都要查询） |
| **响应时间** | 慢（需要等待所有查询完成） |
| **网络开销** | 大（96 次网络请求） |
| **内存占用** | 高（需要合并大量结果） |

## 优化方案

### 方案1：添加 store_id 条件（推荐）

如果业务场景允许，添加 `store_id` 条件：

```java
// 修改前
List<Order> orders = orderMapper.selectByUserId(userId);

// 修改后（如果知道 storeId）
List<Order> orders = orderMapper.selectByUserIdAndStoreId(userId, storeId);
```

**优点：**
- ✅ 只查询一个库的一张表
- ✅ 性能最好
- ✅ 可以使用索引

**缺点：**
- ❌ 需要知道 `storeId`
- ❌ 如果用户跨多个门店下单，需要多次查询

### 方案2：使用 Elasticsearch（推荐用于复杂查询）

将订单数据同步到 ES，通过 ES 查询：

```java
// 使用 ES 查询
List<Order> orders = orderSearchService.searchByUserId(userId);
```

**优点：**
- ✅ 查询性能好
- ✅ 支持复杂查询条件
- ✅ 不影响数据库性能

**缺点：**
- ❌ 需要维护 ES 数据同步
- ❌ 增加系统复杂度

### 方案3：限制查询范围

如果业务允许，限制查询范围（如最近 N 天）：

```sql
SELECT * FROM orders 
WHERE user_id = #{userId} 
  AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY create_time DESC
```

**优点：**
- ✅ 减少查询数据量
- ✅ 仍然会遍历所有表，但数据量减少

**缺点：**
- ❌ 仍然会遍历所有表
- ❌ 性能提升有限

### 方案4：分页查询

使用分页减少单次查询数据量：

```java
List<Order> orders = orderMapper.selectByUserIdWithPage(userId, pageNum, pageSize);
```

**优点：**
- ✅ 减少单次查询数据量
- ✅ 提升用户体验

**缺点：**
- ❌ 仍然会遍历所有表
- ❌ 性能提升有限

### 方案5：缓存用户订单列表

将用户订单列表缓存到 Redis：

```java
// 查询时先查缓存
List<Order> orders = redisTemplate.get("user:orders:" + userId);
if (orders == null) {
    orders = orderMapper.selectByUserId(userId);
    redisTemplate.set("user:orders:" + userId, orders, 5, TimeUnit.MINUTES);
}
```

**优点：**
- ✅ 减少数据库查询
- ✅ 提升响应速度

**缺点：**
- ❌ 需要维护缓存一致性
- ❌ 首次查询仍然慢

## 最佳实践

### 1. 避免无分片键的查询

**不推荐：**
```java
// 没有分片键，会遍历所有表
List<Order> orders = orderMapper.selectByUserId(userId);
```

**推荐：**
```java
// 有分片键，只查询一个表
List<Order> orders = orderMapper.selectByUserIdAndStoreId(userId, storeId);
```

### 2. 使用 ES 处理复杂查询

对于需要跨分片的查询，使用 ES：

```java
// 复杂查询使用 ES
List<Order> orders = orderSearchService.search(
    OrderSearchRequest.builder()
        .userId(userId)
        .status(status)
        .startTime(startTime)
        .endTime(endTime)
        .build()
);
```

### 3. 业务设计时考虑分片键

在设计业务时，尽量让常用查询包含分片键：

- ✅ 查询用户在某门店的订单：`user_id + store_id`
- ✅ 查询门店订单：`store_id`
- ❌ 查询用户所有订单：只有 `user_id`（会遍历所有表）

## 总结

`selectByUserId` 查询会遍历所有库和表（96 次查询），性能很差。

**建议：**
1. 如果可能，添加 `store_id` 条件
2. 如果必须查询所有订单，使用 ES
3. 如果查询频率不高，可以接受性能问题
4. 考虑使用缓存减少数据库压力


