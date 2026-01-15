package com.jiaoyi.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类
 * 负责：订单、订单项、支付管理
 */
@SpringBootApplication(
    scanBasePackages = {"com.jiaoyi.order", "com.jiaoyi.common"},
    exclude = {
        // 只排除 DataSource 自动配置，保留 MyBatis 自动配置
        // MyBatis 需要 SqlSessionFactory，我们需要手动配置以支持多数据源
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
        // 注意：不排除 MybatisAutoConfiguration，让 MyBatis 能自动配置
        // 但我们需要确保有一个名为 "dataSource" 的 Bean 给 MyBatis 使用
    }
)
@MapperScan(
    basePackages = "com.jiaoyi.order.mapper",
    sqlSessionFactoryRef = "shardingSqlSessionFactory"
)
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

