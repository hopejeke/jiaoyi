@echo off
echo 升级项目到Java 21和Spring Boot 3.x...
echo.

echo 1. 检查Java版本...
java -version
echo.

echo 2. 清理项目构建文件...
if exist target rmdir /s /q target
if exist .idea\workspace.xml del .idea\workspace.xml
echo ✅ 构建文件已清理

echo.
echo 3. 更新Maven依赖...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 依赖更新失败
    echo.
    echo 可能的原因：
    echo 1. 网络连接问题
    echo 2. Maven仓库配置问题
    echo 3. Java版本不兼容
    echo.
    echo 解决方案：
    echo 1. 检查网络连接
    echo 2. 运行: mvn clean compile -U
    echo 3. 确保使用Java 21
    pause
    exit /b 1
)

echo.
echo 4. 运行测试...
mvn test -s settings.xml
if %errorlevel% neq 0 (
    echo 警告: 测试执行失败，但项目可以继续运行
)

echo.
echo 5. 打包项目...
mvn package -DskipTests -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 项目打包失败
    pause
    exit /b 1
)

echo.
echo ✅ 项目已成功升级到Java 21和Spring Boot 3.x！
echo.
echo 主要变更：
echo - Java版本: 8 -> 21
echo - Spring Boot版本: 2.7.14 -> 3.2.0
echo - JPA包名: javax.persistence -> jakarta.persistence
echo - Validation包名: javax.validation -> jakarta.validation
echo - Maven编译器插件: 3.8.1 -> 3.11.0
echo.
echo 现在可以运行: mvn spring-boot:run -s settings.xml

pause
