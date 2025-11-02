package com.jiaoyi.service;

import com.jiaoyi.entity.Store;
import com.jiaoyi.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 店铺服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreService {
    
    private final StoreMapper storeMapper;
    
    /**
     * 创建店铺
     */
    @Transactional
    public Store createStore(Store store) {
        log.info("创建店铺，店铺名称: {}", store.getStoreName());
        if (store.getStatus() == null) {
            store.setStatus(Store.StoreStatus.ACTIVE);
        }
        storeMapper.insert(store);
        log.info("店铺创建成功，店铺ID: {}", store.getId());
        return store;
    }
    
    /**
     * 更新店铺
     */
    @Transactional
    public void updateStore(Store store) {
        log.info("更新店铺，店铺ID: {}", store.getId());
        storeMapper.update(store);
        log.info("店铺更新成功，店铺ID: {}", store.getId());
    }
    
    /**
     * 根据ID获取店铺
     */
    public Optional<Store> getStoreById(Long id) {
        log.info("查询店铺，ID: {}", id);
        return storeMapper.selectById(id);
    }
    
    /**
     * 根据店铺编码获取店铺
     */
    public Optional<Store> getStoreByCode(String storeCode) {
        log.info("查询店铺，编码: {}", storeCode);
        return storeMapper.selectByCode(storeCode);
    }
    
    /**
     * 获取所有店铺
     */
    public List<Store> getAllStores() {
        log.info("查询所有店铺");
        return storeMapper.selectAll();
    }
    
    /**
     * 根据状态获取店铺列表
     */
    public List<Store> getStoresByStatus(Store.StoreStatus status) {
        log.info("根据状态查询店铺，状态: {}", status);
        return storeMapper.selectByStatus(status);
    }
    
    /**
     * 删除店铺
     */
    @Transactional
    public void deleteStore(Long id) {
        log.info("删除店铺，ID: {}", id);
        // 删除店铺时会自动删除关联的商品（通过外键CASCADE）
        storeMapper.deleteById(id);
        log.info("店铺删除成功，ID: {}", id);
    }
    
}

