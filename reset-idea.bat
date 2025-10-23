@echo off
echo 重置IntelliJ IDEA项目配置...
echo.

echo 1. 备份重要文件...
if not exist backup mkdir backup
if exist .idea\misc.xml copy .idea\misc.xml backup\ 2>nul
if exist .idea\vcs.xml copy .idea\vcs.xml backup\ 2>nul
echo ✅ 重要配置已备份

echo.
echo 2. 清理IDE配置...
if exist .idea\workspace.xml del .idea\workspace.xml
if exist .idea\usage.statistics.xml del .idea\usage.statistics.xml
if exist .idea\dictionaries rmdir /s /q .idea\dictionaries
if exist .idea\shelf rmdir /s /q .idea\shelf
if exist .idea\modules rmdir /s /q .idea\modules
echo ✅ IDE配置已清理

echo.
echo 3. 重新创建模块配置...
mkdir .idea\modules
echo ✅ 模块目录已创建

echo.
echo 4. 重新导入项目...
echo 请按以下步骤操作：
echo.
echo 步骤1: 关闭IntelliJ IDEA
echo 步骤2: 重新打开IntelliJ IDEA
echo 步骤3: 选择 "Open or Import"
echo 步骤4: 选择项目根目录（包含pom.xml的目录）
echo 步骤5: 选择 "Open as Project"
echo 步骤6: 等待Maven依赖下载完成
echo.
echo 这样可以让IntelliJ重新识别项目结构，避免重复配置问题。

echo.
echo ✅ 重置完成！请按照上述步骤重新导入项目。

pause
