@echo off
echo 测试支付宝支付集成...
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
echo 支付宝支付集成测试：
echo 1. 配置支付宝参数（需要真实的应用ID和密钥）
echo 2. 创建支付订单
echo 3. 跳转支付宝支付页面
echo 4. 处理支付回调
echo.
echo ⚠️  注意：需要配置真实的支付宝应用ID和密钥才能正常使用
echo.
mvn spring-boot:run -s settings.xml

pause
