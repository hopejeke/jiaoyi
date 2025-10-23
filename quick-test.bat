@echo off
echo 快速测试支付功能...
echo.

echo 1. 编译项目...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 编译失败
    pause
    exit /b 1
)

echo.
echo 2. 启动应用...
echo 应用启动后，请访问: http://localhost:8080/test-payment.html
echo.
echo 测试步骤：
echo 1. 填写订单信息（用户ID、收货人、商品等）
echo 2. 点击"创建订单"按钮
echo 3. 点击"支付订单"按钮
echo 4. 跳转到支付宝支付页面（需要配置真实参数）
echo.
echo ⚠️  注意：需要配置真实的支付宝应用ID和密钥才能正常支付
echo.

start mvn spring-boot:run -s settings.xml

echo.
echo 等待应用启动...
timeout /t 15 /nobreak > nul

echo.
echo 打开测试页面...
start http://localhost:8080/test-payment.html

echo.
echo 测试页面已打开，请按照页面提示进行测试
pause
