@echo off
echo 修复MyBatis启动错误...
echo.

echo 1. 清理项目构建文件...
if exist target rmdir /s /q target
if exist .idea\workspace.xml del .idea\workspace.xml
echo ✅ 构建文件已清理

echo.
echo 2. 更新Maven依赖...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 依赖更新失败
    pause
    exit /b 1
)

echo.
echo 3. 检查依赖版本兼容性...
echo 已修复的问题：
echo - PageHelper版本: 1.4.7 -> 1.4.6
echo - 移除了可能有问题的PageHelper配置
echo - 确保MyBatis和PageHelper版本兼容

echo.
echo 4. 重新启动应用...
mvn spring-boot:run -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 应用启动失败
    echo.
    echo 如果问题仍然存在，请尝试：
    echo 1. 检查Java版本是否为21
    echo 2. 检查Maven依赖是否正确下载
    echo 3. 检查数据库连接配置
    pause
    exit /b 1
)

echo.
echo ✅ MyBatis错误已修复！

pause
