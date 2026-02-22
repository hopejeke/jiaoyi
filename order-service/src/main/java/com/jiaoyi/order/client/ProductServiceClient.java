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
     * 按渠道批量扣减（下单时调，一单多品）
     */
    @PostMapping("/inventory/poi/stock/deduct-by-channel/batch")
    ApiResponse<Void> deductByChannelBatch(@RequestBody ChannelDeductBatchRequest request);

    /**
     * 按订单归还库存（取消时调）
     */
    @PostMapping("/inventory/poi/stock/return-by-order")
    ApiResponse<Void> returnByOrder(@RequestParam("orderId") String orderId);

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
     * 按渠道批量扣减请求（brandId/storeId/channelCode/orderId + items）
     */
    class ChannelDeductBatchRequest {
        private String brandId;
        private String storeId;
        private String channelCode;
        private String orderId;
        private List<ChannelDeductItem> items;

        public String getBrandId() { return brandId; }
        public void setBrandId(String brandId) { this.brandId = brandId; }
        public String getStoreId() { return storeId; }
        public void setStoreId(String storeId) { this.storeId = storeId; }
        public String getChannelCode() { return channelCode; }
        public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public List<ChannelDeductItem> getItems() { return items; }
        public void setItems(List<ChannelDeductItem> items) { this.items = items; }
    }

    class ChannelDeductItem {
        private Long objectId;
        private java.math.BigDecimal quantity;

        public Long getObjectId() { return objectId; }
        public void setObjectId(Long objectId) { this.objectId = objectId; }
        public java.math.BigDecimal getQuantity() { return quantity; }
        public void setQuantity(java.math.BigDecimal quantity) { this.quantity = quantity; }
    }
}
