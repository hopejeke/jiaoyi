@echo off
echo 修复Maven依赖问题...
echo.

echo 1. 清理MySQL依赖缓存...
rmdir /s /q "%USERPROFILE%\.m2\repository\mysql" 2>nul
echo ✅ MySQL依赖缓存已清理

echo.
echo 2. 清理项目target目录...
if exist target rmdir /s /q target
echo ✅ 项目构建文件已清理

echo.
echo 3. 强制更新依赖...
mvn dependency:purge-local-repository -DmanualInclude="mysql:mysql-connector-java"
echo ✅ 依赖缓存已清理

echo.
echo 4. 重新下载依赖...
mvn clean compile -U -s settings.xml
if %errorlevel% neq 0 (
    echo ❌ 依赖下载失败，尝试其他方法...
    echo.
    echo 5. 使用中央仓库重新下载...
    mvn clean compile -U -Dmaven.repo.remote=https://repo1.maven.org/maven2
    if %errorlevel% neq 0 (
        echo ❌ 仍然失败，请检查网络连接
        pause
        exit /b 1
    )
)

echo.
echo ✅ Maven依赖问题已修复！
echo 现在可以正常运行项目了

pause
