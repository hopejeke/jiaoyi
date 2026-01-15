#!/bin/bash
# Java压测脚本运行器

# 默认配置
BASE_URL="${BASE_URL:-http://localhost:8080}"
THREADS="${THREADS:-10}"
DURATION="${DURATION:-60}"
RAMP_UP="${RAMP_UP:-5}"
TEST_TYPE="${TEST_TYPE:-create_order}"

# 检查JAR文件是否存在
JAR_FILE="target/load-test-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR文件不存在，正在编译..."
    mvn clean package
    if [ $? -ne 0 ]; then
        echo "编译失败，请检查Maven配置"
        exit 1
    fi
fi

# 运行压测
echo "开始压测..."
echo "URL: $BASE_URL"
echo "线程数: $THREADS"
echo "持续时间: ${DURATION}秒"
echo "测试类型: $TEST_TYPE"
echo ""

java -jar "$JAR_FILE" \
    --url "$BASE_URL" \
    --threads "$THREADS" \
    --duration "$DURATION" \
    --ramp-up "$RAMP_UP" \
    --type "$TEST_TYPE"

