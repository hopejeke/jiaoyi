package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.MenuItem;
import com.jiaoyi.product.mapper.sharding.MenuItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 菜单项信息服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuItemService {
    
    private final MenuItemMapper menuItemMapper;
    
    /**
     * 创建菜单项信息
     */
    @Transactional
    public MenuItem createMenuItem(MenuItem menuItem) {
        log.info("创建菜单项信息，merchantId: {}, itemId: {}", 
                menuItem.getMerchantId(), menuItem.getItemId());
        
        // 检查是否已存在
        Optional<MenuItem> existing = menuItemMapper.selectByMerchantIdAndItemId(
                menuItem.getMerchantId(), menuItem.getItemId());
        if (existing.isPresent()) {
            throw new RuntimeException("菜单项信息已存在，merchantId: " + menuItem.getMerchantId() + 
                    ", itemId: " + menuItem.getItemId());
        }
        
        menuItem.setVersion(1L);
        
        // 插入菜单项信息
        menuItemMapper.insert(menuItem);
        
        // 查询插入后的菜单项信息（获取version）
        MenuItem insertedItem = menuItemMapper.selectByMerchantIdAndItemId(
                menuItem.getMerchantId(), menuItem.getItemId())
                .orElseThrow(() -> new RuntimeException("菜单项信息创建失败：插入后无法查询到记录"));
        
        log.info("菜单项信息创建成功，ID: {}, merchantId: {}, itemId: {}, 版本号: {}", 
                insertedItem.getId(), insertedItem.getMerchantId(), 
                insertedItem.getItemId(), insertedItem.getVersion());
        
        return insertedItem;
    }
    
    /**
     * 根据ID查询菜单项信息
     */
    public Optional<MenuItem> getMenuItemById(Long id) {
        return menuItemMapper.selectById(id);
    }
    
    /**
     * 根据merchantId和itemId查询菜单项信息（推荐，包含分片键）
     */
    public Optional<MenuItem> getMenuItemByMerchantIdAndItemId(String merchantId, Long itemId) {
        return menuItemMapper.selectByMerchantIdAndItemId(merchantId, itemId);
    }
    
    /**
     * 根据merchantId查询所有菜单项信息
     */
    public List<MenuItem> getMenuItemsByMerchantId(String merchantId) {
        return menuItemMapper.selectByMerchantId(merchantId);
    }
    
    /**
     * 更新菜单项信息（使用乐观锁）
     */
    @Transactional
    public MenuItem updateMenuItem(MenuItem menuItem) {
        log.info("更新菜单项信息，merchantId: {}, itemId: {}", 
                menuItem.getMerchantId(), menuItem.getItemId());
        
        // 查询现有信息获取版本号
        Optional<MenuItem> existing = menuItemMapper.selectByMerchantIdAndItemId(
                menuItem.getMerchantId(), menuItem.getItemId());
        if (!existing.isPresent()) {
            throw new RuntimeException("菜单项信息不存在，merchantId: " + menuItem.getMerchantId() + 
                    ", itemId: " + menuItem.getItemId());
        }
        
        // 设置版本号用于乐观锁
        menuItem.setVersion(existing.get().getVersion());
        
        // 更新菜单项信息
        int affectedRows = menuItemMapper.update(menuItem);
        if (affectedRows == 0) {
            throw new RuntimeException("菜单项信息更新失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        // 查询更新后的菜单项信息
        MenuItem updatedItem = menuItemMapper.selectByMerchantIdAndItemId(
                menuItem.getMerchantId(), menuItem.getItemId())
                .orElseThrow(() -> new RuntimeException("菜单项信息更新失败：更新后无法查询到记录"));
        
        log.info("菜单项信息更新成功，merchantId: {}, itemId: {}, 新版本号: {}", 
                updatedItem.getMerchantId(), updatedItem.getItemId(), updatedItem.getVersion());
        
        return updatedItem;
    }
    
    /**
     * 删除菜单项信息
     */
    @Transactional
    public void deleteMenuItem(String merchantId, Long itemId) {
        log.info("删除菜单项信息，merchantId: {}, itemId: {}", merchantId, itemId);
        
        // 查询现有信息获取版本号
        Optional<MenuItem> existing = menuItemMapper.selectByMerchantIdAndItemId(merchantId, itemId);
        if (!existing.isPresent()) {
            throw new RuntimeException("菜单项信息不存在，merchantId: " + merchantId + ", itemId: " + itemId);
        }
        
        MenuItem menuItem = existing.get();
        menuItem.setVersion(existing.get().getVersion());
        
        // 删除菜单项信息
        int affectedRows = menuItemMapper.deleteById(menuItem);
        if (affectedRows == 0) {
            throw new RuntimeException("菜单项信息删除失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        log.info("菜单项信息删除成功，merchantId: {}, itemId: {}", merchantId, itemId);
    }
}

