@echo off
echo 启动应用并打开测试页面...
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
echo 应用启动后，请访问: http://localhost:8080/test-payment.html
echo.
echo 测试页面功能：
echo 1. 填写订单信息
echo 2. 创建订单
echo 3. 支付订单（需要配置真实的支付宝参数）
echo.
start mvn spring-boot:run -s settings.xml

echo.
echo 等待应用启动...
timeout /t 10 /nobreak > nul

echo.
echo 打开测试页面...
start http://localhost:8080/test-payment.html

pause
