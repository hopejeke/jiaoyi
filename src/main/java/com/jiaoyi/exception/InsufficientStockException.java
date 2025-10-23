package com.jiaoyi.exception;

/**
 * 库存不足异常
 */
public class InsufficientStockException extends BusinessException {
    
    private final Long productId;
    private final String productName;
    private final Integer requestedQuantity;
    private final Integer availableStock;
    
    public InsufficientStockException(Long productId, String productName, 
                                   Integer requestedQuantity, Integer availableStock) {
        super(String.format("商品[%s]库存不足，请求数量：%d，可用库存：%d", 
              productName, requestedQuantity, availableStock));
        this.productId = productId;
        this.productName = productName;
        this.requestedQuantity = requestedQuantity;
        this.availableStock = availableStock;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }
    
    public Integer getAvailableStock() {
        return availableStock;
    }
}
