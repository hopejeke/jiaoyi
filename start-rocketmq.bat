@echo off
echo 启动RocketMQ服务...

echo 1. 启动NameServer...
start "RocketMQ NameServer" cmd /k "mqnamesrv.cmd"

echo 等待NameServer启动...
timeout /t 5 /nobreak > nul

echo 2. 启动Broker...
start "RocketMQ Broker" cmd /k "mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true"

echo RocketMQ启动完成！
echo NameServer: localhost:9876
echo 管理控制台: http://localhost:8080 (需要单独下载rocketmq-console)

pause
