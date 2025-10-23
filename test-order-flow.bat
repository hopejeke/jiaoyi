@echo off
echo 测试完整订单流程...
echo.

echo 1. 清理项目...
if exist target rmdir /s /q target
echo ✅ 清理完成

echo.
echo 2. 编译项目...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 编译失败
    pause
    exit /b 1
)

echo.
echo 3. 启动应用...
echo 完整订单流程：
echo 1. 提交订单（锁定库存）
echo 2. 支付订单（扣减库存）
echo 3. 取消订单（解锁库存）
echo.
mvn spring-boot:run -s settings.xml

pause
