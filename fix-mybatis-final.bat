@echo off
echo 最终修复MyBatis XML映射问题...
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
echo 3. 启动应用（使用MyBatis 3.0.1）...
echo 修复内容：
echo - 降级到MyBatis 3.0.1版本
echo - 使用classpath:mapper/*.xml配置
echo - 检查XML文件是否正确编译
echo.
mvn spring-boot:run -s settings.xml

pause