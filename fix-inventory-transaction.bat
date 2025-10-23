@echo off
echo 修复库存记录表逻辑...
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
echo 3. 启动应用（修复库存记录逻辑）...
echo 修复内容：
echo - 修复锁定库存记录逻辑
echo - 修复扣减库存记录逻辑
echo - 修复解锁库存记录逻辑
echo - 删除available_stock字段引用
echo.
mvn spring-boot:run -s settings.xml

pause
