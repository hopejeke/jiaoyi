# -*- coding: utf-8 -*-
import os
import sys

# 设置输出编码
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

def count_file_lines(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            return len(f.readlines())
    except:
        return 0

def count_directory(base_path, extensions, exclude_dirs=['target', 'node_modules', '.git', '__pycache__', '.idea']):
    total_lines = 0
    file_count = 0
    
    if not os.path.exists(base_path):
        return 0, 0
    
    for root, dirs, files in os.walk(base_path):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for file in files:
            if any(file.endswith(ext) for ext in extensions):
                file_path = os.path.join(root, file)
                if any(exclude_dir in file_path.replace(os.sep, '/') for exclude_dir in exclude_dirs):
                    continue
                lines = count_file_lines(file_path)
                if lines > 0:
                    total_lines += lines
                    file_count += 1
    
    return total_lines, file_count

print("=" * 70)
print("代码行数统计")
print("=" * 70)

# 当前项目
print("\n【当前项目 (jiaoyi)】")
print("-" * 70)
current_path = r'D:\软件\ideaproject\jiaoyi'
if os.path.exists(current_path):
    java_lines, java_files = count_directory(current_path, ['.java'])
    print(f"Java文件: {java_files:4d} 个, {java_lines:7,} 行")
    
    xml_lines, xml_files = count_directory(current_path, ['.xml'])
    print(f"XML文件:  {xml_files:4d} 个, {xml_lines:7,} 行")
    
    prop_lines, prop_files = count_directory(current_path, ['.properties'])
    print(f"Properties文件: {prop_files:4d} 个, {prop_lines:7,} 行")
    
    yaml_lines, yaml_files = count_directory(current_path, ['.yml', '.yaml'])
    print(f"YAML文件: {yaml_files:4d} 个, {yaml_lines:7,} 行")
    
    sql_lines, sql_files = count_directory(current_path, ['.sql'])
    print(f"SQL文件:  {sql_files:4d} 个, {sql_lines:7,} 行")
    
    current_total = java_lines + xml_lines + prop_lines + yaml_lines + sql_lines
    current_total_files = java_files + xml_files + prop_files + yaml_files + sql_files
    print(f"{'总计':-<20} {current_total_files:4d} 个文件, {current_total:7,} 行")
else:
    print(f"当前项目路径不存在: {current_path}")
    current_total = 0
    current_total_files = 0

# OO项目
print("\n【OO项目 (online-order-v2-backend)】")
print("-" * 70)
oo_path = r'D:\软件\ideaproject\online-order-v2-backend'
if os.path.exists(oo_path):
    ts_lines, ts_files = count_directory(oo_path, ['.ts', '.js'])
    print(f"TS/JS文件: {ts_files:4d} 个, {ts_lines:7,} 行")
    
    json_lines, json_files = count_directory(oo_path, ['.json'])
    print(f"JSON文件: {json_files:4d} 个, {json_lines:7,} 行")
    
    oo_total = ts_lines + json_lines
    oo_total_files = ts_files + json_files
    print(f"{'总计':-<20} {oo_total_files:4d} 个文件, {oo_total:7,} 行")
    
    print("\n【对比分析】")
    print("-" * 70)
    if current_total > 0:
        diff = current_total - oo_total
        diff_percent = (diff / oo_total * 100) if oo_total > 0 else 0
        print(f"当前项目比OO项目: {diff:+,} 行 ({diff_percent:+.1f}%)")
        print(f"当前项目是OO项目的: {current_total/oo_total*100:.1f}%")
else:
    print(f"OO项目路径不存在: {oo_path}")
    print("请确认路径是否正确")

print("=" * 70)


