package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.Merchant;
import com.jiaoyi.product.mapper.sharding.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 餐馆服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {
    
    private final MerchantMapper merchantMapper;
    
    /**
     * 创建餐馆
     */
    @Transactional
    public Merchant createMerchant(Merchant merchant) {
        log.info("创建餐馆，merchantId: {}, name: {}", merchant.getMerchantId(), merchant.getName());
        
        // 检查merchantId是否已存在
        Optional<Merchant> existing = merchantMapper.selectByMerchantId(merchant.getMerchantId());
        if (existing.isPresent()) {
            throw new RuntimeException("餐馆ID已存在，merchantId: " + merchant.getMerchantId());
        }
        
        // 设置默认值
        if (merchant.getIsPickup() == null) {
            merchant.setIsPickup(true);
        }
        if (merchant.getIsDelivery() == null) {
            merchant.setIsDelivery(true);
        }
        if (merchant.getActivate() == null) {
            merchant.setActivate(false);
        }
        if (merchant.getDlActivate() == null) {
            merchant.setDlActivate(false);
        }
        if (merchant.getDisplay() == null) {
            merchant.setDisplay(true);
        }
        // 设置布尔字段的默认值（不能为 null）
        if (merchant.getPickupHaveSetted() == null) {
            merchant.setPickupHaveSetted(false);
        }
        if (merchant.getDeliveryHaveSetted() == null) {
            merchant.setDeliveryHaveSetted(false);
        }
        if (merchant.getEnableNote() == null) {
            merchant.setEnableNote(false);
        }
        if (merchant.getEnableAutoSend() == null) {
            merchant.setEnableAutoSend(false);
        }
        if (merchant.getEnableAutoReceipt() == null) {
            merchant.setEnableAutoReceipt(false);
        }
        if (merchant.getEnableSdiAutoReceipt() == null) {
            merchant.setEnableSdiAutoReceipt(false);
        }
        if (merchant.getEnableSdiAutoSend() == null) {
            merchant.setEnableSdiAutoSend(false);
        }
        if (merchant.getEnablePopularItem() == null) {
            merchant.setEnablePopularItem(false);
        }
        merchant.setVersion(1L);
        
        // 插入餐馆
        merchantMapper.insert(merchant);
        
        // 查询插入后的餐馆（获取version）
        Merchant insertedMerchant = merchantMapper.selectByMerchantId(merchant.getMerchantId())
                .orElseThrow(() -> new RuntimeException("餐馆创建失败：插入后无法查询到餐馆记录"));
        
        log.info("餐馆创建成功，ID: {}, merchantId: {}, 版本号: {}", 
                insertedMerchant.getId(), insertedMerchant.getMerchantId(), insertedMerchant.getVersion());
        
        return insertedMerchant;
    }
    
    /**
     * 根据ID查询餐馆
     */
    public Optional<Merchant> getMerchantById(Long id) {
        return merchantMapper.selectById(id);
    }
    
    /**
     * 根据merchantId查询餐馆（推荐，包含分片键）
     */
    public Optional<Merchant> getMerchantByMerchantId(String merchantId) {
        log.debug("查询商户，merchantId: {}", merchantId);
        Optional<Merchant> merchant = merchantMapper.selectByMerchantId(merchantId);
        if (merchant.isPresent()) {
            log.info("查询到商户，merchantId: {}, id: {}, name: {}", 
                merchantId, merchant.get().getId(), merchant.get().getName());
        } else {
            log.warn("未查询到商户，merchantId: {}", merchantId);
        }
        return merchant;
    }
    
    /**
     * 根据encryptMerchantId查询餐馆
     */
    public Optional<Merchant> getMerchantByEncryptMerchantId(String encryptMerchantId) {
        return merchantMapper.selectByEncryptMerchantId(encryptMerchantId);
    }
    
    /**
     * 根据merchantGroupId查询所有餐馆
     */
    public List<Merchant> getMerchantsByGroupId(String merchantGroupId) {
        return merchantMapper.selectByMerchantGroupId(merchantGroupId);
    }
    
    /**
     * 查询所有显示的餐馆
     */
    public List<Merchant> getAllDisplayMerchants() {
        return merchantMapper.selectAllDisplay();
    }
    
    /**
     * 更新餐馆（使用乐观锁）
     */
    @Transactional
    public Merchant updateMerchant(Merchant merchant) {
        log.info("更新餐馆，merchantId: {}", merchant.getMerchantId());
        
        // 查询现有餐馆获取版本号
        Optional<Merchant> existing = merchantMapper.selectByMerchantId(merchant.getMerchantId());
        if (!existing.isPresent()) {
            throw new RuntimeException("餐馆不存在，merchantId: " + merchant.getMerchantId());
        }
        
        // 设置版本号用于乐观锁
        merchant.setVersion(existing.get().getVersion());
        
        // 更新餐馆
        int affectedRows = merchantMapper.update(merchant);
        if (affectedRows == 0) {
            throw new RuntimeException("餐馆更新失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        // 查询更新后的餐馆
        Merchant updatedMerchant = merchantMapper.selectByMerchantId(merchant.getMerchantId())
                .orElseThrow(() -> new RuntimeException("餐馆更新失败：更新后无法查询到餐馆记录"));
        
        log.info("餐馆更新成功，merchantId: {}, 新版本号: {}", 
                updatedMerchant.getMerchantId(), updatedMerchant.getVersion());
        
        return updatedMerchant;
    }
    
    /**
     * 删除餐馆（逻辑删除，设置display=0）
     */
    @Transactional
    public void deleteMerchant(String merchantId) {
        log.info("删除餐馆，merchantId: {}", merchantId);
        
        // 查询现有餐馆获取版本号
        Optional<Merchant> existing = merchantMapper.selectByMerchantId(merchantId);
        if (!existing.isPresent()) {
            throw new RuntimeException("餐馆不存在，merchantId: " + merchantId);
        }
        
        Merchant merchant = existing.get();
        merchant.setDisplay(false);
        merchant.setVersion(existing.get().getVersion());
        
        // 逻辑删除餐馆
        int affectedRows = merchantMapper.deleteById(merchant);
        if (affectedRows == 0) {
            throw new RuntimeException("餐馆删除失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        log.info("餐馆删除成功，merchantId: {}", merchantId);
    }
}

