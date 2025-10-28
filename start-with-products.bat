@echo off
echo 启动交易系统（包含商品功能）...
echo.

REM 检查Java是否安装
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：未找到Java，请先安装Java 8或更高版本
    pause
    exit /b 1
)

REM 设置环境变量
set SPRING_PROFILES_ACTIVE=dev
set SERVER_PORT=8080

REM 启动应用
echo 正在启动应用...
java -cp "target/classes;target/lib/*" com.jiaoyi.JiaoyiApplication

pause
