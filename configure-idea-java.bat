@echo off
echo 配置IntelliJ IDEA使用Java 8...
echo.

echo 请按以下步骤在IntelliJ IDEA中配置Java版本：
echo.
echo 步骤1: 打开项目设置
echo - 按 Ctrl+Alt+Shift+S
echo - 或选择 File -> Project Structure
echo.
echo 步骤2: 设置Project SDK
echo - 在左侧选择 "Project"
echo - 在 "Project SDK" 下拉框中选择 Java 8
echo - 如果没有Java 8，点击 "New..." 添加
echo.
echo 步骤3: 设置Module语言级别
echo - 在左侧选择 "Modules" -> "jiaoyi"
echo - 在 "Language level" 下拉框中选择 "8 - Lambdas, type annotations etc."
echo.
echo 步骤4: 设置编译器
echo - 在左侧选择 "Settings" -> "Build, Execution, Deployment" -> "Compiler" -> "Java Compiler"
echo - 设置 "Project bytecode version" 为 8
echo - 设置 "Per-module bytecode version" 为 8
echo.
echo 步骤5: 重新导入Maven项目
echo - 右键点击 pom.xml
echo - 选择 "Maven" -> "Reload project"
echo.
echo 步骤6: 清理并重新构建
echo - 选择 Build -> Clean
echo - 选择 Build -> Rebuild Project
echo.
echo 完成以上步骤后，Java版本兼容性问题应该得到解决。

pause
