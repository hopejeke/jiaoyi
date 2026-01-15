package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.ProductSku;
import com.jiaoyi.product.service.ProductSkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 商品SKU控制器
 */
@RestController
@RequestMapping("/api/product-skus")
@RequiredArgsConstructor
@Slf4j
public class ProductSkuController {
    
    private final ProductSkuService productSkuService;
    
    /**
     * 创建SKU（自动创建库存记录）
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductSku>> createSku(@RequestBody ProductSku sku) {
        log.info("创建SKU，商品ID: {}, SKU编码: {}", sku.getProductId(), sku.getSkuCode());
        try {
            ProductSku createdSku = productSkuService.createSku(sku);
            return ResponseEntity.ok(ApiResponse.success("SKU创建成功（已自动创建库存记录）", createdSku));
        } catch (Exception e) {
            log.error("创建SKU失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID查询SKU
     * 注意：由于 product_sku 表是分片表，只传 id 可能查询不到
     * 建议使用 /product/{productId}/sku/{skuId} 接口
     */
    @GetMapping("/{skuId}")
    public ResponseEntity<ApiResponse<ProductSku>> getSkuById(@PathVariable Long skuId) {
        log.info("查询SKU，SKU ID: {}", skuId);
        Optional<ProductSku> sku = productSkuService.getSkuById(skuId);
        return sku.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "SKU不存在")));
    }
    
    /**
     * 根据商品ID和SKU ID查询SKU（推荐，包含分片键）
     */
    @GetMapping("/product/{productId}/sku/{skuId}")
    public ResponseEntity<ApiResponse<ProductSku>> getSkuByProductIdAndId(
            @PathVariable Long productId,
            @PathVariable Long skuId) {
        log.info("查询SKU，商品ID: {}, SKU ID: {}", productId, skuId);
        Optional<ProductSku> sku = productSkuService.getSkuByProductIdAndId(productId, skuId);
        return sku.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "SKU不存在")));
    }
    
    /**
     * 根据商品ID查询所有SKU
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<ProductSku>>> getSkusByProductId(@PathVariable Long productId) {
        log.info("查询商品的所有SKU，商品ID: {}", productId);
        List<ProductSku> skus = productSkuService.getSkusByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", skus));
    }
    
    /**
     * 更新SKU
     */
    @PutMapping("/{skuId}")
    public ResponseEntity<ApiResponse<ProductSku>> updateSku(
            @PathVariable Long skuId,
            @RequestBody ProductSku sku) {
        log.info("更新SKU，SKU ID: {}", skuId);
        sku.setId(skuId);
        try {
            ProductSku updatedSku = productSkuService.updateSku(sku);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedSku));
        } catch (Exception e) {
            log.error("更新SKU失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除SKU（逻辑删除）
     */
    @DeleteMapping("/{skuId}")
    public ResponseEntity<ApiResponse<Void>> deleteSku(@PathVariable Long skuId) {
        log.info("删除SKU，SKU ID: {}", skuId);
        try {
            productSkuService.deleteSku(skuId);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除SKU失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}

