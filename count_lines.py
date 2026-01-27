#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys

def count_lines_in_directory(directory, extensions):
    """统计指定目录下指定扩展名文件的总行数"""
    total_lines = 0
    file_count = 0
    
    for root, dirs, files in os.walk(directory):
        # 排除不需要的目录
        dirs[:] = [d for d in dirs if d not in ['node_modules', 'target', '.git', '__pycache__', '.idea']]
        
        for file in files:
            if any(file.endswith(ext) for ext in extensions):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                        lines = len(f.readlines())
                        total_lines += lines
                        file_count += 1
                except Exception as e:
                    pass  # 忽略无法读取的文件
    
    return total_lines, file_count

def main():
    # OO项目路径
    oo_path = r'D:\软件\ideaproject\online-order-v2-backend'
    # 当前项目路径
    current_path = r'D:\软件\ideaproject\jiaoyi'
    
    # 要统计的文件扩展名
    extensions = ['.java', '.xml', '.properties', '.yml', '.yaml', '.sql']
    
    print("正在统计代码行数...")
    print("=" * 60)
    
    # 统计OO项目
    if os.path.exists(oo_path):
        oo_lines, oo_files = count_lines_in_directory(oo_path, extensions)
        print(f"OO项目 (online-order-v2-backend):")
        print(f"  文件数: {oo_files}")
        print(f"  总行数: {oo_lines:,}")
        
        # 分别统计Java和XML
        oo_java_lines, oo_java_files = count_lines_in_directory(oo_path, ['.java'])
        oo_xml_lines, oo_xml_files = count_lines_in_directory(oo_path, ['.xml'])
        print(f"  Java文件: {oo_java_files} 个, {oo_java_lines:,} 行")
        print(f"  XML文件: {oo_xml_files} 个, {oo_xml_lines:,} 行")
    else:
        print(f"OO项目路径不存在: {oo_path}")
        oo_lines = 0
    
    print()
    
    # 统计当前项目
    if os.path.exists(current_path):
        current_lines, current_files = count_lines_in_directory(current_path, extensions)
        print(f"当前项目 (jiaoyi):")
        print(f"  文件数: {current_files}")
        print(f"  总行数: {current_lines:,}")
        
        # 分别统计Java和XML
        current_java_lines, current_java_files = count_lines_in_directory(current_path, ['.java'])
        current_xml_lines, current_xml_files = count_lines_in_directory(current_path, ['.xml'])
        print(f"  Java文件: {current_java_files} 个, {current_java_lines:,} 行")
        print(f"  XML文件: {current_xml_files} 个, {current_xml_lines:,} 行")
    else:
        print(f"当前项目路径不存在: {current_path}")
        current_lines = 0
    
    print()
    print("=" * 60)
    if oo_lines > 0 and current_lines > 0:
        diff = current_lines - oo_lines
        diff_percent = (diff / oo_lines * 100) if oo_lines > 0 else 0
        print(f"对比:")
        print(f"  当前项目比OO项目: {diff:+,} 行 ({diff_percent:+.1f}%)")
        print(f"  当前项目是OO项目的: {current_lines/oo_lines*100:.1f}%")

if __name__ == '__main__':
    main()


