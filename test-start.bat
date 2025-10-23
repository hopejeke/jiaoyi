@echo off
echo 测试MyBatis注解方式启动...
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
echo 已修复的问题：
echo - 移除了所有XML映射文件
echo - 使用MyBatis注解方式
echo - 简化了配置
echo.
mvn spring-boot:run -s settings.xml

pause
