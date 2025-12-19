package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.StoreService;
import com.jiaoyi.product.service.StoreServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆服务控制器
 */
@RestController
@RequestMapping("/api/store-services")
@RequiredArgsConstructor
@Slf4j
public class StoreServiceController {
    
    private final StoreServiceService storeServiceService;
    
    /**
     * 创建餐馆服务
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StoreService>> createStoreService(@RequestBody StoreService storeService) {
        log.info("创建餐馆服务，merchantId: {}, serviceType: {}", 
                storeService.getMerchantId(), storeService.getServiceType());
        try {
            StoreService createdService = storeServiceService.createStoreService(storeService);
            return ResponseEntity.ok(ApiResponse.success("创建成功", createdService));
        } catch (Exception e) {
            log.error("创建餐馆服务失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID查询餐馆服务
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreService>> getStoreServiceById(@PathVariable Long id) {
        log.info("查询餐馆服务，ID: {}", id);
        Optional<StoreService> storeService = storeServiceService.getStoreServiceById(id);
        return storeService.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "餐馆服务不存在")));
    }
    
    /**
     * 根据merchantId和serviceType查询餐馆服务（推荐，包含分片键）
     */
    @GetMapping("/merchant/{merchantId}/service/{serviceType}")
    public ResponseEntity<ApiResponse<StoreService>> getStoreServiceByMerchantIdAndServiceType(
            @PathVariable String merchantId,
            @PathVariable String serviceType) {
        log.info("查询餐馆服务，merchantId: {}, serviceType: {}", merchantId, serviceType);
        Optional<StoreService> storeService = storeServiceService.getStoreServiceByMerchantIdAndServiceType(merchantId, serviceType);
        return storeService.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "餐馆服务不存在")));
    }
    
    /**
     * 根据merchantId查询所有餐馆服务
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<StoreService>>> getStoreServicesByMerchantId(@PathVariable String merchantId) {
        log.info("查询餐馆的所有服务，merchantId: {}", merchantId);
        List<StoreService> services = storeServiceService.getStoreServicesByMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", services));
    }
    
    /**
     * 更新餐馆服务
     */
    @PutMapping("/merchant/{merchantId}/service/{serviceType}")
    public ResponseEntity<ApiResponse<StoreService>> updateStoreService(
            @PathVariable String merchantId,
            @PathVariable String serviceType,
            @RequestBody StoreService storeService) {
        log.info("更新餐馆服务，merchantId: {}, serviceType: {}", merchantId, serviceType);
        storeService.setMerchantId(merchantId);
        storeService.setServiceType(serviceType);
        try {
            StoreService updatedService = storeServiceService.updateStoreService(storeService);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedService));
        } catch (Exception e) {
            log.error("更新餐馆服务失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除餐馆服务
     */
    @DeleteMapping("/merchant/{merchantId}/service/{serviceType}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreService(
            @PathVariable String merchantId,
            @PathVariable String serviceType) {
        log.info("删除餐馆服务，merchantId: {}, serviceType: {}", merchantId, serviceType);
        try {
            storeServiceService.deleteStoreService(merchantId, serviceType);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除餐馆服务失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}

