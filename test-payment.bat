@echo off
echo 测试支付流程...
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
echo 支付流程测试：
echo 1. 提交订单（锁定库存）
echo 2. 支付订单（调用第三方支付）
echo 3. 支付成功（扣减库存）
echo 4. 支付失败（保持锁定状态）
echo.
mvn spring-boot:run -s settings.xml

pause
