package com.jiaoyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电商交易系统启动类
 */
@SpringBootApplication
@MapperScan("com.jiaoyi.mapper")
@EnableScheduling
public class JiaoyiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiaoyiApplication.class, args);
    }
}
