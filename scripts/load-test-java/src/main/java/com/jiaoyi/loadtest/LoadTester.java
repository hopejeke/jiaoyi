package com.jiaoyi.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 压测器
 */
public class LoadTester {
    private final String baseUrl;
    private final int threads;
    private final int durationSeconds;
    private final int rampUpSeconds;
    private final LoadTestStats stats = new LoadTestStats();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloseableHttpClient httpClient;
    
    // 测试数据
    private final List<String> merchantIds = Arrays.asList("merchant_001", "merchant_002", "merchant_003");
    private final List<Long> userIds = Arrays.asList(1001L, 1002L, 1003L, 1004L, 1005L);
    private final List<Long> productIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    private final List<Long> storeIds = Arrays.asList(1001L, 1002L, 1003L, 1004L, 1005L);
    private final Random random = new Random();
    
    public LoadTester(String baseUrl, int threads, int durationSeconds, int rampUpSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.threads = threads;
        this.durationSeconds = durationSeconds;
        this.rampUpSeconds = rampUpSeconds;
        
        // 配置HttpClient
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();
        
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
    
    private Map<String, Object> createOrderRequest(String merchantId, Long userId, Long storeId) {
        merchantId = merchantId != null ? merchantId : merchantIds.get(random.nextInt(merchantIds.size()));
        userId = userId != null ? userId : userIds.get(random.nextInt(userIds.size()));
        storeId = storeId != null ? storeId : storeIds.get(random.nextInt(storeIds.size()));
        
        int numItems = random.nextInt(3) + 1; // 1-3个商品
        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("productId", productIds.get(random.nextInt(productIds.size())));
            item.put("quantity", random.nextInt(3) + 1);
            orderItems.add(item);
        }
        
        String phone = "138" + (10000000 + random.nextInt(90000000));
        String name = "Test User " + userId;
        String address = (random.nextInt(999) + 1) + " Test Street, Test City, CA " + (10000 + random.nextInt(90000));
        
        Map<String, Object> request = new HashMap<>();
        request.put("merchantId", merchantId);
        request.put("userId", userId);
        request.put("orderType", Arrays.asList("DELIVERY", "PICKUP", "SELF_DINE_IN").get(random.nextInt(3)));
        request.put("orderItems", orderItems);
        request.put("receiverName", name);
        request.put("receiverPhone", phone);
        request.put("receiverAddress", address);
        request.put("paymentMethod", Arrays.asList("CASH", "CREDIT_CARD", "ALIPAY").get(random.nextInt(3)));
        request.put("payOnline", random.nextBoolean());
        
        return request;
    }
    
    private boolean testCreateOrder() {
        String url = baseUrl + "/api/orders";
        Map<String, Object> requestData = createOrderRequest(null, null, null);
        
        long startTime = System.currentTimeMillis();
        try {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(requestData)));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                long responseTime = System.currentTimeMillis() - startTime;
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                boolean success = statusCode == 200;
                if (success) {
                    try {
                        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                        success = "200".equals(String.valueOf(result.get("code")));
                    } catch (Exception e) {
                        success = false;
                    }
                }
                
                stats.record(success, responseTime, statusCode, success ? null : "HTTP " + statusCode);
                return success;
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            stats.record(false, responseTime, null, e.getMessage());
            return false;
        }
    }
    
    private boolean testPayOrder(Long orderId) {
        String url = baseUrl + "/api/orders/" + orderId + "/pay";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("paymentMethod", "CASH");
        
        long startTime = System.currentTimeMillis();
        try {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(requestData)));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                long responseTime = System.currentTimeMillis() - startTime;
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                boolean success = statusCode == 200;
                if (success) {
                    try {
                        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                        success = "200".equals(String.valueOf(result.get("code")));
                    } catch (Exception e) {
                        success = false;
                    }
                }
                
                stats.record(success, responseTime, statusCode, success ? null : "HTTP " + statusCode);
                return success;
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            stats.record(false, responseTime, null, e.getMessage());
            return false;
        }
    }
    
    private boolean testGetOrder(Long orderId) {
        String url = baseUrl + "/api/orders/" + orderId;
        
        long startTime = System.currentTimeMillis();
        try {
            HttpGet get = new HttpGet(url);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                long responseTime = System.currentTimeMillis() - startTime;
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                boolean success = statusCode == 200;
                if (success) {
                    try {
                        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                        success = "200".equals(String.valueOf(result.get("code")));
                    } catch (Exception e) {
                        success = false;
                    }
                }
                
                stats.record(success, responseTime, statusCode, success ? null : "HTTP " + statusCode);
                return success;
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            stats.record(false, responseTime, null, e.getMessage());
            return false;
        }
    }
    
    private boolean testCalculatePrice() {
        String url = baseUrl + "/api/orders/calculate-price";
        Map<String, Object> requestData = createOrderRequest(null, null, null);
        
        long startTime = System.currentTimeMillis();
        try {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(requestData)));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                long responseTime = System.currentTimeMillis() - startTime;
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                boolean success = statusCode == 200;
                if (success) {
                    try {
                        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                        success = "200".equals(String.valueOf(result.get("code")));
                    } catch (Exception e) {
                        success = false;
                    }
                }
                
                stats.record(success, responseTime, statusCode, success ? null : "HTTP " + statusCode);
                return success;
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            stats.record(false, responseTime, null, e.getMessage());
            return false;
        }
    }
    
    private void workerThread(String testType, List<Long> createdOrders) {
        while (running.get()) {
            try {
                switch (testType) {
                    case "create_order":
                        testCreateOrder();
                        break;
                    case "pay_order":
                        if (!createdOrders.isEmpty()) {
                            Long orderId = createdOrders.get(random.nextInt(createdOrders.size()));
                            testPayOrder(orderId);
                        }
                        break;
                    case "get_order":
                        if (!createdOrders.isEmpty()) {
                            Long orderId = createdOrders.get(random.nextInt(createdOrders.size()));
                            testGetOrder(orderId);
                        }
                        break;
                    case "calculate_price":
                        testCalculatePrice();
                        break;
                    case "mixed":
                        String action = Arrays.asList("create", "pay", "get", "calculate").get(random.nextInt(4));
                        switch (action) {
                            case "create":
                                testCreateOrder();
                                break;
                            case "pay":
                                if (!createdOrders.isEmpty()) {
                                    testPayOrder(createdOrders.get(random.nextInt(createdOrders.size())));
                                }
                                break;
                            case "get":
                                if (!createdOrders.isEmpty()) {
                                    testGetOrder(createdOrders.get(random.nextInt(createdOrders.size())));
                                }
                                break;
                            case "calculate":
                                testCalculatePrice();
                                break;
                        }
                        break;
                }
                
                // 随机延迟
                Thread.sleep(random.nextInt(400) + 100); // 100-500ms
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Worker thread error: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    public void run(String testType) throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("开始压测: " + testType);
        System.out.println("目标URL: " + baseUrl);
        System.out.println("并发线程数: " + threads);
        System.out.println("持续时间: " + durationSeconds + "秒");
        System.out.println("预热时间: " + rampUpSeconds + "秒");
        System.out.println("=".repeat(60) + "\n");
        
        running.set(true);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        List<Long> createdOrders = Collections.synchronizedList(new ArrayList<>());
        
        // 启动工作线程
        for (int i = 0; i < threads; i++) {
            if (i > 0) {
                Thread.sleep(rampUpSeconds * 1000L / threads);
            }
            final int threadIndex = i;
            Future<?> future = executor.submit(() -> workerThread(testType, createdOrders));
            futures.add(future);
        }
        
        // 运行指定时间
        long startTime = System.currentTimeMillis();
        long lastPrintTime = startTime;
        
        while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
            Thread.sleep(5000); // 每5秒打印一次
            
            if (System.currentTimeMillis() - lastPrintTime >= 5000) {
                LoadTestStats.StatsResult statsResult = stats.getStats();
                System.out.printf("[%s] 总请求: %d, 成功: %d, 失败: %d, 成功率: %.2f%%, " +
                                "平均响应时间: %.2fms, P95: %.2fms%n",
                        new Date().toString(),
                        statsResult.total,
                        statsResult.success,
                        statsResult.failed,
                        statsResult.successRate,
                        statsResult.avgTime,
                        statsResult.p95);
                lastPrintTime = System.currentTimeMillis();
            }
        }
        
        // 停止压测
        running.set(false);
        
        // 等待所有线程完成
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // 关闭HttpClient
        try {
            httpClient.close();
        } catch (IOException e) {
            System.err.println("Error closing HttpClient: " + e.getMessage());
        }
        
        // 打印最终统计
        printFinalStats();
    }
    
    private void printFinalStats() {
        LoadTestStats.StatsResult statsResult = stats.getStats();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("压测完成 - 最终统计");
        System.out.println("=".repeat(60));
        System.out.println("总请求数: " + statsResult.total);
        System.out.println("成功请求: " + statsResult.success);
        System.out.println("失败请求: " + statsResult.failed);
        System.out.printf("成功率: %.2f%%%n", statsResult.successRate);
        System.out.println("\n响应时间统计 (毫秒):");
        System.out.printf("  平均: %.2fms%n", statsResult.avgTime);
        System.out.printf("  最小: %dms%n", statsResult.minTime);
        System.out.printf("  最大: %dms%n", statsResult.maxTime);
        System.out.printf("  P50: %dms%n", statsResult.p50);
        System.out.printf("  P95: %dms%n", statsResult.p95);
        System.out.printf("  P99: %dms%n", statsResult.p99);
        
        if (!statsResult.statusCodes.isEmpty()) {
            System.out.println("\nHTTP状态码分布:");
            statsResult.statusCodes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> System.out.printf("  %s: %d%n", e.getKey(), e.getValue()));
        }
        
        if (!statsResult.errors.isEmpty()) {
            System.out.println("\n错误详情 (前10个):");
            statsResult.errors.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> System.out.printf("  %s: %d%n", e.getKey(), e.getValue()));
        }
        
        System.out.println("=".repeat(60) + "\n");
    }
}

