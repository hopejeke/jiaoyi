# Java 压测工具使用说明

## 环境要求

- Java 11+
- Maven 3.6+

## 编译和打包

```bash
cd scripts/load-test-java
mvn clean package
```

打包完成后，会在 `target` 目录下生成 `load-test-1.0.0.jar` 文件。

## 使用方法

### 方式1: 运行JAR包

```bash
# 基本用法
java -jar target/load-test-1.0.0.jar --url http://localhost:8080 --threads 10 --duration 60

# 测试创建订单（默认）
java -jar target/load-test-1.0.0.jar --type create_order

# 测试支付订单
java -jar target/load-test-1.0.0.jar --type pay_order

# 测试查询订单
java -jar target/load-test-1.0.0.jar --type get_order

# 测试计算价格
java -jar target/load-test-1.0.0.jar --type calculate_price

# 混合测试（随机操作）
java -jar target/load-test-1.0.0.jar --type mixed
```

### 方式2: 使用Maven运行

```bash
# 编译并运行
mvn clean compile exec:java -Dexec.mainClass="com.jiaoyi.loadtest.LoadTestMain" \
    -Dexec.args="--url http://localhost:8080 --threads 20 --duration 120"
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
java -jar target/load-test-1.0.0.jar --threads 10 --duration 60
```

### 2. 中等压测（50线程，5分钟）
```bash
java -jar target/load-test-1.0.0.jar --threads 50 --duration 300
```

### 3. 高并发压测（100线程，10分钟）
```bash
java -jar target/load-test-1.0.0.jar --threads 100 --duration 600
```

### 4. 压力测试（200线程，15分钟）
```bash
java -jar target/load-test-1.0.0.jar --threads 200 --duration 900
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

## 与Python版本对比

### Java版本优势
- 性能更好，适合高并发场景
- 类型安全，编译期检查
- 更好的线程管理和资源控制
- 可以打包成独立JAR，无需安装Python环境

### Python版本优势
- 代码更简洁，易于修改
- 跨平台兼容性好
- 依赖更少

## 注意事项

1. 确保服务已启动并可访问
2. 根据服务器性能调整线程数和持续时间
3. 建议从低并发开始，逐步增加
4. 监控服务器资源使用情况（CPU、内存、数据库连接等）
5. 压测前确保数据库有足够的测试数据

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

## 性能调优

如果需要更高的并发性能，可以调整以下参数：

1. **JVM参数**:
```bash
java -Xms2g -Xmx4g -XX:+UseG1GC -jar target/load-test-1.0.0.jar ...
```

2. **HttpClient连接池**:
在 `LoadTester.java` 中可以调整连接池大小和超时时间

3. **线程数**:
根据CPU核心数和服务器性能调整线程数，建议不超过 CPU核心数 * 2

