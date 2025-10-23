@echo off
echo 测试MyBatis XML映射文件...
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
echo 3. 启动应用（使用XML映射文件）...
echo 配置说明：
echo - MyBatis 3.0.3版本
echo - 使用XML映射文件
echo - 启用SQL日志输出
echo.
mvn spring-boot:run -s settings.xml

pause
