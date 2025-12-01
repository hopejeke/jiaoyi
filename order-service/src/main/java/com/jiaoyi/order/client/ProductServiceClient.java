package com.jiaoyi.order.client;

import com.jiaoyi.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务 Feign Client
 * 用于订单服务调用商品服务
 */
@FeignClient(name = "product-service", url = "${product.service.url:http://localhost:8081}")
public interface ProductServiceClient {
    
    /**
     * 获取商品信息
     */
    @GetMapping("/api/store-products/{productId}")
    ApiResponse<?> getProductById(@PathVariable("productId") Long productId);
    
    /**
     * 检查库存
     */
    @PostMapping("/api/inventory/check")
    ApiResponse<Boolean> checkStock(@RequestBody CheckStockRequest request);
    
    /**
     * 锁定库存
     */
    @PostMapping("/api/inventory/{productId}/lock")
    ApiResponse<Void> lockStock(@PathVariable("productId") Long productId, 
                    @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量锁定库存
     */
    @PostMapping("/api/inventory/lock/batch")
    ApiResponse<Void> lockStockBatch(@RequestBody LockStockBatchRequest request);
    
    /**
     * 解锁库存
     */
    @PostMapping("/api/inventory/{productId}/unlock")
    ApiResponse<Void> unlockStock(@PathVariable("productId") Long productId, 
                      @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量解锁库存
     */
    @PostMapping("/api/inventory/unlock/batch")
    ApiResponse<Void> unlockStockBatch(@RequestBody UnlockStockBatchRequest request);
    
    /**
     * 扣减库存
     */
    @PostMapping("/api/inventory/{productId}/deduct")
    ApiResponse<Void> deductStock(@PathVariable("productId") Long productId, 
                      @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量扣减库存
     */
    @PostMapping("/api/inventory/deduct/batch")
    ApiResponse<Void> deductStockBatch(@RequestBody DeductStockBatchRequest request);
    
    /**
     * 检查库存请求
     */
    class CheckStockRequest {
        private Long productId;
        private Integer quantity;
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
    
    /**
     * 批量锁定库存请求
     */
    class LockStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
    }
    
    /**
     * 批量解锁库存请求
     */
    class UnlockStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
        private Long orderId;
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
    
    /**
     * 批量扣减库存请求
     */
    class DeductStockBatchRequest {
        private List<Long> productIds;
        private List<Integer> quantities;
        private Long orderId;
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
}
