@echo off
echo 修复IntelliJ IDEA项目配置...
echo.

echo 1. 清理IDE缓存...
if exist .idea\workspace.xml del .idea\workspace.xml
if exist .idea\usage.statistics.xml del .idea\usage.statistics.xml
if exist .idea\dictionaries del /q .idea\dictionaries
if exist .idea\shelf del /q .idea\shelf
echo ✅ IDE缓存已清理

echo.
echo 2. 重新生成项目结构...
if exist .idea\modules rmdir /s /q .idea\modules
mkdir .idea\modules
echo ✅ 模块配置已重置

echo.
echo 3. 重新导入Maven项目...
echo 请在IntelliJ IDEA中执行以下操作：
echo 1. 关闭当前项目
echo 2. 选择 "File" -> "Open"
echo 3. 选择项目根目录的 pom.xml 文件
echo 4. 选择 "Open as Project"
echo 5. 等待Maven依赖下载完成

echo.
echo 4. 如果问题仍然存在，请手动操作：
echo - 打开 File -> Project Structure
echo - 选择 Modules -> jiaoyi
echo - 在 Sources 标签页中，删除重复的 src\main\resources 条目
echo - 确保只有一个 src\main\resources 被标记为 Resources

echo.
echo ✅ IntelliJ IDEA配置修复完成！

pause
