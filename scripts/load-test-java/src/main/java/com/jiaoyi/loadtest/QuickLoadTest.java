package com.jiaoyi.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 快速压测 - 简化版
 * 
 * 适合快速验证接口性能，直接运行main方法
 */
public class QuickLoadTest {

    // ====== 修改这里的配置 ======
    static final String URL = "http://localhost:8082/api/orders";
    static final int THREADS = 20;           // 并发数
    static final int REQUESTS_PER_THREAD = 100;  // 每线程请求数
    // ===========================

    static final AtomicLong success = new AtomicLong();
    static final AtomicLong failed = new AtomicLong();
    static final AtomicLong totalTime = new AtomicLong();
    static final Random random = new Random();

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("快速压测");
        System.out.println("URL: " + URL);
        System.out.println("并发: " + THREADS);
        System.out.println("总请求: " + (THREADS * REQUESTS_PER_THREAD));
        System.out.println("========================================\n");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        
        long startTime = System.currentTimeMillis();

        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        doRequest(client);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 进度显示
        while (latch.getCount() > 0) {
            Thread.sleep(1000);
            long total = success.get() + failed.get();
            System.out.printf("\r进度: %d/%d (成功: %d, 失败: %d)", 
                    total, THREADS * REQUESTS_PER_THREAD, success.get(), failed.get());
        }

        latch.await();
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;
        long total = success.get() + failed.get();
        double qps = total * 1000.0 / elapsed;
        double avgTime = totalTime.get() * 1.0 / total;
        double successRate = success.get() * 100.0 / total;

        System.out.println("\n\n========================================");
        System.out.println("结果:");
        System.out.printf("  总请求: %d%n", total);
        System.out.printf("  成功: %d%n", success.get());
        System.out.printf("  失败: %d%n", failed.get());
        System.out.printf("  成功率: %.2f%%%n", successRate);
        System.out.printf("  耗时: %dms%n", elapsed);
        System.out.printf("  QPS: %.2f%n", qps);
        System.out.printf("  平均响应: %.2fms%n", avgTime);
        System.out.println("========================================");
    }

    static void doRequest(HttpClient client) {
        int merchantId = random.nextInt(100) + 1;
        String body = String.format("""
            {
                "merchantId": "merchant_%d",
                "userId": %d,
                "storeId": %d,
                "orderType": "DELIVERY",
                "orderItems": [{
                    "productId": %d,
                    "skuId": %d,
                    "quantity": 1
                }],
                "receiverName": "测试",
                "receiverPhone": "13800138000",
                "receiverAddress": "测试地址"
            }
            """, merchantId, random.nextInt(90000) + 10000, merchantId,
                random.nextInt(5000) + 1, random.nextInt(10000) + 1);

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            totalTime.addAndGet(elapsed);

            if (response.statusCode() == 200 && response.body().contains("\"code\":200")) {
                success.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }
        } catch (Exception e) {
            totalTime.addAndGet(System.currentTimeMillis() - start);
            failed.incrementAndGet();
        }
    }
}