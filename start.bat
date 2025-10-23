@echo off
echo 启动电商交易系统...
echo.

echo 检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请先安装JDK 8+
    pause
    exit /b 1
)

echo.
echo 检查Maven环境...
mvn -version
if %errorlevel% neq 0 (
    echo 错误: 未找到Maven环境，请先安装Maven 3.6+
    pause
    exit /b 1
)

echo.
echo 编译项目...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo 错误: 项目编译失败
    pause
    exit /b 1
)

echo.
echo 启动应用...
mvn spring-boot:run -s settings.xml

pause
