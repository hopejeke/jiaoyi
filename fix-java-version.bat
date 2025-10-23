@echo off
echo 修复Java版本兼容性问题...
echo.

echo 1. 检查当前Java版本...
java -version
echo.

echo 2. 检查JAVA_HOME环境变量...
if defined JAVA_HOME (
    echo JAVA_HOME: %JAVA_HOME%
    echo.
    "%JAVA_HOME%\bin\java" -version
) else (
    echo 警告: JAVA_HOME 未设置
)

echo.
echo 3. 清理项目构建文件...
if exist target rmdir /s /q target
if exist .idea\workspace.xml del .idea\workspace.xml
echo ✅ 构建文件已清理

echo.
echo 4. 设置Java 8编译参数...
set MAVEN_OPTS=-Xmx1024m -XX:MaxPermSize=256m
echo ✅ Maven参数已设置

echo.
echo 5. 强制使用Java 8编译...
mvn clean compile -U -s settings.xml -Dmaven.compiler.source=8 -Dmaven.compiler.target=8
if %errorlevel% neq 0 (
    echo ❌ 编译失败，请检查Java版本
    echo.
    echo 解决方案：
    echo 1. 安装Java 8 JDK
    echo 2. 设置JAVA_HOME指向Java 8
    echo 3. 在IntelliJ IDEA中设置Project SDK为Java 8
    echo 4. 运行: mvn clean compile -U
    pause
    exit /b 1
)

echo.
echo ✅ Java版本问题已修复！
echo 项目现在使用Java 8编译

pause
