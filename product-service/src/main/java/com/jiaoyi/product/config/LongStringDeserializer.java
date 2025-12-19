package com.jiaoyi.product.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Long 类型反序列化器
 * 支持从字符串和数字类型反序列化为 Long，避免 JavaScript 大整数精度丢失问题
 */
public class LongStringDeserializer extends JsonDeserializer<Long> {
    
    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // 先尝试作为字符串读取
        if (p.getCurrentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IOException("无法将字符串解析为 Long: " + value, e);
            }
        }
        
        // 如果不是字符串，尝试作为数字读取
        if (p.getCurrentToken().isNumeric()) {
            return p.getLongValue();
        }
        
        // 如果都不是，尝试获取值并转换
        String value = p.getValueAsString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IOException("无法解析为 Long: " + value, e);
        }
    }
}

