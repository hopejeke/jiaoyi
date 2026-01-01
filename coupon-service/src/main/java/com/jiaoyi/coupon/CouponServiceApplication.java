package com.jiaoyi.coupon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 优惠券服务启动类
 * 负责：优惠券、优惠券使用记录管理
 */
@SpringBootApplication(scanBasePackages = {"com.jiaoyi.coupon", "com.jiaoyi.common", "com.jiaoyi.config"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.jiaoyi.coupon.mapper")
public class CouponServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}


