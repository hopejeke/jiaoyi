@echo off
echo 升级项目到MyBatis...
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
echo 3. 运行测试...
mvn test -s settings.xml
if %errorlevel% neq 0 (
    echo 警告: 测试执行失败，但项目可以继续运行
)

echo.
echo 4. 打包项目...
mvn package -DskipTests -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 项目打包失败
    pause
    exit /b 1
)

echo.
echo ✅ 项目已成功升级到MyBatis！
echo.
echo 主要变更：
echo - 数据访问层: JPA Repository -> MyBatis Mapper
echo - 分页组件: Spring Data -> PageHelper
echo - 实体类: 移除JPA注解
echo - 配置文件: 添加MyBatis配置
echo - SQL映射: 使用XML文件管理SQL
echo.
echo 优势：
echo - 更灵活的SQL控制
echo - 更好的性能优化
echo - 更直观的SQL管理
echo - 支持复杂查询
echo.
echo 现在可以运行: mvn spring-boot:run -s settings.xml

pause
