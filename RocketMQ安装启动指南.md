# 🚀 RocketMQ安装启动指南

## 1. 下载RocketMQ

1. 访问 [RocketMQ官网](https://rocketmq.apache.org/download)
2. 下载 `rocketmq-all-4.9.4-bin-release.zip`
3. 解压到 `D:\rocketmq\` 目录

## 2. 配置环境变量

在系统环境变量中添加：
- `ROCKETMQ_HOME=D:\rocketmq`
- 在 `PATH` 中添加 `%ROCKETMQ_HOME%\bin`

## 3. 启动RocketMQ

### 方法1：使用批处理脚本
```bash
# 运行项目根目录下的脚本
start-rocketmq-simple.bat
```

### 方法2：手动启动
```bash
# 1. 启动NameServer
cd D:\rocketmq\bin
mqnamesrv.cmd

# 2. 新开一个命令行窗口，启动Broker
cd D:\rocketmq\bin
mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true
```

## 4. 验证启动

1. 检查端口是否监听：
   ```bash
   netstat -an | findstr :9876  # NameServer端口
   netstat -an | findstr :10911 # Broker端口
   ```

2. 查看日志：
   - NameServer日志：`D:\rocketmq\logs\rocketmqlogs\namesrv.log`
   - Broker日志：`D:\rocketmq\logs\rocketmqlogs\broker.log`

## 5. 启动应用

```bash
mvn spring-boot:run
```

## 6. 测试功能

访问 `http://localhost:8080/order-timeout-rocketmq-test.html` 测试订单超时功能。

## 常见问题

### 问题1：端口被占用
```bash
# 查看端口占用
netstat -ano | findstr :9876
# 结束进程
taskkill /PID <进程ID> /F
```

### 问题2：内存不足
修改 `D:\rocketmq\bin\runbroker.cmd` 和 `D:\rocketmq\bin\runserver.cmd`：
```bash
set "JAVA_OPT=%JAVA_OPT% -Xms256m -Xmx256m"
```

### 问题3：RocketMQ启动失败
1. 确保Java环境正确
2. 检查端口是否被占用
3. 查看日志文件排查问题

## 成功标志

看到以下日志表示启动成功：
```
The Name Server boot success. serializeType=JSON
The broker[broker-a, 192.168.1.100:10911] boot success. serializeType=JSON and name server is localhost:9876
```
