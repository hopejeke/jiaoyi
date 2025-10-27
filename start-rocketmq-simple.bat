@echo off
echo 启动RocketMQ服务...

echo 1. 启动NameServer...
start "RocketMQ NameServer" cmd /k "cd /d D:\rocketmq\bin && mqnamesrv.cmd"

echo 等待NameServer启动...
timeout /t 3 /nobreak > nul

echo 2. 启动Broker...
start "RocketMQ Broker" cmd /k "cd /d D:\rocketmq\bin && mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true"

echo RocketMQ启动完成！
echo 请等待几秒钟让服务完全启动，然后运行应用

pause
