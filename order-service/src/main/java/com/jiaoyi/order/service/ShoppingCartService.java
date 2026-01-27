package com.jiaoyi.order.service;

import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.entity.ShoppingCart;
import com.jiaoyi.order.entity.ShoppingCartItem;
import com.jiaoyi.order.mapper.ShoppingCartItemMapper;
import com.jiaoyi.order.mapper.ShoppingCartMapper;
import com.jiaoyi.order.util.ShardUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 购物车服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingCartService {

    private final ShoppingCartMapper shoppingCartMapper;
    private final ShoppingCartItemMapper shoppingCartItemMapper;
    private final ProductServiceClient productServiceClient;
    private final RedissonClient redissonClient;

    /**
     * 默认购物车过期时间（24小时）
     */
    private static final int DEFAULT_CART_EXPIRE_HOURS = 24;

    /**
     * 获取或创建购物车
     */
    @Transactional
    public ShoppingCart getOrCreateCart(Long userId, Integer tableId, Long storeId, String merchantId) {
        // 计算分片ID
        Integer shardId = ShardUtil.calculateShardId(storeId);

        // 使用分布式锁确保并发安全
        String lockKey = String.format("cart:lock:%d:%d:%d", userId != null ? userId : 0, tableId != null ? tableId : 0, storeId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // 查询购物车
                    ShoppingCart cart;
                    if (tableId != null) {
                        // 桌码购物车
                        cart = shoppingCartMapper.selectByTableIdAndStoreId(tableId, storeId);
                    } else {
                        // 用户购物车
                        cart = shoppingCartMapper.selectByUserIdAndStoreId(userId, storeId, null);
                    }

                    if (cart == null) {
                        // 创建新购物车
                        cart = new ShoppingCart();
                        cart.setUserId(userId);
                        cart.setTableId(tableId);
                        cart.setMerchantId(merchantId);
                        cart.setStoreId(storeId);
                        cart.setShardId(shardId);
                        cart.setTotalAmount(BigDecimal.ZERO);
                        cart.setTotalQuantity(0);
                        cart.setExpireTime(LocalDateTime.now().plusHours(DEFAULT_CART_EXPIRE_HOURS));
                        cart.setVersion(1L);
                        cart.setCreateTime(LocalDateTime.now());
                        cart.setUpdateTime(LocalDateTime.now());

                        shoppingCartMapper.insert(cart);
                        log.info("创建购物车成功，cartId: {}, userId: {}, tableId: {}, storeId: {}", 
                                cart.getId(), userId, tableId, storeId);
                    } else {
                        // 检查是否过期
                        if (cart.getExpireTime().isBefore(LocalDateTime.now())) {
                            // 购物车已过期，清空并重新设置过期时间
                            shoppingCartItemMapper.deleteByCartId(cart.getId());
                            cart.setTotalAmount(BigDecimal.ZERO);
                            cart.setTotalQuantity(0);
                            cart.setExpireTime(LocalDateTime.now().plusHours(DEFAULT_CART_EXPIRE_HOURS));
                            cart.setVersion(cart.getVersion() + 1);
                            cart.setUpdateTime(LocalDateTime.now());
                            shoppingCartMapper.update(cart);
                            log.info("购物车已过期，已清空，cartId: {}", cart.getId());
                        } else {
                            // 更新过期时间
                            cart.setExpireTime(LocalDateTime.now().plusHours(DEFAULT_CART_EXPIRE_HOURS));
                            cart.setVersion(cart.getVersion() + 1);
                            cart.setUpdateTime(LocalDateTime.now());
                            shoppingCartMapper.update(cart);
                        }
                    }

                    // 加载购物车项
                    List<ShoppingCartItem> items = shoppingCartItemMapper.selectByCartId(cart.getId());
                    cart.setItems(items);

                    return cart;
                } finally {
                    lock.unlock();
                }
            } else {
                throw new BusinessException("获取购物车锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("获取购物车锁被中断");
        }
    }

    /**
     * 添加商品到购物车
     */
    @Transactional
    public ShoppingCart addItem(Long userId, Integer tableId, Long storeId, String merchantId,
                                Long productId, Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }

        // 获取或创建购物车
        ShoppingCart cart = getOrCreateCart(userId, tableId, storeId, merchantId);

        // 查询商品信息（TODO: 调用商品服务）
        // 这里先使用默认值，实际应该从商品服务获取
        String productName = "商品" + productId;
        BigDecimal unitPrice = BigDecimal.valueOf(10.00); // 默认价格

        // 查询购物车中是否已有该商品
        ShoppingCartItem existingItem = shoppingCartItemMapper.selectByCartIdAndProductId(
                cart.getId(), productId, skuId);

        if (existingItem != null) {
            // 更新数量
            int newQuantity = existingItem.getQuantity() + quantity;
            BigDecimal newSubtotal = unitPrice.multiply(BigDecimal.valueOf(newQuantity));

            Long oldVersion = existingItem.getVersion();
            int updated = shoppingCartItemMapper.updateQuantity(
                    existingItem.getId(), newQuantity, newSubtotal, oldVersion);

            if (updated == 0) {
                throw new BusinessException("更新购物车项失败，请重试");
            }

            existingItem.setQuantity(newQuantity);
            existingItem.setSubtotal(newSubtotal);
        } else {
            // 添加新商品
            ShoppingCartItem item = new ShoppingCartItem();
            item.setCartId(cart.getId());
            item.setProductId(productId);
            item.setSkuId(skuId);
            item.setProductName(productName);
            item.setUnitPrice(unitPrice);
            item.setQuantity(quantity);
            item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            item.setVersion(1L);
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());

            shoppingCartItemMapper.insert(item);
            existingItem = item;
        }

        // 更新购物车总金额和总数量
        updateCartTotal(cart.getId());

        // 重新加载购物车
        return getCartById(cart.getId());
    }

    /**
     * 更新购物车商品数量
     */
    @Transactional
    public ShoppingCart updateItemQuantity(Long cartId, Long itemId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }

        ShoppingCartItem item = shoppingCartItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("购物车项不存在");
        }

        if (!item.getCartId().equals(cartId)) {
            throw new BusinessException("购物车项不属于该购物车");
        }

        BigDecimal newSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        Long oldVersion = item.getVersion();

        int updated = shoppingCartItemMapper.updateQuantity(itemId, quantity, newSubtotal, oldVersion);
        if (updated == 0) {
            throw new BusinessException("更新购物车项失败，请重试");
        }

        // 更新购物车总金额和总数量
        updateCartTotal(cartId);

        return getCartById(cartId);
    }

    /**
     * 删除购物车商品
     */
    @Transactional
    public ShoppingCart removeItem(Long cartId, Long itemId) {
        ShoppingCartItem item = shoppingCartItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("购物车项不存在");
        }

        if (!item.getCartId().equals(cartId)) {
            throw new BusinessException("购物车项不属于该购物车");
        }

        shoppingCartItemMapper.deleteById(itemId);

        // 更新购物车总金额和总数量
        updateCartTotal(cartId);

        return getCartById(cartId);
    }

    /**
     * 根据ID查询购物车（包含购物车项）
     */
    public ShoppingCart getCartById(Long cartId) {
        ShoppingCart cart = shoppingCartMapper.selectById(cartId);
        if (cart == null) {
            return null;
        }

        // 检查是否过期
        if (cart.getExpireTime().isBefore(LocalDateTime.now())) {
            log.info("购物车已过期，cartId: {}", cartId);
            return null;
        }

        // 加载购物车项
        List<ShoppingCartItem> items = shoppingCartItemMapper.selectByCartId(cartId);
        cart.setItems(items);

        return cart;
    }

    /**
     * 查询用户购物车
     */
    public ShoppingCart getCartByUser(Long userId, Long storeId, Integer tableId) {
        ShoppingCart cart;
        if (tableId != null) {
            cart = shoppingCartMapper.selectByTableIdAndStoreId(tableId, storeId);
        } else {
            cart = shoppingCartMapper.selectByUserIdAndStoreId(userId, storeId, null);
        }

        if (cart == null) {
            return null;
        }

        // 检查是否过期
        if (cart.getExpireTime().isBefore(LocalDateTime.now())) {
            log.info("购物车已过期，cartId: {}", cart.getId());
            return null;
        }

        // 加载购物车项
        List<ShoppingCartItem> items = shoppingCartItemMapper.selectByCartId(cart.getId());
        cart.setItems(items);

        return cart;
    }

    /**
     * 清空购物车
     */
    @Transactional
    public void clearCart(Long cartId) {
        ShoppingCart cart = shoppingCartMapper.selectById(cartId);
        if (cart == null) {
            throw new BusinessException("购物车不存在");
        }

        // 删除所有购物车项
        shoppingCartItemMapper.deleteByCartId(cartId);

        // 更新购物车总金额和总数量
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setTotalQuantity(0);
        cart.setVersion(cart.getVersion() + 1);
        cart.setUpdateTime(LocalDateTime.now());
        shoppingCartMapper.update(cart);

        log.info("清空购物车成功，cartId: {}", cartId);
    }

    /**
     * 更新购物车总金额和总数量
     */
    private void updateCartTotal(Long cartId) {
        List<ShoppingCartItem> items = shoppingCartItemMapper.selectByCartId(cartId);

        BigDecimal totalAmount = items.stream()
                .map(ShoppingCartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalQuantity = items.stream()
                .mapToInt(ShoppingCartItem::getQuantity)
                .sum();

        ShoppingCart cart = shoppingCartMapper.selectById(cartId);
        if (cart != null) {
            Long oldVersion = cart.getVersion();
            int updated = shoppingCartMapper.updateTotal(cartId, totalAmount, totalQuantity, oldVersion);
            if (updated == 0) {
                log.warn("更新购物车总金额失败，cartId: {}, version: {}", cartId, oldVersion);
            }
        }
    }

    /**
     * 合并购物车（登录后合并临时购物车到用户购物车）
     */
    @Transactional
    public ShoppingCart mergeCart(Long userId, Long tempCartId, Long storeId, String merchantId) {
        ShoppingCart tempCart = shoppingCartMapper.selectById(tempCartId);
        if (tempCart == null) {
            throw new BusinessException("临时购物车不存在");
        }

        // 获取用户购物车
        ShoppingCart userCart = getOrCreateCart(userId, null, storeId, merchantId);

        // 如果临时购物车和用户购物车是同一个，直接返回
        if (tempCart.getId().equals(userCart.getId())) {
            return userCart;
        }

        // 加载临时购物车项
        List<ShoppingCartItem> tempItems = shoppingCartItemMapper.selectByCartId(tempCartId);

        // 合并购物车项
        for (ShoppingCartItem tempItem : tempItems) {
            ShoppingCartItem existingItem = shoppingCartItemMapper.selectByCartIdAndProductId(
                    userCart.getId(), tempItem.getProductId(), tempItem.getSkuId());

            if (existingItem != null) {
                // 合并数量
                int newQuantity = existingItem.getQuantity() + tempItem.getQuantity();
                BigDecimal newSubtotal = existingItem.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity));
                Long oldVersion = existingItem.getVersion();

                shoppingCartItemMapper.updateQuantity(existingItem.getId(), newQuantity, newSubtotal, oldVersion);
            } else {
                // 添加新项
                tempItem.setCartId(userCart.getId());
                tempItem.setId(null);
                tempItem.setVersion(1L);
                tempItem.setCreateTime(LocalDateTime.now());
                tempItem.setUpdateTime(LocalDateTime.now());
                shoppingCartItemMapper.insert(tempItem);
            }
        }

        // 删除临时购物车
        shoppingCartItemMapper.deleteByCartId(tempCartId);
        shoppingCartMapper.deleteById(tempCartId);

        // 更新用户购物车总金额和总数量
        updateCartTotal(userCart.getId());

        return getCartById(userCart.getId());
    }
}


