# 压测脚本使用说明

本项目提供两种压测工具：**Python版本**和**Java版本**

## Python版本

### 环境要求

- Python 3.6+
- requests 库

## 安装依赖

```bash
pip3 install requests
```

## 使用方法

### 方式1: 直接使用Python脚本

```bash
# 基本用法
python3 load_test.py --url http://localhost:8080 --threads 10 --duration 60

# 测试创建订单（默认）
python3 load_test.py --type create_order

# 测试支付订单
python3 load_test.py --type pay_order

# 测试查询订单
python3 load_test.py --type get_order

# 测试计算价格
python3 load_test.py --type calculate_price

# 混合测试（随机操作）
python3 load_test.py --type mixed
```

### 方式2: 使用Shell脚本

```bash
# 设置环境变量
export BASE_URL=http://localhost:8080
export THREADS=20
export DURATION=120
export TEST_TYPE=mixed

# 运行
chmod +x load_test.sh
./load_test.sh
```

## 参数说明

- `--url`: 服务基础URL（默认: http://localhost:8080）
- `--threads`: 并发线程数（默认: 10）
- `--duration`: 压测持续时间，单位秒（默认: 60）
- `--ramp-up`: 预热时间，单位秒（默认: 5）
- `--type`: 测试类型
  - `create_order`: 测试创建订单
  - `pay_order`: 测试支付订单
  - `get_order`: 测试查询订单
  - `calculate_price`: 测试计算价格
  - `mixed`: 混合测试（随机操作）

## 测试场景示例

### 1. 轻量压测（10线程，1分钟）
```bash
python3 load_test.py --threads 10 --duration 60
```

### 2. 中等压测（50线程，5分钟）
```bash
python3 load_test.py --threads 50 --duration 300
```

### 3. 高并发压测（100线程，10分钟）
```bash
python3 load_test.py --threads 100 --duration 600
```

### 4. 压力测试（200线程，15分钟）
```bash
python3 load_test.py --threads 200 --duration 900
```

## 输出说明

脚本会实时输出统计信息，包括：
- 总请求数
- 成功/失败请求数
- 成功率
- 平均响应时间
- P50/P95/P99响应时间
- HTTP状态码分布
- 错误详情

---

## Java版本

### 环境要求

- Java 11+
- Maven 3.6+

### 编译和打包

```bash
cd load-test-java
mvn clean package
```

### 使用方法

```bash
# 基本用法
java -jar load-test-java/target/load-test-1.0.0.jar --url http://localhost:8080 --threads 10 --duration 60

# 测试创建订单
java -jar load-test-java/target/load-test-1.0.0.jar --type create_order

# 混合测试
java -jar load-test-java/target/load-test-1.0.0.jar --type mixed --threads 50 --duration 300
```

详细说明请参考：[load-test-java/README.md](load-test-java/README.md)

---

## 版本对比

| 特性 | Python版本 | Java版本 |
|------|-----------|----------|
| 性能 | 中等 | 高 |
| 并发能力 | 适合中小规模 | 适合大规模压测 |
| 代码简洁性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 类型安全 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 部署便利性 | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 跨平台 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

**推荐使用场景：**
- **Python版本**：快速测试、小规模压测、需要频繁修改测试逻辑
- **Java版本**：大规模压测、生产环境压测、需要更高性能

---

## 注意事项

1. 确保服务已启动并可访问
2. 根据服务器性能调整线程数和持续时间
3. 建议从低并发开始，逐步增加
4. 监控服务器资源使用情况（CPU、内存、数据库连接等）
5. 压测前确保数据库有足够的测试数据

## 测试数据配置

可以在脚本中修改以下测试数据：
- `merchant_ids`: 商户ID列表
- `user_ids`: 用户ID列表
- `product_ids`: 商品ID列表
- `store_ids`: 门店ID列表

## 故障排查

如果遇到连接错误：
1. 检查服务是否启动
2. 检查URL是否正确
3. 检查防火墙/网络设置
4. 检查服务日志

如果成功率较低：
1. 降低并发线程数
2. 增加服务器资源
3. 检查数据库连接池配置
4. 检查是否有性能瓶颈

