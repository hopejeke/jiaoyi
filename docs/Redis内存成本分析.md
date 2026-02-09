# Redis滑动窗口内存成本分析

## 一、实际内存占用计算

### 单条记录的内存占用

Redis Sorted Set存储：
```
key: "merchant:orders:{merchantId}"
member: "{orderId}"        # String，如 "1234567890"
score: timestamp           # Double，如 1704177310123
```

**内存开销分解**：
```
1. Member (订单ID字符串)
   - 长度：10-20字符
   - 占用：~20 bytes

2. Score (时间戳)
   - 类型：double
   - 占用：8 bytes

3. Redis内部结构开销
   - Sorted Set节点指针
   - 跳表索引结构
   - 占用：~30 bytes

总计：~58 bytes/订单
```

---

### 典型场景内存占用

#### 场景1：小型餐饮平台
```
商户数量：100个
时间窗口：10分钟
高峰期订单：每商户10分钟内平均50单
保留时长：5小时（时间窗口的2倍 + buffer）

计算：
- 每个商户窗口内订单：50单
- 但5小时内订单：50 * (5*60/10) = 1,500单/商户
- 总订单数：100商户 * 1,500单 = 150,000单
- 总内存：150,000 * 58 bytes ≈ 8.3 MB

结论：8.3 MB
```

#### 场景2：中型平台（美团级别单个城市）
```
商户数量：1,000个
时间窗口：10分钟
高峰期订单：每商户10分钟内平均100单
保留时长：5小时

计算：
- 每个商户5小时订单：100 * 30 = 3,000单/商户
- 总订单数：1,000 * 3,000 = 3,000,000单
- 总内存：3,000,000 * 58 bytes ≈ 165 MB

结论：165 MB
```

#### 场景3：超大平台（美团全国）
```
商户数量：100,000个（10万商户）
时间窗口：10分钟
高峰期订单：每商户10分钟内平均50单
保留时长：5小时

计算：
- 每个商户5小时订单：50 * 30 = 1,500单/商户
- 总订单数：100,000 * 1,500 = 150,000,000单
- 总内存：150,000,000 * 58 bytes ≈ 8.3 GB

结论：8.3 GB
```

---

## 二、成本对比分析

### 方案A：Redis滑动窗口

| 规模 | 内存占用 | 云服务成本/月 | 响应时间 | QPS |
|-----|---------|--------------|---------|-----|
| 小型（100商户） | 8.3 MB | 几乎为0 | <5ms | 20,000 |
| 中型（1K商户） | 165 MB | ~$5 | <5ms | 20,000 |
| 大型（10万商户） | 8.3 GB | ~$50 | <5ms | 20,000 |

**阿里云Redis价格参考**：
- 1GB内存：约$10/月
- 8GB内存：约$50/月
- 16GB内存：约$80/月

---

### 方案B：DB查询（当前方案）

| 指标 | 成本 |
|-----|-----|
| **响应时间** | 100ms（慢20倍） |
| **QPS上限** | ~1,000 |
| **DB资源消耗** | 高（CPU、IO、连接数） |
| **可能需要的优化** | 加索引、分库分表、读写分离 |

**隐性成本**：
1. **数据库扩容成本**：
   - 高峰期DB压力大，可能需要升级配置
   - RDS 4C8G → 8C16G：约$200/月 → $600/月
   - 增量成本：**$400/月**

2. **慢查询影响其他业务**：
   - 订单查询慢影响支付、退款等其他查询
   - 可能需要独立只读实例：**$300/月**

3. **开发时间成本**：
   - 如果DB顶不住，需要优化（分库分表）
   - 开发成本：1-2周 * 人力成本

**总成本：$700+/月 + 开发成本**

---

## 三、性价比结论

### 小型项目（<1000商户）

| 方案 | 内存成本 | DB成本 | 总成本 | 推荐度 |
|-----|---------|-------|--------|-------|
| Redis滑动窗口 | ~$5 | 不增加 | **$5** | ⭐⭐⭐⭐⭐ |
| DB查询 | $0 | 可能+$200 | **$200** | ⭐⭐ |

**结论：Redis方案便宜40倍**

---

### 中大型项目（1000-10万商户）

| 方案 | Redis成本 | DB成本节省 | 净成本 | 推荐度 |
|-----|----------|-----------|-------|-------|
| Redis滑动窗口 | $50 | -$400 (避免DB扩容) | **节省$350/月** | ⭐⭐⭐⭐⭐ |
| DB查询 | $0 | +$400 | **$400/月** | ⭐ |

**结论：Redis方案反而省钱**

---

## 四、如果还是觉得"贵"，优化方案

### 优化1：缩短保留时长 ⭐⭐⭐⭐⭐

**当前**：保留5小时（防止过早删除）
**优化**：保留1小时（时间窗口通常≤30分钟）

```java
// RedisSlidingWindowCounter.java
private static final int DEFAULT_EXPIRE_HOURS = 1;  // 改为1小时

// 内存占用立即降低80%
// 场景2：165 MB → 33 MB
// 场景3：8.3 GB → 1.7 GB
```

**收益**：
- 内存降低80%
- 成本：$50/月 → $10/月
- 零风险

---

### 优化2：只保留活跃商户 ⭐⭐⭐⭐

**现状**：所有商户都存Redis
**优化**：只有启用高峰拒单的商户才存

```java
public void recordOrder(String merchantId, Long orderId) {
    // 检查商户是否启用高峰拒单
    if (!merchantCapabilityConfigMapper.isPeakHourEnabled(merchantId)) {
        return;  // 未启用，不记录
    }

    // 正常记录
    ...
}
```

**假设只有20%商户启用**：
- 内存：165 MB → 33 MB（降低80%）
- 成本：$50/月 → $10/月

---

### 优化3：采样统计（精度换成本） ⭐⭐⭐

**思路**：不记录每一单，只记录部分订单

```java
public void recordOrder(String merchantId, Long orderId) {
    // 采样率：10%（随机记录10%的订单）
    if (orderId % 10 != 0) {
        return;
    }

    // 记录到Redis
    ...
}

// 统计时乘以采样倍数
public int countInWindow(String merchantId, int windowMinutes) {
    int sampledCount = slidingWindowCounter.countInWindow(...);
    return sampledCount * 10;  // 乘以采样倍数
}
```

**收益**：
- 内存降低90%：165 MB → 16.5 MB
- 成本：$50/月 → $5/月
- 精度损失：±10%（对高峰拒单影响很小）

---

### 优化4：分层存储（终极方案） ⭐⭐⭐⭐

**思路**：热数据Redis，冷数据DB

```java
public int countInWindow(String merchantId, int windowMinutes) {
    // 1. 最近10分钟：Redis（热数据）
    int recentCount = countFromRedis(merchantId, 10);

    // 2. 10分钟以上：DB（冷数据，缓存结果）
    if (windowMinutes > 10) {
        int historicalCount = countFromDB(merchantId, 10, windowMinutes);
        return recentCount + historicalCount;
    }

    return recentCount;
}
```

**收益**：
- Redis只存10分钟数据
- 内存降低70%：165 MB → 50 MB
- 响应时间仍然<10ms
- 成本：$50/月 → $15/月

---

## 五、超轻量级替代方案（如果真的在意成本）

### 方案：Guava Cache本地滑动窗口

**适用场景**：单实例部署，商户数<1000

```java
@Service
public class LocalSlidingWindowCounter {

    // 本地缓存：商户 -> 订单列表
    private final Cache<String, ConcurrentLinkedDeque<OrderRecord>> cache;

    public LocalSlidingWindowCounter() {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(1000)  // 最多1000个商户
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    }

    public void recordOrder(String merchantId, Long orderId) {
        ConcurrentLinkedDeque<OrderRecord> deque = cache.get(merchantId,
            k -> new ConcurrentLinkedDeque<>());

        deque.add(new OrderRecord(orderId, System.currentTimeMillis()));

        // 限制队列长度（防止内存溢出）
        if (deque.size() > 500) {
            deque.pollFirst();
        }
    }

    public int countInWindow(String merchantId, int windowMinutes) {
        ConcurrentLinkedDeque<OrderRecord> deque = cache.getIfPresent(merchantId);
        if (deque == null) {
            return 0;
        }

        long windowStart = System.currentTimeMillis() - windowMinutes * 60 * 1000L;
        return (int) deque.stream()
            .filter(r -> r.timestamp >= windowStart)
            .count();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class OrderRecord {
        private Long orderId;
        private long timestamp;
    }
}
```

**优缺点**：
- ✅ 零Redis成本
- ✅ 响应时间<5ms
- ❌ 单实例限制（多实例数据不同步）
- ❌ 重启丢失数据

**适合**：项目初期、单实例部署、成本敏感

---

## 六、最终建议

### 根据项目规模选择

| 商户数 | 推荐方案 | 成本 | 理由 |
|-------|---------|------|------|
| <100 | 本地缓存 | $0 | 单实例够用，零成本 |
| 100-1K | Redis滑动窗口 + 优化1 | $5-10/月 | 性价比最高 |
| 1K-10万 | Redis滑动窗口 + 优化2+4 | $15-30/月 | 避免DB扩容，反而省钱 |
| >10万 | Redis + 采样/分片 | $50-100/月 | 但节省DB成本$400+/月 |

---

## 七、面试时如何回答成本问题

### 如果面试官问："Redis内存成本会不会太高？"

**推荐回答**：

> "这是个好问题，我也考虑过成本。实际计算下来：
>
> 1. **内存占用很小**：
>    - 1000个商户，每个商户峰值100单
>    - 总内存：~165 MB
>    - 阿里云Redis成本：约$5-10/月
>
> 2. **对比DB查询的隐性成本**：
>    - DB频繁查询可能导致慢查询，影响其他业务
>    - 高峰期可能需要升级DB配置：$200-400/月
>    - **Redis方案反而省钱**
>
> 3. **如果真的在意成本，有几个优化方案**：
>    - 缩短TTL：1小时足够（内存降80%）
>    - 只记录启用高峰拒单的商户（内存降80%）
>    - 采样统计：记录10%订单（内存降90%，精度损失可接受）
>
> 4. **权衡结果**：
>    - 性能提升20倍
>    - 每月成本$5-50
>    - 避免DB扩容节省$400+/月
>    - **ROI（投资回报率）非常高**"

---

## 八、成本优化决策树

```
是否在意Redis成本？
├─ 否 → 直接用Redis滑动窗口（推荐）
│
└─ 是 → 商户数多少？
    ├─ <100 → 用本地缓存（零成本）
    │
    ├─ 100-1K → Redis + 优化1（缩短TTL）
    │           成本：$5/月
    │
    └─ >1K → Redis + 优化2+4（只存活跃商户+分层存储）
             成本：$15-30/月
             但节省DB扩容成本：$400+/月
             净收益：$370+/月
```

---

## 总结

**核心观点**：

1. ❌ **"Redis贵"是个误区**
   - 实际成本：$5-50/月（看规模）
   - DB扩容成本：$400+/月
   - **Redis反而省钱**

2. ✅ **如果真的在意成本**
   - 小项目：用本地缓存（$0）
   - 中项目：Redis + 缩短TTL（$5-10/月）
   - 大项目：Redis仍然比DB扩容便宜

3. 🎯 **面试加分点**
   - 考虑了成本问题（成本意识）
   - 计算了实际数据（量化思维）
   - 给出了多个优化方案（工程能力）
   - 做了ROI分析（业务思维）

**结论：不贵，反而省钱。** 💰
