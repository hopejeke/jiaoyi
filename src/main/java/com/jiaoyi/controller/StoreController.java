package com.jiaoyi.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.entity.Store;
import com.jiaoyi.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 店铺控制器
 */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
@Slf4j
public class StoreController {
    
    private final StoreService storeService;
    
    /**
     * 创建店铺
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Store>> createStore(@RequestBody Store store) {
        log.info("创建店铺请求，店铺名称: {}", store.getStoreName());
        Store createdStore = storeService.createStore(store);
        return ResponseEntity.ok(ApiResponse.success("创建成功", createdStore));
    }
    
    /**
     * 更新店铺
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateStore(@PathVariable Long id, @RequestBody Store store) {
        log.info("更新店铺请求，店铺ID: {}", id);
        store.setId(id);
        storeService.updateStore(store);
        return ResponseEntity.ok(ApiResponse.success("更新成功", null));
    }
    
    /**
     * 根据ID获取店铺
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Store>> getStoreById(@PathVariable Long id) {
        log.info("查询店铺请求，ID: {}", id);
        return storeService.getStoreById(id)
                .map(store -> ResponseEntity.ok(ApiResponse.success("查询成功", store)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "店铺不存在")));
    }
    
    /**
     * 根据店铺编码获取店铺
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<Store>> getStoreByCode(@PathVariable String code) {
        log.info("查询店铺请求，编码: {}", code);
        return storeService.getStoreByCode(code)
                .map(store -> ResponseEntity.ok(ApiResponse.success("查询成功", store)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "店铺不存在")));
    }
    
    /**
     * 获取所有店铺
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Store>>> getAllStores() {
        log.info("查询所有店铺请求");
        List<Store> stores = storeService.getAllStores();
        return ResponseEntity.ok(ApiResponse.success("查询成功", stores));
    }
    
    /**
     * 根据状态获取店铺列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<Store>>> getStoresByStatus(@PathVariable String status) {
        log.info("根据状态查询店铺请求，状态: {}", status);
        Store.StoreStatus storeStatus = Store.StoreStatus.valueOf(status.toUpperCase());
        List<Store> stores = storeService.getStoresByStatus(storeStatus);
        return ResponseEntity.ok(ApiResponse.success("查询成功", stores));
    }
    
    /**
     * 删除店铺
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(@PathVariable Long id) {
        log.info("删除店铺请求，ID: {}", id);
        storeService.deleteStore(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }
}

