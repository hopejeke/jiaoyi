@echo off
REM 执行数据库迁移脚本
REM 使用方法：双击此文件，或命令行执行：execute_migration.bat

echo 正在连接数据库并执行迁移脚本...
echo.

REM 尝试常见的MySQL安装路径
SET MYSQL_PATH=
IF EXIST "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" SET MYSQL_PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe
IF EXIST "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe" SET MYSQL_PATH=C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe
IF EXIST "C:\xampp\mysql\bin\mysql.exe" SET MYSQL_PATH=C:\xampp\mysql\bin\mysql.exe
IF EXIST "D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" SET MYSQL_PATH=D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe

IF "%MYSQL_PATH%"=="" (
    echo 错误：未找到MySQL客户端，请手动指定路径或确保mysql在PATH中
    echo 或者直接使用数据库客户端工具执行：src\main\resources\sql\migration_add_stores.sql
    pause
    exit /b 1
)

echo 使用MySQL路径: %MYSQL_PATH%
echo 正在执行迁移脚本...
echo.

"%MYSQL_PATH%" -h localhost -P 3306 -u root -proot jiaoyi < "src\main\resources\sql\migration_add_stores.sql"

IF %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 迁移脚本执行成功！
    echo ========================================
) ELSE (
    echo.
    echo ========================================
    echo 迁移脚本执行失败！错误代码: %ERRORLEVEL%
    echo ========================================
)

pause

