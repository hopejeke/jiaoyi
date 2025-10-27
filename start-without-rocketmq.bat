@echo off
echo 启动应用（不包含RocketMQ功能）...

echo 设置环境变量禁用RocketMQ...
set ROCKETMQ_ENABLED=false

echo 启动Spring Boot应用...
mvn spring-boot:run -Dspring-boot.run.arguments="--rocketmq.enabled=false"

pause
