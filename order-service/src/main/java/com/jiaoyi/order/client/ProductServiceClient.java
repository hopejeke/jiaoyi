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
@FeignClient(name = "product-service", path = "/api")
public interface ProductServiceClient {
    
    /**
     * 获取商品信息（仅通过ID，会查询所有分片，性能较差）
     * @deprecated 建议使用 getProductByMerchantIdAndId
     */
    @GetMapping("/store-products/{productId}")
    @Deprecated
    ApiResponse<?> getProductById(@PathVariable("productId") Long productId);
    
    /**
     * 通过商户ID和商品ID获取商品信息（推荐，包含分片键，性能更好）
     */
    @GetMapping("/store-products/merchant/{merchantId}/{productId}")
    ApiResponse<?> getProductByMerchantIdAndId(
            @PathVariable("merchantId") String merchantId,
            @PathVariable("productId") Long productId);
    
    /**
     * 检查库存
     */
    @PostMapping("/inventory/check")
    ApiResponse<Boolean> checkStock(@RequestBody CheckStockRequest request);
    
    /**
     * 锁定库存
     */
    @PostMapping("/inventory/{productId}/lock")
    ApiResponse<Void> lockStock(@PathVariable("productId") Long productId, 
                    @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量锁定库存
     */
    @PostMapping("/inventory/lock/batch")
    ApiResponse<Void> lockStockBatch(@RequestBody LockStockBatchRequest request);
    
    /**
     * 解锁库存（基于SKU）
     */
    @PostMapping("/inventory/{productId}/unlock")
    ApiResponse<Void> unlockStock(@PathVariable("productId") Long productId,
                      @RequestParam("skuId") Long skuId,
                      @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量解锁库存（基于SKU）
     * 
     * 返回 OperationResult，包含操作结果状态：
     * - SUCCESS: 第一次调用，解锁成功
     * - IDEMPOTENT_SUCCESS: 重复调用，但库存已解锁过（幂等成功）
     * - FAILED: 操作失败
     */
    @PostMapping("/inventory/unlock/batch")
    ApiResponse<com.jiaoyi.common.OperationResult> unlockStockBatch(@RequestBody UnlockStockBatchRequest request);
    
    /**
     * 扣减库存（基于SKU）
     */
    @PostMapping("/inventory/{productId}/deduct")
    ApiResponse<Void> deductStock(@PathVariable("productId") Long productId,
                      @RequestParam("skuId") Long skuId,
                      @RequestParam("quantity") Integer quantity);
    
    /**
     * 批量扣减库存
     */
    @PostMapping("/inventory/deduct/batch")
    ApiResponse<Void> deductStockBatch(@RequestBody DeductStockBatchRequest request);
    
    /**
     * 获取商户信息（包含自动接单配置）
     */
    @GetMapping("/merchants/{merchantId}")
    ApiResponse<?> getMerchant(@PathVariable("merchantId") String merchantId);
    
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
     * 批量锁定库存请求（基于SKU）
     */
    class LockStockBatchRequest {
        private List<Long> productIds;
        private List<Long> skuIds;
        private List<Integer> quantities;
        private Long orderId; // 订单ID（用于幂等性校验）
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Long> getSkuIds() { return skuIds; }
        public void setSkuIds(List<Long> skuIds) { this.skuIds = skuIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
    
    /**
     * 批量解锁库存请求（基于SKU）
     */
    class UnlockStockBatchRequest {
        private List<Long> productIds;
        private List<Long> skuIds;
        private List<Integer> quantities;
        private Long orderId;
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Long> getSkuIds() { return skuIds; }
        public void setSkuIds(List<Long> skuIds) { this.skuIds = skuIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
    
    /**
     * 批量扣减库存请求（基于SKU）
     */
    class DeductStockBatchRequest {
        private List<Long> productIds;
        private List<Long> skuIds;
        private List<Integer> quantities;
        private Long orderId;
        private String idempotencyKey; // 幂等键（用于库存服务幂等）
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        public List<Long> getSkuIds() { return skuIds; }
        public void setSkuIds(List<Long> skuIds) { this.skuIds = skuIds; }
        public List<Integer> getQuantities() { return quantities; }
        public void setQuantities(List<Integer> quantities) { this.quantities = quantities; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
}
