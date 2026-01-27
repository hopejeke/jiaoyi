package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.entity.ShoppingCart;
import com.jiaoyi.order.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 购物车控制器
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
public class ShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    /**
     * 获取购物车
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ShoppingCart>> getCart(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer tableId,
            @RequestParam Long storeId,
            @RequestParam String merchantId) {
        log.info("获取购物车，userId: {}, tableId: {}, storeId: {}", userId, tableId, storeId);

        ShoppingCart cart = shoppingCartService.getCartByUser(userId, storeId, tableId);
        if (cart == null) {
            // 返回空购物车
            cart = shoppingCartService.getOrCreateCart(userId, tableId, storeId, merchantId);
        }

        return ResponseEntity.ok(ApiResponse.success("获取成功", cart));
    }

    /**
     * 添加商品到购物车
     */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<ShoppingCart>> addItem(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer tableId,
            @RequestParam Long storeId,
            @RequestParam String merchantId,
            @RequestParam Long productId,
            @RequestParam(required = false) Long skuId,
            @RequestParam Integer quantity) {
        log.info("添加商品到购物车，userId: {}, productId: {}, skuId: {}, quantity: {}", 
                userId, productId, skuId, quantity);

        ShoppingCart cart = shoppingCartService.addItem(userId, tableId, storeId, merchantId, 
                productId, skuId, quantity);

        return ResponseEntity.ok(ApiResponse.success("添加成功", cart));
    }

    /**
     * 更新购物车商品数量
     */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<ShoppingCart>> updateItemQuantity(
            @PathVariable Long itemId,
            @RequestParam Long cartId,
            @RequestParam Integer quantity) {
        log.info("更新购物车商品数量，cartId: {}, itemId: {}, quantity: {}", cartId, itemId, quantity);

        ShoppingCart cart = shoppingCartService.updateItemQuantity(cartId, itemId, quantity);

        return ResponseEntity.ok(ApiResponse.success("更新成功", cart));
    }

    /**
     * 删除购物车商品
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<ShoppingCart>> removeItem(
            @PathVariable Long itemId,
            @RequestParam Long cartId) {
        log.info("删除购物车商品，cartId: {}, itemId: {}", cartId, itemId);

        ShoppingCart cart = shoppingCartService.removeItem(cartId, itemId);

        return ResponseEntity.ok(ApiResponse.success("删除成功", cart));
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/{cartId}")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable Long cartId) {
        log.info("清空购物车，cartId: {}", cartId);

        shoppingCartService.clearCart(cartId);

        return ResponseEntity.ok(ApiResponse.success("清空成功", null));
    }

    /**
     * 合并购物车（登录后合并临时购物车）
     */
    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<ShoppingCart>> mergeCart(
            @RequestParam Long userId,
            @RequestParam Long tempCartId,
            @RequestParam Long storeId,
            @RequestParam String merchantId) {
        log.info("合并购物车，userId: {}, tempCartId: {}", userId, tempCartId);

        ShoppingCart cart = shoppingCartService.mergeCart(userId, tempCartId, storeId, merchantId);

        return ResponseEntity.ok(ApiResponse.success("合并成功", cart));
    }
}

