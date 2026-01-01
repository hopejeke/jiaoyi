package com.jiaoyi.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商品服务启动类
 * 负责：商品、库存、店铺管理
 */
@SpringBootApplication(
    scanBasePackages = {"com.jiaoyi.product", "com.jiaoyi.common", "com.jiaoyi.config"},
    exclude = {
        org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.class
    }
)
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
// 注意：不要在这里使用 @MapperScan，Mapper 的扫描和 SqlSessionFactory 绑定在 DataSourceConfig 中配置
// 这样可以精确控制哪些 Mapper 使用哪个数据源
// 排除 MybatisAutoConfiguration 避免自动配置冲突（因为我们有多个 SqlSessionFactory）
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}

