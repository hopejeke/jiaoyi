#!/bin/bash
# 压测脚本包装器

# 默认配置
BASE_URL="${BASE_URL:-http://localhost:8080}"
THREADS="${THREADS:-10}"
DURATION="${DURATION:-60}"
RAMP_UP="${RAMP_UP:-5}"
TEST_TYPE="${TEST_TYPE:-create_order}"

# 检查Python是否安装
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到 python3，请先安装 Python 3"
    exit 1
fi

# 检查requests库是否安装
python3 -c "import requests" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "正在安装 requests 库..."
    pip3 install requests
fi

# 运行压测
echo "开始压测..."
echo "URL: $BASE_URL"
echo "线程数: $THREADS"
echo "持续时间: ${DURATION}秒"
echo "测试类型: $TEST_TYPE"
echo ""

python3 load_test.py \
    --url "$BASE_URL" \
    --threads "$THREADS" \
    --duration "$DURATION" \
    --ramp-up "$RAMP_UP" \
    --type "$TEST_TYPE"

