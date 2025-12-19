package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.StoreService;
import com.jiaoyi.product.mapper.sharding.StoreServiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆服务服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreServiceService {
    
    private final StoreServiceMapper storeServiceMapper;
    
    /**
     * 创建餐馆服务
     */
    @Transactional
    public StoreService createStoreService(StoreService storeService) {
        log.info("创建餐馆服务，merchantId: {}, serviceType: {}", 
                storeService.getMerchantId(), storeService.getServiceType());
        
        // 检查是否已存在
        Optional<StoreService> existing = storeServiceMapper.selectByMerchantIdAndServiceType(
                storeService.getMerchantId(), storeService.getServiceType());
        if (existing.isPresent()) {
            throw new RuntimeException("餐馆服务已存在，merchantId: " + storeService.getMerchantId() + 
                    ", serviceType: " + storeService.getServiceType());
        }
        
        // 设置默认值
        if (storeService.getTempClose() == null) {
            storeService.setTempClose(false);
        }
        if (storeService.getHaveSet() == null) {
            storeService.setHaveSet(false);
        }
        if (storeService.getActivate() == null) {
            storeService.setActivate(false);
        }
        if (storeService.getEnableUse() == null) {
            storeService.setEnableUse(false);
        }
        storeService.setVersion(1L);
        
        // 插入餐馆服务
        storeServiceMapper.insert(storeService);
        
        // 查询插入后的餐馆服务（获取version）
        StoreService insertedService = storeServiceMapper.selectByMerchantIdAndServiceType(
                storeService.getMerchantId(), storeService.getServiceType())
                .orElseThrow(() -> new RuntimeException("餐馆服务创建失败：插入后无法查询到记录"));
        
        log.info("餐馆服务创建成功，ID: {}, merchantId: {}, serviceType: {}, 版本号: {}", 
                insertedService.getId(), insertedService.getMerchantId(), 
                insertedService.getServiceType(), insertedService.getVersion());
        
        return insertedService;
    }
    
    /**
     * 根据ID查询餐馆服务
     */
    public Optional<StoreService> getStoreServiceById(Long id) {
        return storeServiceMapper.selectById(id);
    }
    
    /**
     * 根据merchantId和serviceType查询餐馆服务（推荐，包含分片键）
     */
    public Optional<StoreService> getStoreServiceByMerchantIdAndServiceType(String merchantId, String serviceType) {
        return storeServiceMapper.selectByMerchantIdAndServiceType(merchantId, serviceType);
    }
    
    /**
     * 根据merchantId查询所有餐馆服务
     */
    public List<StoreService> getStoreServicesByMerchantId(String merchantId) {
        return storeServiceMapper.selectByMerchantId(merchantId);
    }
    
    /**
     * 更新餐馆服务（使用乐观锁）
     */
    @Transactional
    public StoreService updateStoreService(StoreService storeService) {
        log.info("更新餐馆服务，merchantId: {}, serviceType: {}", 
                storeService.getMerchantId(), storeService.getServiceType());
        
        // 查询现有服务获取版本号
        Optional<StoreService> existing = storeServiceMapper.selectByMerchantIdAndServiceType(
                storeService.getMerchantId(), storeService.getServiceType());
        if (!existing.isPresent()) {
            throw new RuntimeException("餐馆服务不存在，merchantId: " + storeService.getMerchantId() + 
                    ", serviceType: " + storeService.getServiceType());
        }
        
        // 设置版本号用于乐观锁
        storeService.setVersion(existing.get().getVersion());
        
        // 更新餐馆服务
        int affectedRows = storeServiceMapper.update(storeService);
        if (affectedRows == 0) {
            throw new RuntimeException("餐馆服务更新失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        // 查询更新后的餐馆服务
        StoreService updatedService = storeServiceMapper.selectByMerchantIdAndServiceType(
                storeService.getMerchantId(), storeService.getServiceType())
                .orElseThrow(() -> new RuntimeException("餐馆服务更新失败：更新后无法查询到记录"));
        
        log.info("餐馆服务更新成功，merchantId: {}, serviceType: {}, 新版本号: {}", 
                updatedService.getMerchantId(), updatedService.getServiceType(), updatedService.getVersion());
        
        return updatedService;
    }
    
    /**
     * 删除餐馆服务
     */
    @Transactional
    public void deleteStoreService(String merchantId, String serviceType) {
        log.info("删除餐馆服务，merchantId: {}, serviceType: {}", merchantId, serviceType);
        
        // 查询现有服务获取版本号
        Optional<StoreService> existing = storeServiceMapper.selectByMerchantIdAndServiceType(merchantId, serviceType);
        if (!existing.isPresent()) {
            throw new RuntimeException("餐馆服务不存在，merchantId: " + merchantId + ", serviceType: " + serviceType);
        }
        
        StoreService storeService = existing.get();
        storeService.setVersion(existing.get().getVersion());
        
        // 删除餐馆服务
        int affectedRows = storeServiceMapper.deleteById(storeService);
        if (affectedRows == 0) {
            throw new RuntimeException("餐馆服务删除失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        log.info("餐馆服务删除成功，merchantId: {}, serviceType: {}", merchantId, serviceType);
    }
}

