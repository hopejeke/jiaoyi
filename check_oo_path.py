#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

oo_path = r'D:\软件\ideaproject\online-order-v2-backend'
parent_path = r'D:\软件\ideaproject'

print("=" * 60)
print("路径检查")
print("=" * 60)

print(f"\nOO项目路径: {oo_path}")
print(f"路径存在: {os.path.exists(oo_path)}")

print(f"\n父目录路径: {parent_path}")
print(f"父目录存在: {os.path.exists(parent_path)}")

if os.path.exists(parent_path):
    print("\n父目录内容:")
    try:
        items = os.listdir(parent_path)
        for item in items:
            item_path = os.path.join(parent_path, item)
            item_type = "目录" if os.path.isdir(item_path) else "文件"
            print(f"  [{item_type}] {item}")
    except Exception as e:
        print(f"  无法列出目录内容: {e}")
else:
    print("\n父目录不存在，尝试查找其他可能的位置...")
    # 尝试查找包含 "order" 或 "oo" 的目录
    possible_paths = [
        r'D:\软件',
        r'D:\',
    ]
    for base_path in possible_paths:
        if os.path.exists(base_path):
            print(f"\n检查 {base_path}:")
            try:
                for item in os.listdir(base_path):
                    if 'order' in item.lower() or 'oo' in item.lower():
                        item_path = os.path.join(base_path, item)
                        if os.path.isdir(item_path):
                            print(f"  找到可能的项目: {item_path}")
            except:
                pass

print("=" * 60)


