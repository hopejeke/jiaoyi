#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
订单服务压测脚本
支持多线程并发压测订单创建、支付、查询等核心接口
"""

import requests
import json
import time
import random
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from collections import defaultdict
import argparse
import sys

class LoadTestStats:
    """压测统计"""
    def __init__(self):
        self.lock = threading.Lock()
        self.total_requests = 0
        self.success_requests = 0
        self.failed_requests = 0
        self.response_times = []
        self.error_details = defaultdict(int)
        self.status_codes = defaultdict(int)
        
    def record(self, success, response_time, status_code=None, error=None):
        with self.lock:
            self.total_requests += 1
            if success:
                self.success_requests += 1
            else:
                self.failed_requests += 1
            if response_time:
                self.response_times.append(response_time)
            if status_code:
                self.status_codes[status_code] += 1
            if error:
                self.error_details[error] += 1
    
    def get_stats(self):
        with self.lock:
            response_times = sorted(self.response_times)
            total = len(response_times)
            if total == 0:
                return {
                    'total': self.total_requests,
                    'success': self.success_requests,
                    'failed': self.failed_requests,
                    'success_rate': 0,
                    'avg_time': 0,
                    'min_time': 0,
                    'max_time': 0,
                    'p50': 0,
                    'p95': 0,
                    'p99': 0
                }
            
            return {
                'total': self.total_requests,
                'success': self.success_requests,
                'failed': self.failed_requests,
                'success_rate': (self.success_requests / self.total_requests * 100) if self.total_requests > 0 else 0,
                'avg_time': sum(response_times) / total,
                'min_time': min(response_times),
                'max_time': max(response_times),
                'p50': response_times[int(total * 0.5)],
                'p95': response_times[int(total * 0.95)],
                'p99': response_times[int(total * 0.99)] if total > 99 else response_times[-1],
                'status_codes': dict(self.status_codes),
                'errors': dict(self.error_details)
            }

class LoadTester:
    """压测器"""
    
    def __init__(self, base_url, threads=10, duration=60, ramp_up=5):
        self.base_url = base_url.rstrip('/')
        self.threads = threads
        self.duration = duration
        self.ramp_up = ramp_up
        self.stats = LoadTestStats()
        self.running = False
        
        # 测试数据
        self.merchant_ids = ['merchant_001', 'merchant_002', 'merchant_003']
        self.user_ids = [1001, 1002, 1003, 1004, 1005]
        self.product_ids = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        self.store_ids = [1001, 1002, 1003, 1004, 1005]
        
    def create_order_request(self, merchant_id=None, user_id=None, store_id=None):
        """生成创建订单请求"""
        merchant_id = merchant_id or random.choice(self.merchant_ids)
        user_id = user_id or random.choice(self.user_ids)
        store_id = store_id or random.choice(self.store_ids)
        
        # 随机选择1-3个商品
        num_items = random.randint(1, 3)
        order_items = []
        for _ in range(num_items):
            product_id = random.choice(self.product_ids)
            quantity = random.randint(1, 3)
            order_items.append({
                'productId': product_id,
                'quantity': quantity
            })
        
        # 生成随机电话号码
        phone = f'138{random.randint(10000000, 99999999)}'
        name = f'Test User {user_id}'
        address = f'{random.randint(1, 999)} Test Street, Test City, CA {random.randint(10000, 99999)}'
        
        return {
            'merchantId': merchant_id,
            'userId': user_id,
            'orderType': random.choice(['DELIVERY', 'PICKUP', 'SELF_DINE_IN']),
            'orderItems': order_items,
            'receiverName': name,
            'receiverPhone': phone,
            'receiverAddress': address,
            'paymentMethod': random.choice(['CASH', 'CREDIT_CARD', 'ALIPAY']),
            'payOnline': random.choice([True, False])
        }
    
    def test_create_order(self):
        """测试创建订单"""
        url = f'{self.base_url}/api/orders'
        request_data = self.create_order_request()
        
        start_time = time.time()
        try:
            response = requests.post(
                url,
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            response_time = (time.time() - start_time) * 1000  # 转换为毫秒
            
            success = response.status_code == 200
            if success:
                result = response.json()
                success = result.get('code') == 200
            
            self.stats.record(success, response_time, response.status_code)
            
            if success:
                return response.json()
            else:
                error_msg = f"HTTP {response.status_code}"
                if response.text:
                    try:
                        error_data = response.json()
                        error_msg = error_data.get('message', error_msg)
                    except:
                        error_msg = response.text[:100]
                self.stats.record(False, response_time, response.status_code, error_msg)
                return None
                
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            error_msg = str(e)[:100]
            self.stats.record(False, response_time, None, error_msg)
            return None
    
    def test_pay_order(self, order_id):
        """测试支付订单"""
        url = f'{self.base_url}/api/orders/{order_id}/pay'
        request_data = {
            'paymentMethod': 'CASH',
            'amount': None  # 由服务端计算
        }
        
        start_time = time.time()
        try:
            response = requests.post(
                url,
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            response_time = (time.time() - start_time) * 1000
            
            success = response.status_code == 200
            if success:
                result = response.json()
                success = result.get('code') == 200
            
            self.stats.record(success, response_time, response.status_code)
            return success
            
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            error_msg = str(e)[:100]
            self.stats.record(False, response_time, None, error_msg)
            return False
    
    def test_get_order(self, order_id):
        """测试查询订单"""
        url = f'{self.base_url}/api/orders/{order_id}'
        
        start_time = time.time()
        try:
            response = requests.get(url, timeout=30)
            response_time = (time.time() - start_time) * 1000
            
            success = response.status_code == 200
            if success:
                result = response.json()
                success = result.get('code') == 200
            
            self.stats.record(success, response_time, response.status_code)
            return success
            
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            error_msg = str(e)[:100]
            self.stats.record(False, response_time, None, error_msg)
            return False
    
    def test_calculate_price(self):
        """测试计算价格"""
        url = f'{self.base_url}/api/orders/calculate-price'
        request_data = self.create_order_request()
        
        start_time = time.time()
        try:
            response = requests.post(
                url,
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            response_time = (time.time() - start_time) * 1000
            
            success = response.status_code == 200
            if success:
                result = response.json()
                success = result.get('code') == 200
            
            self.stats.record(success, response_time, response.status_code)
            return success
            
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            error_msg = str(e)[:100]
            self.stats.record(False, response_time, None, error_msg)
            return False
    
    def worker_thread(self, test_type='create_order'):
        """工作线程"""
        created_orders = []
        
        while self.running:
            try:
                if test_type == 'create_order':
                    result = self.test_create_order()
                    if result and result.get('data'):
                        order = result['data'].get('order')
                        if order:
                            created_orders.append(order.get('id'))
                
                elif test_type == 'pay_order':
                    if created_orders:
                        order_id = random.choice(created_orders)
                        self.test_pay_order(order_id)
                    else:
                        # 如果没有已创建的订单，先创建一个
                        result = self.test_create_order()
                        if result and result.get('data'):
                            order = result['data'].get('order')
                            if order:
                                order_id = order.get('id')
                                created_orders.append(order_id)
                                self.test_pay_order(order_id)
                
                elif test_type == 'get_order':
                    if created_orders:
                        order_id = random.choice(created_orders)
                        self.test_get_order(order_id)
                    else:
                        # 如果没有已创建的订单，先创建一个
                        result = self.test_create_order()
                        if result and result.get('data'):
                            order = result['data'].get('order')
                            if order:
                                created_orders.append(order.get('id'))
                
                elif test_type == 'calculate_price':
                    self.test_calculate_price()
                
                elif test_type == 'mixed':
                    # 混合测试：随机选择操作
                    action = random.choice(['create', 'pay', 'get', 'calculate'])
                    if action == 'create':
                        result = self.test_create_order()
                        if result and result.get('data'):
                            order = result['data'].get('order')
                            if order:
                                created_orders.append(order.get('id'))
                    elif action == 'pay' and created_orders:
                        order_id = random.choice(created_orders)
                        self.test_pay_order(order_id)
                    elif action == 'get' and created_orders:
                        order_id = random.choice(created_orders)
                        self.test_get_order(order_id)
                    elif action == 'calculate':
                        self.test_calculate_price()
                
                # 随机延迟，模拟真实用户行为
                time.sleep(random.uniform(0.1, 0.5))
                
            except Exception as e:
                print(f"Worker thread error: {e}")
                time.sleep(1)
    
    def run(self, test_type='create_order'):
        """运行压测"""
        print(f"\n{'='*60}")
        print(f"开始压测: {test_type}")
        print(f"目标URL: {self.base_url}")
        print(f"并发线程数: {self.threads}")
        print(f"持续时间: {self.duration}秒")
        print(f"预热时间: {self.ramp_up}秒")
        print(f"{'='*60}\n")
        
        self.running = True
        
        # 启动工作线程
        with ThreadPoolExecutor(max_workers=self.threads) as executor:
            futures = []
            for i in range(self.threads):
                # 逐步启动线程（预热）
                if i > 0:
                    time.sleep(self.ramp_up / self.threads)
                future = executor.submit(self.worker_thread, test_type)
                futures.append(future)
            
            # 运行指定时间
            start_time = time.time()
            last_print_time = start_time
            
            while time.time() - start_time < self.duration:
                time.sleep(5)  # 每5秒打印一次统计
                current_time = time.time()
                if current_time - last_print_time >= 5:
                    stats = self.stats.get_stats()
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] "
                          f"总请求: {stats['total']}, "
                          f"成功: {stats['success']}, "
                          f"失败: {stats['failed']}, "
                          f"成功率: {stats['success_rate']:.2f}%, "
                          f"平均响应时间: {stats['avg_time']:.2f}ms, "
                          f"P95: {stats['p95']:.2f}ms")
                    last_print_time = current_time
            
            # 停止压测
            self.running = False
            
            # 等待所有线程完成
            for future in futures:
                future.cancel()
        
        # 打印最终统计
        self.print_final_stats()
    
    def print_final_stats(self):
        """打印最终统计"""
        stats = self.stats.get_stats()
        
        print(f"\n{'='*60}")
        print("压测完成 - 最终统计")
        print(f"{'='*60}")
        print(f"总请求数: {stats['total']}")
        print(f"成功请求: {stats['success']}")
        print(f"失败请求: {stats['failed']}")
        print(f"成功率: {stats['success_rate']:.2f}%")
        print(f"\n响应时间统计 (毫秒):")
        print(f"  平均: {stats['avg_time']:.2f}ms")
        print(f"  最小: {stats['min_time']:.2f}ms")
        print(f"  最大: {stats['max_time']:.2f}ms")
        print(f"  P50: {stats['p50']:.2f}ms")
        print(f"  P95: {stats['p95']:.2f}ms")
        print(f"  P99: {stats['p99']:.2f}ms")
        
        if stats['status_codes']:
            print(f"\nHTTP状态码分布:")
            for code, count in sorted(stats['status_codes'].items()):
                print(f"  {code}: {count}")
        
        if stats['errors']:
            print(f"\n错误详情 (前10个):")
            for error, count in sorted(stats['errors'].items(), key=lambda x: x[1], reverse=True)[:10]:
                print(f"  {error}: {count}")
        
        print(f"{'='*60}\n")

def main():
    parser = argparse.ArgumentParser(description='订单服务压测脚本')
    parser.add_argument('--url', type=str, default='http://localhost:8080',
                       help='服务基础URL (默认: http://localhost:8080)')
    parser.add_argument('--threads', type=int, default=10,
                       help='并发线程数 (默认: 10)')
    parser.add_argument('--duration', type=int, default=60,
                       help='压测持续时间(秒) (默认: 60)')
    parser.add_argument('--ramp-up', type=int, default=5,
                       help='预热时间(秒) (默认: 5)')
    parser.add_argument('--type', type=str, default='create_order',
                       choices=['create_order', 'pay_order', 'get_order', 'calculate_price', 'mixed'],
                       help='测试类型 (默认: create_order)')
    
    args = parser.parse_args()
    
    tester = LoadTester(
        base_url=args.url,
        threads=args.threads,
        duration=args.duration,
        ramp_up=args.ramp_up
    )
    
    try:
        tester.run(test_type=args.type)
    except KeyboardInterrupt:
        print("\n\n压测被用户中断")
        tester.running = False
        tester.print_final_stats()
        sys.exit(0)

if __name__ == '__main__':
    main()

