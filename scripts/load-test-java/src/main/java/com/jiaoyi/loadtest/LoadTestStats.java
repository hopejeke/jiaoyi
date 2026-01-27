package com.jiaoyi.loadtest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 压测统计类
 */
public class LoadTestStats {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AtomicLong> statusCodes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorDetails = new ConcurrentHashMap<>();
    
    public void record(boolean success, long responseTime, Integer statusCode, String error) {
        totalRequests.incrementAndGet();
        if (success) {
            successRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
        
        if (responseTime > 0) {
            responseTimes.add(responseTime);
        }
        
        if (statusCode != null) {
            statusCodes.computeIfAbsent(String.valueOf(statusCode), k -> new AtomicLong(0)).incrementAndGet();
        }
        
        if (error != null && !error.isEmpty()) {
            String errorKey = error.length() > 100 ? error.substring(0, 100) : error;
            errorDetails.computeIfAbsent(errorKey, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    public StatsResult getStats() {
        List<Long> sortedTimes = new ArrayList<>(responseTimes);
        Collections.sort(sortedTimes);
        
        long total = totalRequests.get();
        long success = successRequests.get();
        long failed = failedRequests.get();
        
        if (sortedTimes.isEmpty()) {
            return new StatsResult(total, success, failed, 0.0, 0, 0, 0, 0, 0, 0,
                    new HashMap<>(), new HashMap<>());
        }
        
        double avgTime = sortedTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minTime = sortedTimes.get(0);
        long maxTime = sortedTimes.get(sortedTimes.size() - 1);
        long p50 = sortedTimes.get((int) (sortedTimes.size() * 0.5));
        long p95 = sortedTimes.get((int) (sortedTimes.size() * 0.95));
        long p99 = sortedTimes.size() > 99 
                ? sortedTimes.get((int) (sortedTimes.size() * 0.99)) 
                : sortedTimes.get(sortedTimes.size() - 1);
        
        double successRate = total > 0 ? (success * 100.0 / total) : 0.0;
        
        Map<String, Long> statusCodeMap = statusCodes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        
        Map<String, Long> errorMap = errorDetails.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        
        return new StatsResult(total, success, failed, successRate, avgTime, minTime, maxTime,
                p50, p95, p99, statusCodeMap, errorMap);
    }
    
    public static class StatsResult {
        public final long total;
        public final long success;
        public final long failed;
        public final double successRate;
        public final double avgTime;
        public final long minTime;
        public final long maxTime;
        public final long p50;
        public final long p95;
        public final long p99;
        public final Map<String, Long> statusCodes;
        public final Map<String, Long> errors;
        
        public StatsResult(long total, long success, long failed, double successRate,
                          double avgTime, long minTime, long maxTime, long p50, long p95, long p99,
                          Map<String, Long> statusCodes, Map<String, Long> errors) {
            this.total = total;
            this.success = success;
            this.failed = failed;
            this.successRate = successRate;
            this.avgTime = avgTime;
            this.minTime = minTime;
            this.maxTime = maxTime;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.statusCodes = statusCodes;
            this.errors = errors;
        }
    }
}



