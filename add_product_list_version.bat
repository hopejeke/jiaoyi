@echo off
setlocal

REM 尝试查找MySQL安装路径
set "MYSQL_PATH="
if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL_PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if exist "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe" set "MYSQL_PATH=C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
if exist "C:\Program Files (x86)\MySQL\MySQL Server 5.7\bin\mysql.exe" set "MYSQL_PATH=C:\Program Files (x86)\MySQL\MySQL Server 5.7\bin\mysql.exe"

REM 如果找到MySQL，则执行SQL文件
if defined MYSQL_PATH (
    echo Found MySQL at: %MYSQL_PATH%
    echo Executing SQL file: src/main/resources/sql/add_product_list_version_to_stores.sql
    "%MYSQL_PATH%" -h localhost -P 3306 -u root -proot jiaoyi < src/main/resources/sql/add_product_list_version_to_stores.sql
    if %errorlevel% equ 0 (
        echo SQL executed successfully.
    ) else (
        echo Error executing SQL. The column might already exist.
    )
) else (
    echo MySQL not found in common installation paths.
    echo Please ensure MySQL client is in your PATH or manually execute add_product_list_version_to_stores.sql.
)

pause


