# IDEA 直接运行说明

## 方式1: 直接运行（最简单）

1. 打开 `LoadTestMain.java` 文件
2. 点击类名旁边的绿色运行按钮 ▶️
3. 或者右键点击 `main` 方法，选择 "Run 'LoadTestMain.main()'"

**使用默认配置：**
- URL: http://localhost:8080
- 线程数: 10
- 持续时间: 60秒
- 测试类型: create_order

## 方式2: 使用运行配置（推荐）

### 创建运行配置

1. 点击右上角的运行配置下拉框
2. 选择 "Edit Configurations..."
3. 点击左上角的 `+` 号，选择 "Application"
4. 配置如下：
   - **Name**: `LoadTestMain - 创建订单压测`
   - **Main class**: `com.jiaoyi.loadtest.LoadTestMain`
   - **Module**: `load-test`
   - **Program arguments**: `--url http://localhost:8080 --threads 20 --duration 120 --type create_order`

### 预设的运行配置

项目已经包含了几个预设的运行配置（在 `.idea/runConfigurations/` 目录下）：

1. **LoadTestMain - 默认配置**
   - 无参数，使用默认配置

2. **LoadTestMain - 创建订单压测**
   - 20线程，120秒，测试创建订单

3. **LoadTestMain - 混合压测**
   - 50线程，300秒，混合测试

4. **LoadTestMain - 高并发压测**
   - 100线程，600秒，混合测试
   - 包含JVM参数：`-Xms2g -Xmx4g`

### 导入预设配置

如果IDEA没有自动识别预设配置，可以手动导入：

1. 点击运行配置下拉框
2. 选择 "Edit Configurations..."
3. 点击左上角的 `+` 号，选择 "Application"
4. 在 "Main class" 中输入：`com.jiaoyi.loadtest.LoadTestMain`
5. 在 "Program arguments" 中输入参数，例如：
   ```
   --url http://localhost:8080 --threads 20 --duration 120 --type create_order
   ```

## 方式3: 修改默认配置

如果想修改默认配置，可以直接编辑 `LoadTestMain.java` 中的常量：

```java
private static final String DEFAULT_BASE_URL = "http://localhost:8080";
private static final int DEFAULT_THREADS = 10;
private static final int DEFAULT_DURATION = 60;
private static final int DEFAULT_RAMP_UP = 5;
private static final String DEFAULT_TEST_TYPE = "create_order";
```

## 常用参数示例

### 轻量压测
```
--threads 10 --duration 60
```

### 中等压测
```
--threads 50 --duration 300 --type mixed
```

### 高并发压测
```
--threads 100 --duration 600 --type mixed
```

### 测试不同接口
```
--type create_order    # 创建订单
--type pay_order       # 支付订单
--type get_order       # 查询订单
--type calculate_price # 计算价格
--type mixed           # 混合测试
```

## 注意事项

1. **确保服务已启动**：压测前确保订单服务在 `http://localhost:8080` 运行
2. **修改URL**：如果服务在其他地址，使用 `--url` 参数
3. **调整线程数**：根据机器性能调整，建议从10开始逐步增加
4. **监控资源**：压测时注意监控CPU、内存使用情况

## 故障排查

### 如果提示找不到类
- 确保项目已正确导入为Maven项目
- 右键 `pom.xml`，选择 "Add as Maven Project"
- 等待Maven依赖下载完成

### 如果提示找不到依赖
- 检查 `pom.xml` 是否正确
- 在IDEA右侧打开Maven工具窗口，点击刷新按钮
- 或者运行 `mvn clean install`

### 如果连接失败
- 检查服务是否启动：访问 http://localhost:8080/api/orders
- 检查防火墙设置
- 修改URL参数指向正确的服务地址

