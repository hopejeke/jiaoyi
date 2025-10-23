@echo off
echo 使用MyBatis XML映射文件启动应用...
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
echo - 使用MyBatis XML映射文件
echo - SQL语句写在XML中
echo - 更符合MyBatis最佳实践
echo.
mvn spring-boot:run -s settings.xml

pause
