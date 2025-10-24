package com.jiaoyi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.dto.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车指纹工具类
 */
@Slf4j
public class CartFingerprintUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成购物车指纹
     * 基于用户ID + 商品信息 + 收货信息生成唯一标识
     */
    public static String generateFingerprint(CreateOrderRequest request) {
        try {
            // 构建指纹数据
            FingerprintData data = new FingerprintData();
            data.setUserId(request.getUserId());
            data.setReceiverName(request.getReceiverName());
            data.setReceiverPhone(request.getReceiverPhone());
            data.setReceiverAddress(request.getReceiverAddress());
            
            // 对订单项进行排序，确保相同内容生成相同指纹
            List<OrderItemFingerprint> orderItems = request.getOrderItems().stream()
                    .map(item -> {
                        OrderItemFingerprint fingerprint = new OrderItemFingerprint();
                        fingerprint.setProductId(item.getProductId());
                        fingerprint.setProductName(item.getProductName());
                        fingerprint.setUnitPrice(item.getUnitPrice().doubleValue());
                        fingerprint.setQuantity(item.getQuantity());
                        return fingerprint;
                    })
                    .sorted((a, b) -> {
                        // 按商品ID排序，确保顺序一致
                        int productIdCompare = Long.compare(a.getProductId(), b.getProductId());
                        if (productIdCompare != 0) {
                            return productIdCompare;
                        }
                        // 如果商品ID相同，按数量排序
                        return Integer.compare(a.getQuantity(), b.getQuantity());
                    })
                    .collect(Collectors.toList());
            
            data.setOrderItems(orderItems);
            
            // 转换为JSON字符串
            String jsonString = objectMapper.writeValueAsString(data);
            
            // 生成MD5哈希
            return generateMD5Hash(jsonString);
            
        } catch (JsonProcessingException e) {
            log.error("生成购物车指纹失败", e);
            // 如果JSON序列化失败，使用简单的方式生成指纹
            return generateSimpleFingerprint(request);
        }
    }
    
    /**
     * 生成简单指纹（备用方案）
     */
    private static String generateSimpleFingerprint(CreateOrderRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getUserId());
        sb.append("_");
        sb.append(request.getReceiverName());
        sb.append("_");
        sb.append(request.getReceiverPhone());
        sb.append("_");
        sb.append(request.getReceiverAddress());
        sb.append("_");
        
        // 对订单项进行排序后拼接
        String orderItemsStr = request.getOrderItems().stream()
                .sorted((a, b) -> {
                    int productIdCompare = Long.compare(a.getProductId(), b.getProductId());
                    if (productIdCompare != 0) {
                        return productIdCompare;
                    }
                    return Integer.compare(a.getQuantity(), b.getQuantity());
                })
                .map(item -> item.getProductId() + ":" + item.getQuantity() + ":" + item.getUnitPrice())
                .collect(Collectors.joining(","));
        
        sb.append(orderItemsStr);
        return generateMD5Hash(sb.toString());
    }
    
    /**
     * 生成MD5哈希
     */
    private static String generateMD5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            log.error("生成MD5哈希失败", e);
            // 如果MD5失败，返回输入字符串的哈希码
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * 指纹数据类
     */
    private static class FingerprintData {
        private Long userId;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private List<OrderItemFingerprint> orderItems;
        
        // getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getReceiverName() { return receiverName; }
        public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
        public String getReceiverPhone() { return receiverPhone; }
        public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
        public String getReceiverAddress() { return receiverAddress; }
        public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }
        public List<OrderItemFingerprint> getOrderItems() { return orderItems; }
        public void setOrderItems(List<OrderItemFingerprint> orderItems) { this.orderItems = orderItems; }
    }
    
    /**
     * 订单项指纹数据类
     */
    private static class OrderItemFingerprint {
        private Long productId;
        private String productName;
        private Double unitPrice;
        private Integer quantity;
        
        // getters and setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
