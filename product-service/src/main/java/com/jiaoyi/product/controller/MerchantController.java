package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.Merchant;
import com.jiaoyi.product.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆控制器
 */
@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Slf4j
public class MerchantController {
    
    private final MerchantService merchantService;
    
    /**
     * 创建餐馆
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Merchant>> createMerchant(@RequestBody Merchant merchant) {
        log.info("创建餐馆，merchantId: {}", merchant.getMerchantId());
        try {
            Merchant createdMerchant = merchantService.createMerchant(merchant);
            return ResponseEntity.ok(ApiResponse.success("创建成功", createdMerchant));
        } catch (Exception e) {
            log.error("创建餐馆失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID查询餐馆
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Merchant>> getMerchantById(@PathVariable Long id) {
        log.info("查询餐馆，ID: {}", id);
        Optional<Merchant> merchant = merchantService.getMerchantById(id);
        return merchant.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "餐馆不存在")));
    }
    
    /**
     * 根据merchantId查询餐馆（推荐，包含分片键）
     */
    @GetMapping("/merchant-id/{merchantId}")
    public ResponseEntity<ApiResponse<Merchant>> getMerchantByMerchantId(@PathVariable String merchantId) {
        log.info("查询餐馆，merchantId: {}", merchantId);
        Optional<Merchant> merchant = merchantService.getMerchantByMerchantId(merchantId);
        return merchant.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "餐馆不存在")));
    }
    
    /**
     * 根据encryptMerchantId查询餐馆
     */
    @GetMapping("/encrypt/{encryptMerchantId}")
    public ResponseEntity<ApiResponse<Merchant>> getMerchantByEncryptMerchantId(@PathVariable String encryptMerchantId) {
        log.info("查询餐馆，encryptMerchantId: {}", encryptMerchantId);
        Optional<Merchant> merchant = merchantService.getMerchantByEncryptMerchantId(encryptMerchantId);
        return merchant.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "餐馆不存在")));
    }
    
    /**
     * 根据merchantGroupId查询所有餐馆
     */
    @GetMapping("/group/{merchantGroupId}")
    public ResponseEntity<ApiResponse<List<Merchant>>> getMerchantsByGroupId(@PathVariable String merchantGroupId) {
        log.info("查询餐馆组，merchantGroupId: {}", merchantGroupId);
        List<Merchant> merchants = merchantService.getMerchantsByGroupId(merchantGroupId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", merchants));
    }
    
    /**
     * 查询所有显示的餐馆
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Merchant>>> getAllDisplayMerchants() {
        log.info("查询所有显示的餐馆");
        List<Merchant> merchants = merchantService.getAllDisplayMerchants();
        return ResponseEntity.ok(ApiResponse.success("查询成功", merchants));
    }
    
    /**
     * 更新餐馆
     */
    @PutMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<Merchant>> updateMerchant(
            @PathVariable String merchantId,
            @RequestBody Merchant merchant) {
        log.info("更新餐馆，merchantId: {}", merchantId);
        merchant.setMerchantId(merchantId);
        try {
            Merchant updatedMerchant = merchantService.updateMerchant(merchant);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedMerchant));
        } catch (Exception e) {
            log.error("更新餐馆失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除餐馆（逻辑删除）
     */
    @DeleteMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<Void>> deleteMerchant(@PathVariable String merchantId) {
        log.info("删除餐馆，merchantId: {}", merchantId);
        try {
            merchantService.deleteMerchant(merchantId);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除餐馆失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}

