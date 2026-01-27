#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

def count_lines_in_file(file_path):
    """统计单个文件的行数"""
    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            return len(f.readlines())
    except:
        return 0

def count_lines_in_directory(directory, extensions, exclude_dirs=None):
    """统计指定目录下指定扩展名文件的总行数"""
    if exclude_dirs is None:
        exclude_dirs = ['target', 'node_modules', '.git', '__pycache__', '.idea']
    
    total_lines = 0
    file_count = 0
    file_details = []
    
    for root, dirs, files in os.walk(directory):
        # 排除不需要的目录
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        
        for file in files:
            if any(file.endswith(ext) for ext in extensions):
                file_path = os.path.join(root, file)
                # 检查路径中是否包含排除目录
                if any(exclude_dir in file_path for exclude_dir in exclude_dirs):
                    continue
                
                lines = count_lines_in_file(file_path)
                if lines > 0:
                    total_lines += lines
                    file_count += 1
                    file_details.append((file_path, lines))
    
    return total_lines, file_count, file_details

def main():
    # 当前项目路径
    current_path = r'D:\软件\ideaproject\jiaoyi'
    
    # OO项目路径
    oo_path = r'D:\软件\ideaproject\online-order-v2-backend'
    
    print("=" * 70)
    print("代码行数统计")
    print("=" * 70)
    
    # 统计当前项目
    print("\n【当前项目 (jiaoyi)】")
    print("-" * 70)
    
    # Java文件
    java_lines, java_files, _ = count_lines_in_directory(current_path, ['.java'])
    print(f"Java文件: {java_files:4d} 个, {java_lines:7,} 行")
    
    # XML文件
    xml_lines, xml_files, _ = count_lines_in_directory(current_path, ['.xml'])
    print(f"XML文件:  {xml_files:4d} 个, {xml_lines:7,} 行")
    
    # Properties文件
    prop_lines, prop_files, _ = count_lines_in_directory(current_path, ['.properties'])
    print(f"Properties文件: {prop_files:4d} 个, {prop_lines:7,} 行")
    
    # YAML文件
    yaml_lines, yaml_files, _ = count_lines_in_directory(current_path, ['.yml', '.yaml'])
    print(f"YAML文件: {yaml_files:4d} 个, {yaml_lines:7,} 行")
    
    # SQL文件
    sql_lines, sql_files, _ = count_lines_in_directory(current_path, ['.sql'])
    print(f"SQL文件:  {sql_files:4d} 个, {sql_lines:7,} 行")
    
    current_total = java_lines + xml_lines + prop_lines + yaml_lines + sql_lines
    current_total_files = java_files + xml_files + prop_files + yaml_files + sql_files
    print(f"{'总计':-<20} {current_total_files:4d} 个文件, {current_total:7,} 行")
    
    # 统计OO项目
    print("\n【OO项目 (online-order-v2-backend)】")
    print("-" * 70)
    
    if os.path.exists(oo_path):
        # TypeScript/JavaScript文件
        ts_lines, ts_files, _ = count_lines_in_directory(oo_path, ['.ts', '.js'])
        print(f"TS/JS文件: {ts_files:4d} 个, {ts_lines:7,} 行")
        
        # JSON文件
        json_lines, json_files, _ = count_lines_in_directory(oo_path, ['.json'])
        print(f"JSON文件: {json_files:4d} 个, {json_lines:7,} 行")
        
        oo_total = ts_lines + json_lines
        oo_total_files = ts_files + json_files
        print(f"{'总计':-<20} {oo_total_files:4d} 个文件, {oo_total:7,} 行")
    else:
        print(f"OO项目路径不存在: {oo_path}")
        oo_total = 0
        oo_total_files = 0
    
    # 对比
    print("\n【对比分析】")
    print("-" * 70)
    if oo_total > 0:
        diff = current_total - oo_total
        diff_percent = (diff / oo_total * 100) if oo_total > 0 else 0
        print(f"当前项目比OO项目: {diff:+,} 行 ({diff_percent:+.1f}%)")
        print(f"当前项目是OO项目的: {current_total/oo_total*100:.1f}%")
    else:
        print("无法对比：OO项目路径不存在")
    
    print("=" * 70)

if __name__ == '__main__':
    main()


