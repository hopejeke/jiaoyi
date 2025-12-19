package com.jiaoyi.product.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.product.entity.MenuItem;
import com.jiaoyi.product.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 菜单项信息控制器
 */
@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
@Slf4j
public class MenuItemController {
    
    private final MenuItemService menuItemService;
    
    /**
     * 创建菜单项信息
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MenuItem>> createMenuItem(@RequestBody MenuItem menuItem) {
        log.info("创建菜单项信息，merchantId: {}, itemId: {}", 
                menuItem.getMerchantId(), menuItem.getItemId());
        try {
            MenuItem createdItem = menuItemService.createMenuItem(menuItem);
            return ResponseEntity.ok(ApiResponse.success("创建成功", createdItem));
        } catch (Exception e) {
            log.error("创建菜单项信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID查询菜单项信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItem>> getMenuItemById(@PathVariable Long id) {
        log.info("查询菜单项信息，ID: {}", id);
        Optional<MenuItem> menuItem = menuItemService.getMenuItemById(id);
        return menuItem.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "菜单项信息不存在")));
    }
    
    /**
     * 根据merchantId和itemId查询菜单项信息（推荐，包含分片键）
     */
    @GetMapping("/merchant/{merchantId}/item/{itemId}")
    public ResponseEntity<ApiResponse<MenuItem>> getMenuItemByMerchantIdAndItemId(
            @PathVariable String merchantId,
            @PathVariable Long itemId) {
        log.info("查询菜单项信息，merchantId: {}, itemId: {}", merchantId, itemId);
        Optional<MenuItem> menuItem = menuItemService.getMenuItemByMerchantIdAndItemId(merchantId, itemId);
        return menuItem.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "菜单项信息不存在")));
    }
    
    /**
     * 根据merchantId查询所有菜单项信息
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getMenuItemsByMerchantId(@PathVariable String merchantId) {
        log.info("查询餐馆的所有菜单项信息，merchantId: {}", merchantId);
        List<MenuItem> items = menuItemService.getMenuItemsByMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", items));
    }
    
    /**
     * 更新菜单项信息
     */
    @PutMapping("/merchant/{merchantId}/item/{itemId}")
    public ResponseEntity<ApiResponse<MenuItem>> updateMenuItem(
            @PathVariable String merchantId,
            @PathVariable Long itemId,
            @RequestBody MenuItem menuItem) {
        log.info("更新菜单项信息，merchantId: {}, itemId: {}", merchantId, itemId);
        menuItem.setMerchantId(merchantId);
        menuItem.setItemId(itemId);
        try {
            MenuItem updatedItem = menuItemService.updateMenuItem(menuItem);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updatedItem));
        } catch (Exception e) {
            log.error("更新菜单项信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除菜单项信息
     */
    @DeleteMapping("/merchant/{merchantId}/item/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteMenuItem(
            @PathVariable String merchantId,
            @PathVariable Long itemId) {
        log.info("删除菜单项信息，merchantId: {}, itemId: {}", merchantId, itemId);
        try {
            menuItemService.deleteMenuItem(merchantId, itemId);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            log.error("删除菜单项信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}

