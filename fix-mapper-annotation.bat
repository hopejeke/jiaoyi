@echo off
echo 修复MyBatis Mapper注解问题...
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
echo 3. 启动应用（使用注解方式）...
echo 修复内容：
echo - 使用MyBatis注解方式替代XML
echo - 移除XML映射文件配置
echo - 直接在接口中定义SQL
echo.
mvn spring-boot:run -s settings.xml

pause
