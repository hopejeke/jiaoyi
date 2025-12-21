package com.jiaoyi.product.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson配置类
 * 解决JavaScript大整数精度丢失问题：
 * 1. 将Long类型序列化为字符串（输出到前端）
 * 2. 支持从字符串反序列化为Long（接收前端数据）
 * 
 * 问题：JavaScript的Number类型只能安全表示±2^53的整数（-9007199254740991 到 9007199254740991）
 * 雪花算法生成的ID（如1207279389033627649）超过这个范围，会导致精度丢失
 * 例如：1207279389033627649 会被转换为 1207279389033627600
 * 
 * 解决方案：
 * - 序列化：将Long类型序列化为字符串，前端接收时作为字符串处理
 * - 反序列化：支持从字符串反序列化为Long，前端可以传递字符串类型的ID
 */
@Configuration
public class JacksonConfig {

    /**
     * 配置ObjectMapper，将Long类型序列化为字符串，并支持从字符串反序列化
     * Spring Boot会自动使用这个ObjectMapper进行JSON序列化和反序列化
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        
        // 创建自定义模块
        SimpleModule simpleModule = new SimpleModule();
        
        // 将Long类型序列化为字符串（输出到前端）
        // Long.class 对应 Long包装类型
        // Long.TYPE 对应 long 基本类型
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        
        // 支持从字符串反序列化为Long（接收前端数据）
        simpleModule.addDeserializer(Long.class, new LongStringDeserializer());
        simpleModule.addDeserializer(Long.TYPE, new LongStringDeserializer());
        
        // 注册模块
        objectMapper.registerModule(simpleModule);
        
        // 允许接受字符串类型的数字
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        return objectMapper;
    }
}

