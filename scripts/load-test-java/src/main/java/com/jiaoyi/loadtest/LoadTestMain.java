package com.jiaoyi.loadtest;

import java.util.Arrays;
import java.util.List;

/**
 * 压测主类
 * 可以直接在IDEA中运行，也可以打包成JAR运行
 */
public class LoadTestMain {
    
    /**
     * 默认配置（可以在IDEA的运行配置中修改Program arguments来覆盖）
     */
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final int DEFAULT_THREADS = 10;
    private static final int DEFAULT_DURATION = 60;
    private static final int DEFAULT_RAMP_UP = 5;
    private static final String DEFAULT_TEST_TYPE = "create_order";
    
    public static void main(String[] args) {
        // 如果没有参数，使用默认配置（方便IDEA直接运行）
        if (args.length == 0) {
            System.out.println("使用默认配置运行压测...");
            System.out.println("提示：可以在IDEA的运行配置中添加Program arguments来修改参数");
            System.out.println("例如：--url http://localhost:8080 --threads 20 --duration 120 --type mixed\n");
        }
        
        String baseUrl = DEFAULT_BASE_URL;
        int threads = DEFAULT_THREADS;
        int duration = DEFAULT_DURATION;
        int rampUp = DEFAULT_RAMP_UP;
        String testType = DEFAULT_TEST_TYPE;
        
        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url":
                    if (i + 1 < args.length) {
                        baseUrl = args[++i];
                    }
                    break;
                case "--threads":
                    if (i + 1 < args.length) {
                        threads = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--duration":
                    if (i + 1 < args.length) {
                        duration = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--ramp-up":
                    if (i + 1 < args.length) {
                        rampUp = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--type":
                    if (i + 1 < args.length) {
                        testType = args[++i];
                    }
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
            }
        }
        
        // 验证测试类型
        List<String> validTypes = Arrays.asList("create_order", "pay_order", "get_order", 
                "calculate_price", "mixed");
        if (!validTypes.contains(testType)) {
            System.err.println("无效的测试类型: " + testType);
            System.err.println("有效类型: " + String.join(", ", validTypes));
            return;
        }
        
        LoadTester tester = new LoadTester(baseUrl, threads, duration, rampUp);
        
        try {
            tester.run(testType);
        } catch (InterruptedException e) {
            System.err.println("\n压测被用户中断");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("压测执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printUsage() {
        System.out.println("订单服务压测工具");
        System.out.println("\n用法:");
        System.out.println("  1. 在IDEA中直接运行（使用默认配置）");
        System.out.println("  2. 在IDEA运行配置中添加Program arguments:");
        System.out.println("     --url http://localhost:8080 --threads 20 --duration 120");
        System.out.println("  3. 打包后运行:");
        System.out.println("     java -jar load-test.jar [选项]");
        System.out.println("\n选项:");
        System.out.println("  --url <URL>           服务基础URL (默认: " + DEFAULT_BASE_URL + ")");
        System.out.println("  --threads <数量>       并发线程数 (默认: " + DEFAULT_THREADS + ")");
        System.out.println("  --duration <秒数>     压测持续时间 (默认: " + DEFAULT_DURATION + ")");
        System.out.println("  --ramp-up <秒数>      预热时间 (默认: " + DEFAULT_RAMP_UP + ")");
        System.out.println("  --type <类型>         测试类型 (默认: " + DEFAULT_TEST_TYPE + ")");
        System.out.println("                         可选: create_order, pay_order, get_order,");
        System.out.println("                              calculate_price, mixed");
        System.out.println("  --help, -h            显示帮助信息");
        System.out.println("\n示例:");
        System.out.println("  直接运行（使用默认配置）");
        System.out.println("  --url http://localhost:8080 --threads 20 --duration 120");
        System.out.println("  --type mixed --threads 50 --duration 300");
    }
}

