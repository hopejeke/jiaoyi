package com.jiaoyi.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车指纹工具类
 * 用于生成订单请求的唯一指纹，防止重复提交
 */
@Slf4j
public class CartFingerprintUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 订单项指纹数据类
     */
    public static class OrderItemFingerprint {
        private Long productId;
        private String productName;
        private Double unitPrice;
        private Integer quantity;
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    /**
     * 指纹数据类
     */
    public static class FingerprintData {
        private Long userId;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private List<OrderItemFingerprint> orderItems;
        
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
     * 生成购物车指纹
     * 基于用户ID + 商品信息 + 收货信息生成唯一标识
     * 
     * @param userId 用户ID
     * @param receiverName 收货人姓名
     * @param receiverPhone 收货人电话
     * @param receiverAddress 收货地址
     * @param orderItems 订单项列表
     * @return 指纹字符串
     */
    public static String generateFingerprint(Long userId, String receiverName, String receiverPhone, 
                                             String receiverAddress, List<OrderItemFingerprint> orderItems) {
        try {
            FingerprintData data = new FingerprintData();
            data.setUserId(userId);
            data.setReceiverName(receiverName);
            data.setReceiverPhone(receiverPhone);
            data.setReceiverAddress(receiverAddress);
            
            // 对订单项进行排序，确保相同内容生成相同指纹
            List<OrderItemFingerprint> sortedItems = orderItems.stream()
                    .sorted((a, b) -> {
                        int productIdCompare = Long.compare(a.getProductId(), b.getProductId());
                        if (productIdCompare != 0) {
                            return productIdCompare;
                        }
                        return Integer.compare(a.getQuantity(), b.getQuantity());
                    })
                    .collect(Collectors.toList());
            
            data.setOrderItems(sortedItems);
            
            String jsonString = objectMapper.writeValueAsString(data);
            return generateMD5Hash(jsonString);
            
        } catch (JsonProcessingException e) {
            log.error("生成购物车指纹失败", e);
            return generateSimpleFingerprint(userId, receiverName, receiverPhone, receiverAddress, orderItems);
        }
    }
    
    /**
     * 生成简单指纹（备用方案）
     */
    private static String generateSimpleFingerprint(Long userId, String receiverName, String receiverPhone,
                                                     String receiverAddress, List<OrderItemFingerprint> orderItems) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId);
        sb.append("_");
        sb.append(receiverName);
        sb.append("_");
        sb.append(receiverPhone);
        sb.append("_");
        sb.append(receiverAddress);
        sb.append("_");
        
        String orderItemsStr = orderItems.stream()
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
            return String.valueOf(input.hashCode());
        }
    }
}
