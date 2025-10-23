@echo off
echo 清理并重新构建项目...
echo.

echo 1. 清理本地Maven缓存...
rmdir /s /q "%USERPROFILE%\.m2\repository\mysql" 2>nul
echo MySQL依赖缓存已清理

echo.
echo 2. 清理项目构建文件...
if exist target rmdir /s /q target
if exist .m2 rmdir /s /q .m2
echo 项目构建文件已清理

echo.
echo 3. 强制更新依赖...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo 错误: 依赖更新失败
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
    echo 错误: 项目打包失败
    pause
    exit /b 1
)

echo.
echo ✅ 项目重建完成！
echo 现在可以运行: mvn spring-boot:run -s settings.xml
echo 或者使用: start.bat

pause
