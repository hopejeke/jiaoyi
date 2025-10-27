#!/bin/bash

echo "启动RocketMQ服务..."

echo "1. 启动NameServer..."
nohup sh mqnamesrv > namesrv.log 2>&1 &

echo "等待NameServer启动..."
sleep 5

echo "2. 启动Broker..."
nohup sh mqbroker -n localhost:9876 autoCreateTopicEnable=true > broker.log 2>&1 &

echo "RocketMQ启动完成！"
echo "NameServer: localhost:9876"
echo "管理控制台: http://localhost:8080 (需要单独下载rocketmq-console)"
echo "日志文件: namesrv.log, broker.log"
