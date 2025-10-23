@echo off
echo 修复循环依赖问题...
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
echo 修复内容：
echo - 移除OrderService对PaymentService的依赖
echo - PaymentService直接操作OrderMapper更新订单状态
echo - OrderController直接注入PaymentService
echo.
mvn spring-boot:run -s settings.xml

pause
