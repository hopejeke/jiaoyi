@echo off
echo 删除available_stock字段...
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
echo 3. 启动应用（删除available_stock字段）...
echo 修复内容：
echo - 删除available_stock字段
echo - 使用current_stock - locked_stock计算可用库存
echo - 简化库存逻辑
echo.
mvn spring-boot:run -s settings.xml

pause
